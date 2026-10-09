package com.amynna.hololib.modules.dimension.space.math;

import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import com.amynna.hololib.modules.dimension.space.model.SpaceOrbit;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import org.joml.Vector3d;

import java.util.List;

/**
 * Orbites de Kepler (voir {@code docs/space/04-mathematiques.md} par. 3). Calcul deterministe et sans allocation :
 * le temps de jeu est synchronise par vanilla, rien n'est envoye pour animer les orbites.
 * <p>
 * Pour une orbite {@code (a, e, orientation R_o, periode P, phase)} :
 * <pre>
 * M = 2 pi frac(t / P + phase / 360)              anomalie moyenne dans [0, 2 pi[
 * E - e sin E = M                                 Newton, depart M + 0,85 e signe(sin M), 6 iterations max
 * x_O = a (cos E - e)     z_O = a sqrt(1 - e^2) sin E     y_O = 0
 * p_local = R_o (x_O, 0, z_O) * UA_KM             R_o = rotationYXZ(-yaw, pitch, roll)
 * p = p_parent + p_local
 * </pre>
 * Plan de l'orbite = plan local {@code XZ}, parent au foyer, periapse sur {@code +X}, parcours de {@code +X} vers
 * {@code +Z}. {@code R_o} suit la convention de {@code HoloEditMath.rotation} (recopiee en {@code double}). Un corps sans
 * parent est immobile au centre du systeme.
 */
public final class SpaceOrbits
{
    /** Nombre maximal d'iterations de Newton. */
    public static final int KEPLER_ITERATIONS = 6;
    /** Arret anticipe de Newton : correction plus petite que ce seuil (radians). */
    private static final double KEPLER_TOLERANCE = 1.0e-13;

    private SpaceOrbits() {}

    /**
     * Tableau de positions pret a l'emploi pour {@link #positions} : {@link SpaceLimits#MAX_BODIES} vecteurs. A creer
     * <b>une fois</b> (champ), jamais par frame.
     */
    public static Vector3d[] newPositionArray()
    {
        Vector3d[] array = new Vector3d[SpaceLimits.MAX_BODIES];
        for (int i = 0; i < array.length; i++) array[i] = new Vector3d();
        return array;
    }

    /**
     * Positions absolues de tous les corps du systeme, en km, repere systeme (centre sur les corps sans parent), au
     * temps {@code tDays} (jours simules). {@code dest[i]} recoit la position du corps {@code system.bodies().get(i)} ;
     * une seule passe, les parents precedant leurs enfants (garanti par le modele). Sans allocation.
     *
     * @param dest au moins {@code system.bodies().size()} vecteurs non nuls ({@link #newPositionArray()})
     */
    public static void positions(SpaceSystem system, double tDays, Vector3d[] dest)
    {
        List<SpaceBody> bodies = system.bodies();
        int count = bodies.size();
        for (int i = 0; i < count; i++) {
            SpaceBody body = bodies.get(i);
            Vector3d p = dest[i];
            if (body.isRoot()) {
                p.x = 0.0;
                p.y = 0.0;
                p.z = 0.0;
                continue;
            }
            localPosition(body.orbit(), tDays, p);
            Vector3d parent = dest[system.parentIndex(i)];
            p.x += parent.x;
            p.y += parent.y;
            p.z += parent.z;
        }
    }

    /** Position d'un corps relativement a son parent, en km (axes du systeme), au temps {@code tDays}. */
    public static Vector3d localPosition(SpaceOrbit orbit, double tDays, Vector3d dest)
    {
        double e = orbit.eccentricity();
        return orbitPoint(orbit, eccentricAnomaly(meanAnomaly(orbit, tDays), e), dest);
    }

    /** Anomalie moyenne au temps {@code tDays}, dans {@code [0, 2 pi[} ; temps non fini traite comme {@code 0}. */
    public static double meanAnomaly(SpaceOrbit orbit, double tDays)
    {
        if (!Double.isFinite(tDays)) tDays = 0.0;
        return SpaceUnits.TWO_PI * SpaceUnits.orbitFraction(tDays, orbit.period(), orbit.phase());
    }

    /**
     * Anomalie excentrique {@code E} telle que {@code E - e sin E = M} : Newton, depart de Danby
     * {@code E = M + 0,85 e signe(sin M)} (precis a e = 0,95 en 6 iterations), au plus {@link #KEPLER_ITERATIONS}
     * iterations. Le denominateur
     * {@code 1 - e cos E} vaut au moins {@code 1 - e >= 0,05} pour {@code e <= 0,95} : le resultat est toujours fini.
     *
     * @param meanAnomaly {@code M}, radians (dans {@code [0, 2 pi[} de preference)
     * @param e           excentricite, {@code [0, 0,95]}
     */
    public static double eccentricAnomaly(double meanAnomaly, double e)
    {
        double E = meanAnomaly + 0.85 * e * Math.signum(StrictMath.sin(meanAnomaly));
        for (int i = 0; i < KEPLER_ITERATIONS; i++) {
            double delta = (E - e * StrictMath.sin(E) - meanAnomaly) / (1.0 - e * StrictMath.cos(E));
            E -= delta;
            if (Math.abs(delta) < KEPLER_TOLERANCE) break;
        }
        return E;
    }

    /**
     * Point de l'orbite d'anomalie excentrique {@code E}, relativement au parent, en km (axes du systeme). Sert aussi a
     * tracer l'ellipse ({@code E} de 0 a {@code 2 pi}).
     */
    public static Vector3d orbitPoint(SpaceOrbit orbit, double eccentricAnomaly, Vector3d dest)
    {
        double a = orbit.semiMajorAxis() * SpaceUnits.AU_KM;
        double e = orbit.eccentricity();
        double x = a * (StrictMath.cos(eccentricAnomaly) - e);
        double z = a * Math.sqrt(1.0 - e * e) * StrictMath.sin(eccentricAnomaly);
        return SpaceRotations.rotateYXZ(-Math.toRadians(orbit.yaw()), Math.toRadians(orbit.pitch()),
                Math.toRadians(orbit.roll()), x, 0.0, z, dest);
    }
}
