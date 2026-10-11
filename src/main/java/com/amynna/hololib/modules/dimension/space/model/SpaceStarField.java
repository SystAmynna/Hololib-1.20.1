package com.amynna.hololib.modules.dimension.space.model;

/**
 * Fond etoile procedural (T31, D22, C72) : environ {@value #COUNT} petites etoiles decoratives, toutes derivees de la
 * <b>graine</b> 64 bits de l'Environment (seule donnee envoyee : bloc froid). Chaque client genere le meme champ pour la
 * meme graine : generateur maison (SplitMix64) et calculs en {@link StrictMath} uniquement, donc le meme resultat sur
 * toute JVM ; les etoiles elles-memes ne circulent jamais sur le reseau.
 * <p>
 * Contenu, par etoile (indices {@code 0} a {@code size() - 1}, ordre de generation) :
 * <ul>
 *     <li><b>direction</b> unitaire dans les axes du systeme ({@code x, y, z} ; ce sont aussi les axes galactiques :
 *     le champ est fixe dans le repere du systeme et ne tourne qu'avec l'orientation du monde). Une part
 *     {@value #BAND_FRACTION} des etoiles est concentree autour du plan galactique (bande : longitude uniforme,
 *     latitude gaussienne d'ecart type {@value #BAND_SIGMA_DEGREES} degres), une part {@value #BULGE_FRACTION} autour
 *     du centre galactique (renflement : gaussienne angulaire aplatie dans le plan, ecarts types
 *     {@value #BULGE_SIGMA_LONGITUDE} degres en longitude et {@value #BULGE_SIGMA_LATITUDE} en latitude), le reste est
 *     uniforme sur la sphere. Plan et centre sont ceux de la Voie lactee reelle dans le repere equatorial du modele
 *     (pole nord galactique en ascension droite 192,86 degres, declinaison 27,13 degres ; centre, dans le Sagittaire,
 *     en 266,40 et -28,94 degres ; meme convention que les systemes voisins de {@link SpaceGalaxy#createDefault()}).
 *     Renflement : un quart de la bande en effectif, concentre sur environ 35 x 18 degres a mi-hauteur ; au centre, la
 *     densite vaut environ 2,5 fois celle du plan ailleurs (9 fois la densite uniforme) : la region la plus riche du
 *     ciel, comme les nuages du Sagittaire, sans tache nette (ses bords se fondent dans la bande) ;</li>
 *     <li><b>eclat</b> relatif dans {@code [0, 1]} ({@link #brightness}) : magnitude tiree selon une loi de puissance
 *     ({@code dN / dm} proportionnel a {@code 10^(0,45 m)} entre {@value #MAG_BRIGHT} et {@value #MAG_FAINT} : la
 *     plupart des etoiles sont faibles), ramenee lineairement en magnitude (1 = la plus brillante possible) ;</li>
 *     <li><b>couleur</b> ({@link #color}, ARGB opaque) : temperature tiree par classe spectrale (proportions des
 *     etoiles visibles a l'oeil nu : beaucoup de B, A et K), puis couleur d'un corps noir (table interpolee).</li>
 * </ul>
 * Lecture seule apres {@link #generate}. Utilise par le ciel (dessin) et, au lot 3, par la distorsion du saut (T35 :
 * convergence des etoiles), sans allocation a la lecture.
 */
public final class SpaceStarField
{
    /** Nombre d'etoiles generees. */
    public static final int COUNT = 4000;
    /** Part des etoiles concentrees autour du plan galactique. */
    public static final double BAND_FRACTION = 0.25;
    /** Ecart type de la latitude galactique des etoiles de la bande, degres. */
    public static final double BAND_SIGMA_DEGREES = 7.0;
    /** Part des etoiles concentrees autour du centre galactique (renflement). */
    public static final double BULGE_FRACTION = 0.06;
    /** Ecarts types du renflement en longitude et en latitude galactiques, degres (aplati dans le plan : 2 pour 1). */
    public static final double BULGE_SIGMA_LONGITUDE = 15.0;
    public static final double BULGE_SIGMA_LATITUDE = 7.5;
    /** Magnitudes extremes tirees (la plus brillante, la plus faible). */
    public static final double MAG_BRIGHT = 1.0;
    public static final double MAG_FAINT = 7.0;
    /** Pente de la loi des magnitudes : {@code dN / dm} proportionnel a {@code 10^(SLOPE m)}. */
    private static final double MAG_SLOPE = 0.45;

    /** Pole nord galactique (J2000) : ascension droite et declinaison, degres. */
    private static final double POLE_RA = 192.85948;
    private static final double POLE_DEC = 27.12825;
    /** Centre galactique (Sagittaire A*, J2000) : ascension droite et declinaison, degres. */
    private static final double CENTER_RA = 266.40500;
    private static final double CENTER_DEC = -28.93617;

