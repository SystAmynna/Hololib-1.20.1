package com.amynna.hololib.modules.dimension.space.model;

/**
 * Bornes du modele de la galaxie et fonctions d'assainissement (voir {@code docs/space/03-modele-donnees.md} par. 3).
 * <p>
 * Les fonctions {@code sanitizeXxx} bornent une valeur <b>finie</b> et levent {@link IllegalArgumentException} sur
 * une valeur non finie : les constructeurs des elements les appellent, si bien que tout element existant est borne. Une
 * operation d'edition qui recoit des nombres bruts verifie d'abord {@link #finite} pour refuser proprement (resultat)
 * plutot que lever. Toutes ces fonctions sont idempotentes : un aller-retour NBT ou reseau rend la meme valeur.
 */
public final class SpaceLimits
{
    /** Nombre maximal de systemes dans une galaxie. */
    public static final int MAX_SYSTEMS = 256;
    /** Nombre maximal de corps dans un systeme (48 depuis T16 : Sol en compte 34 ; 32 au lot 1). */
    public static final int MAX_BODIES = 48;
    /** Longueur maximale d'un nom, en unites UTF-16. */
    public static final int MAX_NAME_LENGTH = 32;

    /** Distance maximale d'un systeme a l'origine galactique, en annees-lumiere. */
    public static final double MAX_GALACTIC_DISTANCE = 100_000.0;

    /** Rayon d'un corps, en km. */
    public static final double MIN_RADIUS = 1.0;
    public static final double MAX_RADIUS = 1.0e7;

    /** Demi-grand axe d'une orbite, en UA (minimum 1e-5 depuis T16 : Phobos a 6,27e-5 UA ; 1e-4 au lot 1). */
    public static final double MIN_SEMI_MAJOR_AXIS = 1.0e-5;
    public static final double MAX_SEMI_MAJOR_AXIS = 1.0e4;

    /** Excentricite maximale (ellipse bien formee, Kepler convergent). */
    public static final double MAX_ECCENTRICITY = 0.95;

    /**
     * Periode orbitale, en jours simules (maximum 1e9 depuis T16 : Proxima autour d'Alpha du Centaure A, 2e8 jours ;
     * 1e7 au lot 1).
     */
    public static final double MIN_PERIOD = 0.01;
    public static final double MAX_PERIOD = 1.0e9;

    /** Luminosite d'une etoile, relative au Soleil. */
    public static final float MIN_LUMINOSITY = 1.0e-4f;
    public static final float MAX_LUMINOSITY = 1.0e6f;

    /** Vitesse de distorsion du saut interstellaire (reglage de l'Environment, T15), en annees-lumiere par seconde. */
    public static final double MIN_WARP_SPEED = 0.01;
    public static final double MAX_WARP_SPEED = 1_000.0;

    /**
     * Periode siderale de la rotation propre (T28), en heures, <b>en valeur absolue</b> : une periode non nulle est
     * ramenee a {@code [MIN, MAX]} en gardant son signe ({@code < 0} = retrograde) ; {@code 0} = rotation synchrone.
     * Minimum 36 s (aucun corps du modele ne tourne plus vite), maximum environ 114 ans (Venus : 5 833 h).
     */
    public static final double MIN_ROTATION_PERIOD = 0.01;
    public static final double MAX_ROTATION_PERIOD = 1.0e6;
    /** Obliquite de la rotation propre (T28), degres. */
    public static final double MAX_OBLIQUITY = 180.0;

    /**
     * Masse d'un corps (T39, D29), en kg : minimum 10^10 kg (un corps de 1 km de rayon pese de l'ordre de 10^12 kg ;
     * Deimos 1,5e15), maximum 10^33 kg (environ 500 masses solaires). La masse ne sert qu'a choisir le referentiel
     * (spheres d'influence), jamais aux orbites ni au mouvement.
     */
    public static final double MIN_MASS = 1.0e10;
    public static final double MAX_MASS = 1.0e33;

    private SpaceLimits() {}

    // ---------------------------------------------------------------- verification

