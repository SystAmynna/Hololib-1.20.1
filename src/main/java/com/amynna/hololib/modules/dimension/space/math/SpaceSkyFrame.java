package com.amynna.hololib.modules.dimension.space.math;

import com.amynna.hololib.modules.dimension.space.model.SpaceGalaxy;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

/**
 * Repere du ciel d'une frame (voir {@code docs/space/04-mathematiques.md} par. 2, 4) : construit depuis la pose
 * interpolee ({@link #set}), il transforme un point du systeme en <b>direction</b> unitaire dans les axes du monde (le
 * repere du {@code PoseStack} d'un {@code SkyRenderer}, centre sur l'oeil) et en <b>distance</b> (km) :
 * {@code p_W = Q^-1 (p_S - s)}. C'est la matrice du systeme vue depuis le monde, {@code M_SW = Q^-1 T(-s)}.
 * <p>
 * Instance reutilisable (une par renderer, fil de rendu) : aucune allocation par appel. Vecteur nul (point confondu
 * avec le monde, ou deux systemes superposes) : direction conventionnelle {@code (0, 1, 0)} et distance {@code 0},
 * a l'appelant de l'ignorer.
 */
public final class SpaceSkyFrame
{
    /**
     * Rayon angulaire apparent maximal ({@link #angularRadius}), radians : 90 degres, monde sur la surface (D9 : plus de
     * plafond a 80 degres ; au plancher {@code h = 0,01 R}, un corps fait environ 81,9 degres et remplit presque la
     * moitie du ciel).
     */
    public static final double MAX_ANGULAR_RADIUS = 0.5 * Math.PI;

    private final Vector3d position = new Vector3d();
    private final Quaterniond orientation = new Quaterniond();
    private final Vector3d tmp = new Vector3d();

    /** Cadre a l'origine, identite, jusqu'au premier {@link #set}. */
    public SpaceSkyFrame() {}

    /** Prend la pose de la frame (copiee : la pose peut ensuite changer). */
    public SpaceSkyFrame set(SpacePose pose)
    {
        position.set(pose.position());
        orientation.set(pose.orientation());
        return this;
    }

    /** Position du monde de ce cadre, km (lecture seule). */
    public Vector3dc position() { return position; }

    // ---------------------------------------------------------------- systeme -> monde

    /**
     * Direction (axes du monde, unitaire) du point {@code pointKm} du systeme vu depuis le monde.
     *
     * @return la distance, en km ({@code 0} si le point est confondu avec le monde : {@code dest = (0, 1, 0)})
     */
    public double direction(Vector3dc pointKm, Vector3f dest)
    {
        return toWorld(pointKm.x() - position.x, pointKm.y() - position.y, pointKm.z() - position.z, dest);
    }

    /** Comme {@link #direction(Vector3dc, Vector3f)}, point donne par ses composantes (km). */
    public double direction(double x, double y, double z, Vector3f dest)
    {
        return toWorld(x - position.x, y - position.y, z - position.z, dest);
    }

    /**
     * Direction (axes du monde, unitaire) de {@code fromKm} vers {@code toKm}, deux points du systeme : par exemple
     * corps -> etoile pour l'eclairage.
     *
     * @return la distance entre les deux points, en km ({@code 0} : {@code dest = (0, 1, 0)})
     */
    public double directionBetween(Vector3dc fromKm, Vector3dc toKm, Vector3f dest)
    {
        return toWorld(toKm.x() - fromKm.x(), toKm.y() - fromKm.y(), toKm.z() - fromKm.z(), dest);
    }

    /**
     * Direction (axes du monde, unitaire) d'un autre systeme : {@code normalize(Q^-1 (g_to - g_from))}, la position
     * du monde dans son systeme etant negligeable a l'echelle galactique.
     *
     * @return la distance entre les deux systemes, en annees-lumiere ({@code 0} si superposes :
     * {@code dest = (0, 1, 0)})
     */
    public double galacticDirection(double fromX, double fromY, double fromZ, double toX, double toY, double toZ,
                                    Vector3f dest)
    {
        return toWorld(toX - fromX, toY - fromY, toZ - fromZ, dest);
    }

    /** {@link #galacticDirection(double, double, double, double, double, double, Vector3f)} d'une etoile de fond. */
    public double galacticDirection(SpaceSystem from, SpaceGalaxy.BackgroundStar to, Vector3f dest)
    {
        return toWorld(to.x() - from.x(), to.y() - from.y(), to.z() - from.z(), dest);
    }

    /** Direction des axes du systeme vers ceux du monde ({@code Q^-1 v}), sans normalisation ni translation. */
    public Vector3f rotateToWorld(double x, double y, double z, Vector3f dest)
    {
        SpaceRotations.rotateInverse(orientation, x, y, z, tmp);
        return dest.set((float) tmp.x, (float) tmp.y, (float) tmp.z);
    }

    // ---------------------------------------------------------------- tailles apparentes

    /**
     * Rayon angulaire apparent d'un corps de rayon {@code radius} a la distance {@code distance} (memes unites) :
     * {@code asin(min(1, R / d))}, jusqu'a {@link #MAX_ANGULAR_RADIUS} (90 degres : monde sur ou dans le corps) ; en
     * radians. Distance nulle : 90 degres.
     */
    public static double angularRadius(double radius, double distance)
    {
        if (!(distance > 0.0)) return MAX_ANGULAR_RADIUS;
        return Math.asin(Math.min(1.0, radius / distance));
    }

    // ---------------------------------------------------------------- interne

    /** {@code dest = normalize(Q^-1 v)} ; rend {@code |v|}. */
    private double toWorld(double x, double y, double z, Vector3f dest)
    {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (!(length > 0.0) || !Double.isFinite(length)) {
            dest.set(0f, 1f, 0f);
            return 0.0;
        }
        SpaceRotations.rotateInverse(orientation, x / length, y / length, z / length, tmp);
        dest.set((float) tmp.x, (float) tmp.y, (float) tmp.z);
        return length;
    }
}