    /**
     * Classes spectrales : poids (part des etoiles visibles), temperatures extremes (K). O B A F G K M ; les geantes
     * rouges comptent dans K et M.
     */
    private static final double[] CLASS_WEIGHT = {0.005, 0.20, 0.22, 0.13, 0.15, 0.245, 0.05};
    private static final double[] CLASS_T_MIN = {30000, 10000, 7500, 6000, 5200, 3900, 3000};
    private static final double[] CLASS_T_MAX = {40000, 30000, 10000, 7500, 6000, 5200, 3900};

    /**
     * Couleur d'un corps noir (sRGB, blanc a 6 500 K environ) : temperatures (K) et composantes, interpolees
     * lineairement ; bornees aux extremites.
     */
    private static final double[] TABLE_T = {3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500, 7000, 8000, 9000, 10000,
            12000, 15000, 20000, 30000, 40000};
    private static final int[][] TABLE_RGB = {
            {255, 180, 107}, {255, 196, 137}, {255, 209, 163}, {255, 219, 186}, {255, 228, 206}, {255, 236, 224},
            {255, 243, 239}, {255, 249, 253}, {245, 243, 255}, {227, 233, 255}, {214, 225, 255}, {204, 219, 255},
            {191, 211, 255}, {179, 204, 255}, {168, 197, 255}, {159, 191, 255}, {155, 188, 255}};

    private final long seed;
    private final float[] x;
    private final float[] y;
    private final float[] z;
    private final float[] brightness;
    private final int[] color;

    private SpaceStarField(long seed, int count)
    {
        this.seed = seed;
        x = new float[count];
        y = new float[count];
        z = new float[count];
        brightness = new float[count];
        color = new int[count];
    }

    /**
     * Champ de la graine {@code seed} : meme graine, memes etoiles, sur toute JVM. Cinq ou six tirages par etoile
     * (moins d'une milliseconde) ; a appeler une fois par graine (allocation des tableaux, 80 Ko).
     */
    public static SpaceStarField generate(long seed)
    {
        SpaceStarField field = new SpaceStarField(seed, COUNT);
        Random random = new Random(seed);
        // Base galactique : pole n, e1 = centre galactique ramene dans le plan (longitude 0), e2 = n x e1.
        double ra = StrictMath.toRadians(POLE_RA);
        double dec = StrictMath.toRadians(POLE_DEC);
        double nx = StrictMath.cos(dec) * StrictMath.cos(ra);
        double ny = StrictMath.sin(dec);
        double nz = StrictMath.cos(dec) * StrictMath.sin(ra);
        ra = StrictMath.toRadians(CENTER_RA);
        dec = StrictMath.toRadians(CENTER_DEC);
        double cx = StrictMath.cos(dec) * StrictMath.cos(ra);
        double cy = StrictMath.sin(dec);
        double cz = StrictMath.cos(dec) * StrictMath.sin(ra);
        double along = cx * nx + cy * ny + cz * nz;
        cx -= along * nx;
        cy -= along * ny;
        cz -= along * nz;
        double h = StrictMath.sqrt(cx * cx + cy * cy + cz * cz);
        double e1x = cx / h, e1y = cy / h, e1z = cz / h;
        double e2x = ny * e1z - nz * e1y;
        double e2y = nz * e1x - nx * e1z;
        double e2z = nx * e1y - ny * e1x;
        double sigma = StrictMath.toRadians(BAND_SIGMA_DEGREES);
        double sigmaL = StrictMath.toRadians(BULGE_SIGMA_LONGITUDE);
        double sigmaB = StrictMath.toRadians(BULGE_SIGMA_LATITUDE);
        double span = StrictMath.pow(10.0, MAG_SLOPE * (MAG_FAINT - MAG_BRIGHT)) - 1.0;

        for (int i = 0; i < COUNT; i++) {
            double dx, dy, dz;
            double pick = random.nextDouble();
            if (pick < BAND_FRACTION + BULGE_FRACTION) {
                double longitude;
                double latitude;
                // Deux gaussiennes independantes (Box-Muller).
                double radius = StrictMath.sqrt(-2.0 * StrictMath.log(1.0 - random.nextDouble()));
                double angle = 2.0 * StrictMath.PI * random.nextDouble();
                if (pick < BAND_FRACTION) {
                    // Bande : longitude uniforme, latitude gaussienne.
                    longitude = 2.0 * StrictMath.PI * random.nextDouble();
                    latitude = sigma * radius * StrictMath.cos(angle);
                } else {
                    // Renflement : gaussienne angulaire autour du centre, aplatie dans le plan.
                    longitude = sigmaL * radius * StrictMath.cos(angle);
                    latitude = sigmaB * radius * StrictMath.sin(angle);
                }
                latitude = StrictMath.max(-0.5 * StrictMath.PI, StrictMath.min(0.5 * StrictMath.PI, latitude));
                double cb = StrictMath.cos(latitude);
                double sb = StrictMath.sin(latitude);
                double cl = cb * StrictMath.cos(longitude);
                double sl = cb * StrictMath.sin(longitude);
                dx = cl * e1x + sl * e2x + sb * nx;
                dy = cl * e1y + sl * e2y + sb * ny;
                dz = cl * e1z + sl * e2z + sb * nz;
            } else {
                // Uniforme sur la sphere : hauteur uniforme, azimut uniforme.
                dy = 1.0 - 2.0 * random.nextDouble();
                double r = StrictMath.sqrt(StrictMath.max(0.0, 1.0 - dy * dy));
                double phi = 2.0 * StrictMath.PI * random.nextDouble();
                dx = r * StrictMath.cos(phi);
                dz = r * StrictMath.sin(phi);
            }
            double length = StrictMath.sqrt(dx * dx + dy * dy + dz * dz);
            field.x[i] = (float) (dx / length);
            field.y[i] = (float) (dy / length);
            field.z[i] = (float) (dz / length);
            // Magnitude par inversion de la repartition de 10^(SLOPE m) sur [MAG_BRIGHT, MAG_FAINT].
            double magnitude = MAG_BRIGHT + StrictMath.log10(1.0 + random.nextDouble() * span) / MAG_SLOPE;
            field.brightness[i] = (float) StrictMath.max(0.0, StrictMath.min(1.0,
                    (MAG_FAINT - magnitude) / (MAG_FAINT - MAG_BRIGHT)));
            field.color[i] = blackBody(temperature(random));
        }
        return field;
    }

