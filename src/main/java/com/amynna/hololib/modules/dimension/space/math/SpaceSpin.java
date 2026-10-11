package com.amynna.hololib.modules.dimension.space.math;

import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import com.amynna.hololib.modules.dimension.space.model.SpaceOrbit;
import com.amynna.hololib.modules.dimension.space.model.SpaceRotation;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import org.joml.Quaterniond;

import java.util.List;

/**
 * Rotation propre des corps (T28, decision C71 ; voir {@code docs/space/04-mathematiques.md} par. 10) : orientation
 * d'un corps au temps simule {@code t} (jours), sous forme d'un quaternion {@code q_B} qui envoie les axes locaux du
 * corps dans les axes du systeme ({@code v_S = q_B v_B}) :
 * <pre>
 * q_B = R_ref * Ry(-noeud) * Rx(obliquite) * Ry(W)
 * R_ref = Ry(-yaw) Rx(pitch) Rz(roll) de l'orbite (corps avec parent), identite sinon
 * W = 2 pi frac(frac(24 t / P) + phase / 360)            P != 0 (heures, signee : P < 0 fait decroitre W)
 * W = atan2(-d_z, d_x) + phase                           P == 0 (synchrone), d = direction corps -> parent
 *                                                        exprimee dans le repere du pole (avant Ry(W))
 * </pre>
 * Axes locaux du corps : {@code +Y} = pole (nord pour une periode positive), {@code +X} = meridien origine (longitude
 * 0 d'une texture), equateur dans le plan local {@code XZ}. Une rotation synchrone tient l'axe {@code +X} tourne vers
 * le parent (libration comprise sur une orbite excentrique) ; sans parent, le corps ne tourne pas.
 * <p>
 * <b>Deterministe</b> comme {@link SpaceOrbits} : trigonometrie {@link StrictMath}, produits de quaternions de
 * {@code SpaceRotations} (pas de JOML, pas de {@code fma}) : deux appels avec les memes entrees rendent le meme
 * quaternion bit a bit, sur le serveur et sur les clients. <b>Sans allocation</b> dans les variantes a tampon
 * ({@link #orientation(SpaceBody, double, Quaterniond)}, {@link #orientations}).
 */
public final class SpaceSpin
{
    /** Heures par jour simule. */
    public static final double HOURS_PER_DAY = 24.0;
    /** En dessous de ce carre de norme, la direction du parent sur l'equateur est indefinie (W = phase). */
    private static final double DEGENERATE = 1.0e-24;

    private SpaceSpin() {}

    /**
     * Tableau d'orientations pret a l'emploi pour {@link #orientations} : {@link SpaceLimits#MAX_BODIES}
     * quaternions. A creer <b>une fois</b> (champ), jamais par frame.
     */
    public static Quaterniond[] newOrientationArray()
    {
        Quaterniond[] array = new Quaterniond[SpaceLimits.MAX_BODIES];
        for (int i = 0; i < array.length; i++) array[i] = new Quaterniond();
        return array;
    }

    /**
     * Orientations de tous les corps du systeme au temps {@code tDays} : {@code dest[i]} recoit celle du corps
     * {@code system.bodies().get(i)}. Sans allocation.
     *
     * @param dest au moins {@code system.bodies().size()} quaternions non nuls ({@link #newOrientationArray()})
     */
    public static void orientations(SpaceSystem system, double tDays, Quaterniond[] dest)
    {
        List<SpaceBody> bodies = system.bodies();
        for (int i = 0; i < bodies.size(); i++) orientation(bodies.get(i), tDays, dest[i]);
    }

    /** Orientation du corps au temps {@code tDays} (nouveau quaternion : hors des chemins par frame). */
    public static Quaterniond orientation(SpaceBody body, double tDays)
    {
        return orientation(body, tDays, new Quaterniond());
    }

