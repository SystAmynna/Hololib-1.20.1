package com.amynna.hololib.modules.dimension.space.math;

/**
 * Unites et conversions des modules Space (voir {@code docs/space/03-modele-donnees.md} par. 1 et
 * {@code 04-mathematiques.md} par. 3) : km (positions et rayons), UA (orbites), al (galaxie), jours simules (periodes),
 * km par tick (vitesse du monde).
 * <p>
 * Temps simule : {@code t = (gameTime + partialTick) * echelle / 86 400} jours, {@code echelle} = secondes simulees
 * par tick ({@link SpaceMotionSettings#timeScale()}). Calcule en {@code double} depuis le {@code long} du jeu (exact
 * jusqu'a 2^53 ticks) ; la position sur une orbite ne garde que la fraction de tour ({@link #orbitFraction}), si bien
 * que la precision ne se degrade pas avec la duree de la partie.
 */
public final class SpaceUnits
{
    /** Kilometres par unite astronomique (meme valeur que la constante privee de {@code SpaceGalaxy}). */
    public static final double AU_KM = 149_597_870.7;
    /** Kilometres par annee-lumiere (annee julienne). */
    public static final double LIGHT_YEAR_KM = 9_460_730_472_580.8;
    /** Secondes par jour simule. */
    public static final double SECONDS_PER_DAY = 86_400.0;
    /** Ticks de jeu par seconde reelle (vitesse nominale du serveur). */
    public static final int TICKS_PER_SECOND = 20;
    /** Un tour, en radians. */
    public static final double TWO_PI = 2.0 * Math.PI;

    private SpaceUnits() {}

    // ---------------------------------------------------------------- distances

    public static double auToKm(double au) { return au * AU_KM; }

    public static double kmToAu(double km) { return km / AU_KM; }

    public static double lightYearsToKm(double ly) { return ly * LIGHT_YEAR_KM; }

    public static double kmToLightYears(double km) { return km / LIGHT_YEAR_KM; }

    // ---------------------------------------------------------------- temps

    /**
     * Temps simule, en jours, au tick {@code gameTime} avance de {@code partialTick} (fraction de tick du rendu, 0
     * pour un pas de simulation).
     *
     * @param secondsPerTick secondes simulees par tick ({@link SpaceMotionSettings#timeScale()})
     */
    public static double days(long gameTime, float partialTick, double secondsPerTick)
    {
        return ((double) gameTime + partialTick) * secondsPerTick / SECONDS_PER_DAY;
    }

    /** Temps simule, en jours, au debut du tick {@code gameTime} (pas de simulation). */
    public static double days(long gameTime, double secondsPerTick)
    {
        return (double) gameTime * secondsPerTick / SECONDS_PER_DAY;
    }

    /** Partie fractionnaire, dans {@code [0, 1[} (aussi pour une valeur negative). */
    public static double frac(double value)
    {
        double f = value - Math.floor(value);
        // Une valeur negative minuscule donne 1.0 apres arrondi : ramenee a 0.
        return f >= 1.0 ? 0.0 : f;
    }

    /**
     * Fraction de tour {@code [0, 1[} parcourue au temps {@code tDays} par une orbite de periode {@code periodDays}
     * et de phase {@code phaseDegrees} (anomalie moyenne a {@code t = 0}) : {@code frac(t / P + phase / 360)}.
     * L'anomalie moyenne vaut {@code 2 pi} fois ce resultat.
     */
    public static double orbitFraction(double tDays, double periodDays, double phaseDegrees)
    {
        return frac(frac(tDays / periodDays) + phaseDegrees / 360.0);
    }
}