    /** Graine du champ. */
    public long seed() { return seed; }

    /** Nombre d'etoiles. */
    public int size() { return x.length; }

    /** Direction unitaire de l'etoile {@code i}, axes du systeme : composante x. */
    public float x(int i) { return x[i]; }

    /** Composante y (haut du systeme) de la direction de l'etoile {@code i}. */
    public float y(int i) { return y[i]; }

    /** Composante z de la direction de l'etoile {@code i}. */
    public float z(int i) { return z[i]; }

    /** Eclat relatif de l'etoile {@code i} : 0 = magnitude {@value #MAG_FAINT}, 1 = magnitude {@value #MAG_BRIGHT}. */
    public float brightness(int i) { return brightness[i]; }

    /** Couleur de l'etoile {@code i} (ARGB opaque, corps noir, sans l'eclat). */
    public int color(int i) { return color[i]; }

    // ---------------------------------------------------------------- interne

    /** Temperature (K) : classe spectrale selon {@link #CLASS_WEIGHT}, puis uniforme en logarithme dans la classe. */
    private static double temperature(Random random)
    {
        double pick = random.nextDouble();
        int type = CLASS_WEIGHT.length - 1;
        for (int k = 0; k < CLASS_WEIGHT.length; k++) {
            pick -= CLASS_WEIGHT[k];
            if (pick < 0.0) {
                type = k;
                break;
            }
        }
        double low = StrictMath.log(CLASS_T_MIN[type]);
        double high = StrictMath.log(CLASS_T_MAX[type]);
        return StrictMath.exp(low + (high - low) * random.nextDouble());
    }

    /** Couleur ARGB opaque d'un corps noir a la temperature {@code t} (K), table {@link #TABLE_T} interpolee. */
    private static int blackBody(double t)
    {
        int last = TABLE_T.length - 1;
        int k = 0;
        while (k < last - 1 && t > TABLE_T[k + 1]) k++;
        double f = StrictMath.max(0.0, StrictMath.min(1.0, (t - TABLE_T[k]) / (TABLE_T[k + 1] - TABLE_T[k])));
        int rgb = 0xFF000000;
        for (int c = 0; c < 3; c++) {
            double value = TABLE_RGB[k][c] + (TABLE_RGB[k + 1][c] - TABLE_RGB[k][c]) * f;
            rgb |= (int) StrictMath.round(value) << (16 - 8 * c);
        }
        return rgb;
    }

    /**
     * SplitMix64 : generateur 64 bits fixe (le format de {@code java.util.Random} ou de {@code SplittableRandom} ne
     * fait pas partie de ce contrat) ; une suite par graine.
     */
    private static final class Random
    {
        private long state;

        Random(long seed) { state = seed; }

        long nextLong()
        {
            long v = state += 0x9E3779B97F4A7C15L;
            v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
            v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
            return v ^ (v >>> 31);
        }

        /** Reel uniforme dans {@code [0, 1)}, 53 bits. */
        double nextDouble() { return (nextLong() >>> 11) * 0x1.0p-53; }
    }
}
