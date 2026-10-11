package com.amynna.hololib.client.render.modules.dimension.space.warp;

/**
 * Convergence des etoiles vers la cible du saut (T35, livrable 2) : place le quad d'une etoile qui glisse sur son
 * grand cercle vers la direction de la cible, pour le ciel ({@code SpaceSkyRenderer} : fond etoile procedural dans
 * les axes du systeme, etoiles des autres systemes dans les axes du monde), a partir des fractions fournies par
 * {@link SpaceWarpTimeline#convergence()} et {@link SpaceWarpTimeline#convergenceTail()}.
 * <p>
 * Une etoile de direction {@code d} (unitaire) a l'angle {@code theta} de la cible {@code T} : sa tete est tournee
 * de {@code c theta} vers {@code T} autour de l'axe {@code k = d x T / |d x T|}, sa queue de {@code c_queue theta}
 * ({@code c_queue <= c}) ; le quad va de la queue a la tete, de demi-largeur la demi-taille de l'etoile : un carre
 * a l'arret ({@code c = c_queue}), une trainee qui s'allonge avec la vitesse. Coins sur la sphere unite (a l'appelant
 * de les mettre a l'echelle), dans l'ordre {@code (-, -), (+, -), (+, +), (-, +)} de la base {@code (k, m)}
 * ({@code m} : tangente vers la cible ; {@code k x m} vers l'oeil), meme sens que les carres du ciel : face avant
 * vue du centre (les quads opaques du ciel eliminent les faces arriere). Etoile sur la cible : immobile ; a l'oppose :
 * grand cercle quelconque (fixe). Instance mutable reutilisee, fil de rendu, sans allocation.
 */
public final class SpaceWarpConvergence
{
    /** Sinus sous lequel une etoile est tenue pour alignee sur la cible (ou opposee). */
    private static final double PARALLEL = 1.0e-6;

    private double tx;
    private double ty;
    private double tz = -1.0;
    private double head;
    private double tail;
    /** Coins du dernier quad place, sphere unite : {@code x0 y0 z0 ... x3 y3 z3}. */
    private final float[] corners = new float[12];

    public SpaceWarpConvergence() {}

    /**
     * Cible {@code (x, y, z)} (unitaire, dans les axes des etoiles a placer) et fractions de la convergence de la tete
     * et de la queue, {@code [0, 1]}.
     */
    public SpaceWarpConvergence set(float x, float y, float z, float headFraction, float tailFraction)
    {
        tx = x;
        ty = y;
        tz = z;
        head = clamp01(headFraction);
        tail = Math.min(head, clamp01(tailFraction));
        return this;
    }

    /**
     * Place le quad de l'etoile de direction {@code (dx, dy, dz)} (unitaire) et de demi-taille {@code half}
     * (tangente de la demi-taille angulaire) ; coins lus par {@link #x}, {@link #y}, {@link #z}.
     */
    public void place(float dx, float dy, float dz, float half)
    {
        double cos = dx * tx + dy * ty + dz * tz;
        // k = d x T (perpendiculaire au grand cercle), |k| = sin theta.
        double kx = dy * tz - dz * ty;
        double ky = dz * tx - dx * tz;
        double kz = dx * ty - dy * tx;
        double sin = Math.sqrt(kx * kx + ky * ky + kz * kz);
        double theta;
        double px, py, pz;
        if (sin > PARALLEL) {
            kx /= sin;
            ky /= sin;
            kz /= sin;
            // p = k x d : tangente en d vers la cible, (T - d cos) / sin.
            px = (tx - dx * cos) / sin;
            py = (ty - dy * cos) / sin;
            pz = (tz - dz * cos) / sin;
            theta = Math.atan2(sin, cos);
        } else {
            // Alignee (immobile) ou opposee (demi-tour sur un grand cercle fixe) : axe perpendiculaire quelconque.
            if (Math.abs(dy) < 0.9f) {
                kx = -dz;
                ky = 0.0;
                kz = dx;
            } else {
                kx = 0.0;
                ky = dz;
                kz = -dy;
            }
            double length = Math.sqrt(kx * kx + ky * ky + kz * kz);
            kx /= length;
            ky /= length;
            kz /= length;
            px = ky * dz - kz * dy;
            py = kz * dx - kx * dz;
            pz = kx * dy - ky * dx;
            theta = cos > 0.0 ? 0.0 : Math.PI;
        }
        double h = half;
        double ka = kx * h, kb = ky * h, kc = kz * h;
        // Queue : point et tangente tournes de c_queue theta ; cote -m de la trainee.
        double a = tail * theta;
        double ca = Math.cos(a), sa = Math.sin(a);
        double qx = dx * ca + px * sa, qy = dy * ca + py * sa, qz = dz * ca + pz * sa;
        double mx = (px * ca - dx * sa) * h, my = (py * ca - dy * sa) * h, mz = (pz * ca - dz * sa) * h;
        corner(0, qx - ka - mx, qy - kb - my, qz - kc - mz);
        corner(1, qx + ka - mx, qy + kb - my, qz + kc - mz);
        // Tete : point et tangente tournes de c theta ; cote +m.
        double b = head * theta;
        double cb = Math.cos(b), sb = Math.sin(b);
        double hx = dx * cb + px * sb, hy = dy * cb + py * sb, hz = dz * cb + pz * sb;
        mx = (px * cb - dx * sb) * h;
        my = (py * cb - dy * sb) * h;
        mz = (pz * cb - dz * sb) * h;
        corner(2, hx + ka + mx, hy + kb + my, hz + kc + mz);
        corner(3, hx - ka + mx, hy - kb + my, hz - kc + mz);
    }

    /** Coordonnee {@code x} du coin {@code i} ({@code 0..3}) du dernier quad place. */
    public float x(int i) { return corners[3 * i]; }

    public float y(int i) { return corners[3 * i + 1]; }

    public float z(int i) { return corners[3 * i + 2]; }

    private void corner(int i, double x, double y, double z)
    {
        corners[3 * i] = (float) x;
        corners[3 * i + 1] = (float) y;
        corners[3 * i + 2] = (float) z;
    }

    private static double clamp01(double value) { return value < 0.0 ? 0.0 : Math.min(1.0, value); }
}
