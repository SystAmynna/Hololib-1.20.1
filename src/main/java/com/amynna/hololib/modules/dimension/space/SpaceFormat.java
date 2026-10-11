package com.amynna.hololib.modules.dimension.space;

import com.amynna.hololib.modules.dimension.space.math.SpaceUnits;

import java.util.Locale;

/**
 * Formats d'affichage communs des modules Space (fenetre du Space Controller, HUD de navigation de Space Pilot,
 * distance au regard de Space Knowledge) : vitesse (unite choisie ou imposee, T34), distance, distance galactique (T33), duree (T22), nombre a trois chiffres significatifs « a la francaise » (virgule decimale,
 * milliers separes par une espace). Extrait de {@code SpaceControllerWindow} en T20 pour que les deux affichages
 * restent identiques.
 * <p>
 * Chaque appel alloue des chaines : a reserver aux rafraichissements (synchronisation, quelques fois par seconde),
 * jamais au dessin d'une frame. Code commun, aucune classe client.
 */
public final class SpaceFormat
{
    /** Au-dela (km/s), la vitesse est affichee en UA/s. */
    public static final double SPEED_AU_THRESHOLD = 0.01 * SpaceUnits.AU_KM;
    /** Au-dela (km), une distance est affichee en UA. */
    public static final double DISTANCE_AU_THRESHOLD = 0.01 * SpaceUnits.AU_KM;

    private SpaceFormat() {}

    /**
     * Vitesse signee (km par tick) en km/s, ou en UA/s au-dela de 0,01 UA/s en valeur absolue ; une vitesse negative
     * (marche arriere) garde son signe ({@code -12,3 km/s}).
     */
    public static String speed(double kmPerTick) { return speed(kmPerTick, speedInAu(kmPerTick)); }

    /**
     * Vrai si {@link #speed(double)} afficherait cette vitesse (km par tick) en UA/s : au-dela de 0,01 UA/s en valeur
     * absolue. Pour afficher deux vitesses dans la meme unite (vitesse de croisiere, T34), choisir l'unite sur la plus
     * grande. Sans allocation.
     */
    public static boolean speedInAu(double kmPerTick)
    {
        return Math.abs(kmPerTick * SpaceUnits.TICKS_PER_SECOND) >= SPEED_AU_THRESHOLD;
    }

    /** Vitesse signee (km par tick) en UA/s si {@code au}, sinon en km/s (unite imposee, T34). */
    public static String speed(double kmPerTick, boolean au)
    {
        double kms = kmPerTick * SpaceUnits.TICKS_PER_SECOND;
        if (au) return number(SpaceUnits.kmToAu(kms)) + " UA/s";
        return number(kms) + " km/s";
    }

    /** Distance (km) en km, ou en UA au-dela de 0,01 UA ; {@code "-"} si elle n'est pas finie. */
    public static String distance(double km)
    {
        if (!Double.isFinite(km)) return "-";
        if (Math.abs(km) >= DISTANCE_AU_THRESHOLD) return number(SpaceUnits.kmToAu(km)) + " UA";
        return number(km) + " km";
    }

    /**
     * Distance galactique (annees-lumiere) : {@code "4,24 al"} ; {@code "-"} si elle n'est pas finie. Distance d'une
     * etoile de fond sous son nom (Space Knowledge, T33).
     */
    public static String lightYears(double ly)
    {
        if (!Double.isFinite(ly)) return "-";
        return number(ly) + " al";
    }

    /**
     * Duree en secondes entieres, arrondie au-dessus ({@code 0} pour une duree nulle, negative ou non finie) : temps
     * restant d'un saut (T22). Sans allocation : comparer cette valeur avant de reconstruire un texte.
     */
    public static int wholeSeconds(double seconds)
    {
        if (!(seconds > 0.0)) return 0;
        double value = Math.ceil(seconds);
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    /** Duree {@code n} secondes : {@code "12 s"} (temps restant d'un saut, Controller et HUD). */
    public static String seconds(int n) { return n + " s"; }

    /** Nombre a trois chiffres significatifs (a la francaise : virgule decimale, milliers separes par une espace). */
    public static String number(double value)
    {
        if (!Double.isFinite(value)) return "-";
        double magnitude = Math.abs(value);
        if (magnitude < 1e-6) return "0";
        if (magnitude >= 1000.0) return String.format(Locale.ROOT, "%,.0f", value).replace(',', ' ');
        int decimals = Math.min(6, Math.max(0, 2 - (int) Math.floor(Math.log10(magnitude))));
        return String.format(Locale.ROOT, "%." + decimals + "f", value).replace('.', ',');
    }

    /**
     * Arrondi a trois chiffres significatifs (valeurs affichees : un changement invisible a l'ecran ne declenche ni
     * envoi ni reconstruction de texte). Sans allocation.
     */
    public static double round3(double value)
    {
        if (!Double.isFinite(value) || value == 0.0) return value;
        double step = Math.pow(10.0, Math.floor(Math.log10(Math.abs(value))) - 2.0);
        return Math.round(value / step) * step;
    }
}
