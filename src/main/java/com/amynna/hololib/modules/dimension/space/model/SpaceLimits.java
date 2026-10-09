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
    /** Nombre maximal de corps dans un systeme. */
    public static final int MAX_BODIES = 32;
    /** Longueur maximale d'un nom, en unites UTF-16. */
    public static final int MAX_NAME_LENGTH = 32;

    /** Distance maximale d'un systeme a l'origine galactique, en annees-lumiere. */
    public static final double MAX_GALACTIC_DISTANCE = 100_000.0;

    /** Rayon d'un corps, en km. */
    public static final double MIN_RADIUS = 1.0;
    public static final double MAX_RADIUS = 1.0e7;

    /** Demi-grand axe d'une orbite, en UA. */
    public static final double MIN_SEMI_MAJOR_AXIS = 1.0e-4;
    public static final double MAX_SEMI_MAJOR_AXIS = 1.0e4;

    /** Excentricite maximale (ellipse bien formee, Kepler convergent). */
    public static final double MAX_ECCENTRICITY = 0.95;

    /** Periode orbitale, en jours simules. */
    public static final double MIN_PERIOD = 0.01;
    public static final double MAX_PERIOD = 1.0e7;

    /** Luminosite d'une etoile, relative au Soleil. */
    public static final float MIN_LUMINOSITY = 1.0e-4f;
    public static final float MAX_LUMINOSITY = 1.0e6f;

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

    private static void requireFinite(double value)
    {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Valeur non finie : " + value);
    }
}