    /** Vrai si toutes les valeurs sont finies (ni NaN ni infini). */
    public static boolean finite(double... values)
    {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    // ---------------------------------------------------------------- noms

    /**
     * Nettoie un nom : caracteres de controle (retours a la ligne compris) et surrogates isoles retires, tronque a
     * {@link #MAX_NAME_LENGTH} unites UTF-16 sans couper un caractere, espaces de bord retires. {@code null} donne
     * une chaine vide. Le resultat s'ecrit et se relit a l'identique sur le reseau.
     */
    public static String sanitizeName(String value)
    {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.SURROGATE) continue;
            // Espaces de tete : retires avant de compter la longueur.
            if (out.length() == 0 && Character.isWhitespace(codePoint)) continue;
            if (out.length() + Character.charCount(codePoint) > MAX_NAME_LENGTH) break;
            out.appendCodePoint(codePoint);
        }
        return out.toString().strip();
    }

    // ---------------------------------------------------------------- nombres

    /** Rayon borne a {@code [MIN_RADIUS, MAX_RADIUS]}. */
    public static double sanitizeRadius(double radius) { return clamp(radius, MIN_RADIUS, MAX_RADIUS); }

    /** Demi-grand axe borne a {@code [MIN_SEMI_MAJOR_AXIS, MAX_SEMI_MAJOR_AXIS]}. */
    public static double sanitizeSemiMajorAxis(double a) { return clamp(a, MIN_SEMI_MAJOR_AXIS, MAX_SEMI_MAJOR_AXIS); }

    /** Excentricite bornee a {@code [0, MAX_ECCENTRICITY]}. */
    public static double sanitizeEccentricity(double e) { return clamp(e, 0.0, MAX_ECCENTRICITY); }

    /** Periode bornee a {@code [MIN_PERIOD, MAX_PERIOD]}. */
    public static double sanitizePeriod(double period) { return clamp(period, MIN_PERIOD, MAX_PERIOD); }

    /** Luminosite bornee a {@code [MIN_LUMINOSITY, MAX_LUMINOSITY]}. */
    public static float sanitizeLuminosity(float luminosity)
    {
        requireFinite(luminosity);
        return Math.max(MIN_LUMINOSITY, Math.min(MAX_LUMINOSITY, luminosity));
    }

    /** Angle d'orientation (yaw, pitch, roll) ramene a {@code [-180, 180[} degres. */
    public static double sanitizeAngle(double degrees)
    {
        requireFinite(degrees);
        double wrapped = degrees % 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        return wrapped;
    }

    /** Phase ramenee a {@code [0, 360[} degres. */
    public static double sanitizePhase(double degrees)
    {
        requireFinite(degrees);
        double wrapped = degrees % 360.0;
        if (wrapped < 0.0) wrapped += 360.0;
        if (wrapped >= 360.0) wrapped -= 360.0;
        return wrapped;
    }

    /** Masse bornee a {@code [MIN_MASS, MAX_MASS]} (kg, T39) ; une masse nulle ou negative donne {@code MIN_MASS}. */
    public static double sanitizeMass(double mass) { return clamp(mass, MIN_MASS, MAX_MASS); }

    /** Vitesse de distorsion bornee a {@code [MIN_WARP_SPEED, MAX_WARP_SPEED]} (al/s). */
    public static double sanitizeWarpSpeed(double warpSpeed)
    {
        return clamp(warpSpeed, MIN_WARP_SPEED, MAX_WARP_SPEED);
    }

    // ---------------------------------------------------------------- rotation propre (T28)
    // Les quatre valeurs de la rotation propre sont ramenees a la precision d'un float (C13 pour les bornes) : elles
    // circulent en float sur le reseau (16 octets par corps au lieu de 32) et l'aller-retour reste exact ; le calcul de
    // l'orientation (double, StrictMath) part donc des memes valeurs sur le serveur et sur les clients.

    /**
     * Periode de rotation propre (heures, signee) : {@code 0} reste {@code 0} (synchrone), sinon valeur absolue bornee
     * a {@code [MIN_ROTATION_PERIOD, MAX_ROTATION_PERIOD]}, signe garde, precision float.
     */
    public static double sanitizeRotationPeriod(double hours)
    {
        requireFinite(hours);
        if (hours == 0.0) return 0.0;
        double magnitude = toFloatPrecision(clamp(Math.abs(hours), MIN_ROTATION_PERIOD, MAX_ROTATION_PERIOD));
        return hours < 0.0 ? -magnitude : magnitude;
    }

    /** Obliquite (degres) bornee a {@code [0, MAX_OBLIQUITY]}, precision float. */
    public static double sanitizeObliquity(double degrees)
    {
        return toFloatPrecision(clamp(degrees, 0.0, MAX_OBLIQUITY));
    }

    /** Longitude du noeud du pole (degres) ramenee a {@code [-180, 180[} ({@link #sanitizeAngle}), precision float. */
    public static double sanitizeRotationNode(double degrees)
    {
        double value = toFloatPrecision(sanitizeAngle(degrees));
        // L'arrondi en float peut atteindre la borne exclue.
        return value >= 180.0 ? -180.0 : value;
    }

    /** Phase de rotation propre (degres) ramenee a {@code [0, 360[} ({@link #sanitizePhase}), precision float. */
    public static double sanitizeRotationPhase(double degrees)
    {
        double value = toFloatPrecision(sanitizePhase(degrees));
        return value >= 360.0 ? 0.0 : value;
    }

    /** Couleur d'un corps : ARGB rendue opaque (l'alpha est ignore pour les corps). */
    public static int sanitizeColor(int argb) { return argb | 0xFF000000; }

    /**
     * Facteur a appliquer a une position galactique pour que sa norme ne depasse pas
     * {@link #MAX_GALACTIC_DISTANCE} : 1 si elle est deja dans la borne, sinon un facteur qui la ramene juste en
     * dessous (marge de {@code 1e-12} pour que l'operation soit idempotente malgre les arrondis).
     */
    public static double galacticPositionFactor(double x, double y, double z)
    {
        requireFinite(x);
        requireFinite(y);
        requireFinite(z);
        double max = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
        if (max == 0.0) return 1.0;
        // Normalisation par la plus grande composante : pas de depassement de capacite au carre.
        double nx = x / max;
        double ny = y / max;
        double nz = z / max;
        double norm = max * Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (norm <= MAX_GALACTIC_DISTANCE) return 1.0;
        return MAX_GALACTIC_DISTANCE / norm * (1.0 - 1.0e-12);
    }

    // ---------------------------------------------------------------- interne

    private static double clamp(double value, double min, double max)
    {
        requireFinite(value);
        return Math.max(min, Math.min(max, value));
    }

    /** Valeur finie arrondie au float le plus proche (sans depassement : l'appelant l'a deja bornee). */
    private static double toFloatPrecision(double value) { return (float) value; }

    private static void requireFinite(double value)
    {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Valeur non finie : " + value);
    }
}