    /**
     * Orientation du corps au temps {@code tDays} (jours simules ; non fini traite comme 0), axes locaux du corps ->
     * axes du systeme, quaternion unitaire. Sans allocation.
     */
    public static Quaterniond orientation(SpaceBody body, double tDays, Quaterniond dest)
    {
        SpaceRotation rotation = body.rotation();
        SpaceOrbit orbit = body.orbit();
        dest.x = 0.0;
        dest.y = 0.0;
        dest.z = 0.0;
        dest.w = 1.0;
        if (orbit != null) {
            // R_ref : meme convention que SpaceOrbits.orbitPoint (rotationYXZ(-yaw, pitch, roll)).
            SpaceRotations.multiplyAxisAngle(dest, 0.0, 1.0, 0.0, -Math.toRadians(orbit.yaw()));
            SpaceRotations.multiplyAxisAngle(dest, 1.0, 0.0, 0.0, Math.toRadians(orbit.pitch()));
            SpaceRotations.multiplyAxisAngle(dest, 0.0, 0.0, 1.0, Math.toRadians(orbit.roll()));
        }
        SpaceRotations.multiplyAxisAngle(dest, 0.0, 1.0, 0.0, -Math.toRadians(rotation.node()));
        SpaceRotations.multiplyAxisAngle(dest, 1.0, 0.0, 0.0, Math.toRadians(rotation.obliquity()));
        SpaceRotations.multiplyAxisAngle(dest, 0.0, 1.0, 0.0, spinAngle(body, tDays));
        SpaceRotations.normalize(dest);
        return dest;
    }

    /**
     * Angle de rotation propre {@code W} du corps au temps {@code tDays}, radians dans {@code [0, 2 pi[} : angle du
     * meridien origine autour du pole, compte depuis la ligne des noeuds (voir la documentation de la classe).
     */
    public static double spinAngle(SpaceBody body, double tDays)
    {
        if (!Double.isFinite(tDays)) tDays = 0.0;
        SpaceRotation rotation = body.rotation();
        double phase = rotation.phase() / 360.0;
        if (!rotation.isSynchronous()) {
            // frac de chaque terme : la precision ne se degrade pas avec la duree de la partie.
            double turns = SpaceUnits.frac(tDays * HOURS_PER_DAY / rotation.period());
            return SpaceUnits.TWO_PI * SpaceUnits.frac(turns + phase);
        }
        SpaceOrbit orbit = body.orbit();
        if (orbit == null) return SpaceUnits.TWO_PI * SpaceUnits.frac(phase);
        return SpaceUnits.TWO_PI * SpaceUnits.frac(lockedAngle(orbit, rotation, tDays) / SpaceUnits.TWO_PI + phase);
    }

    /**
     * Angle {@code W} (radians) qui tourne l'axe {@code +X} du corps vers son parent : position du corps sur son orbite
     * dans le repere de l'orbite {@code (x_O, 0, z_O)} (Kepler, comme {@link SpaceOrbits}), direction du parent
     * {@code -(x_O, 0, z_O)} ramenee dans le repere du pole par {@code Rx(-obliquite) Ry(noeud)}, puis
     * {@code W = atan2(-d_z, d_x)} (car {@code Ry(W) (1, 0, 0) = (cos W, 0, -sin W)}). Direction indefinie : 0.
     */
    private static double lockedAngle(SpaceOrbit orbit, SpaceRotation rotation, double tDays)
    {
        double e = orbit.eccentricity();
        double anomaly = SpaceOrbits.eccentricAnomaly(SpaceOrbits.meanAnomaly(orbit, tDays), e);
        // Unite sans importance (seule la direction compte) : demi-grand axe = 1.
        double x = StrictMath.cos(anomaly) - e;
        double z = Math.sqrt(1.0 - e * e) * StrictMath.sin(anomaly);
        double node = Math.toRadians(rotation.node());
        double sinNode = StrictMath.sin(node);
        double cosNode = StrictMath.cos(node);
        // Ry(noeud) (-x, 0, -z), puis Rx(-obliquite) : seules les composantes X et Z du resultat servent.
        double dx = -x * cosNode - z * sinNode;
        double dz = (x * sinNode - z * cosNode) * StrictMath.cos(Math.toRadians(rotation.obliquity()));
        if (dx * dx + dz * dz < DEGENERATE) return 0.0;
        double angle = StrictMath.atan2(-dz, dx);
        return angle < 0.0 ? angle + SpaceUnits.TWO_PI : angle;
    }
}
