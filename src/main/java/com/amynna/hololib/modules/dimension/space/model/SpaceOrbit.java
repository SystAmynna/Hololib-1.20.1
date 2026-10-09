package com.amynna.hololib.modules.dimension.space.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Orbite keplerienne d'un corps autour de son parent (immuable). Plan de l'orbite = plan local {@code XZ}, parent au
 * foyer, periapse sur {@code +X}, parcours de {@code +X} vers {@code +Z} ; l'orientation {@code (yaw, pitch, roll)}
 * suit les conventions de {@code HoloTransform} (YXZ, {@code HoloEditMath.rotation}). Voir
 * {@code docs/space/03-modele-donnees.md} par. 2 et {@code 04-mathematiques.md} par. 3.
 * <p>
 * Le constructeur borne chaque valeur ({@link SpaceLimits}) et leve sur une valeur non finie.
 *
 * @param semiMajorAxis demi-grand axe {@code a}, en UA
 * @param eccentricity  excentricite {@code e}, {@code [0, 0.95]}
 * @param yaw           orientation, degres {@code [-180, 180[}
 * @param pitch         orientation, degres {@code [-180, 180[}
 * @param roll          orientation, degres {@code [-180, 180[}
 * @param period        periode, en jours simules
 * @param phase         anomalie moyenne a {@code t = 0}, degres {@code [0, 360[}
 */
public record SpaceOrbit(double semiMajorAxis, double eccentricity, double yaw, double pitch, double roll,
                         double period, double phase)
{
    /** Orbite par defaut d'un tag incomplet : 1 UA, circulaire, un an. */
    private static final double DEFAULT_A = 1.0;
    private static final double DEFAULT_PERIOD = 365.25;

    public SpaceOrbit
    {
        semiMajorAxis = SpaceLimits.sanitizeSemiMajorAxis(semiMajorAxis);
        eccentricity = SpaceLimits.sanitizeEccentricity(eccentricity);
        yaw = SpaceLimits.sanitizeAngle(yaw);
        pitch = SpaceLimits.sanitizeAngle(pitch);
        roll = SpaceLimits.sanitizeAngle(roll);
        period = SpaceLimits.sanitizePeriod(period);
        phase = SpaceLimits.sanitizePhase(phase);
    }

    /** Orbite circulaire dans le plan de reference. */
    public static SpaceOrbit circular(double semiMajorAxis, double period, double phase)
    {
        return new SpaceOrbit(semiMajorAxis, 0.0, 0.0, 0.0, 0.0, period, phase);
    }

    /** Demi-petit axe {@code b = a * sqrt(1 - e^2)}, en UA. */
    public double semiMinorAxis() { return semiMajorAxis * Math.sqrt(1.0 - eccentricity * eccentricity); }

    /** Distance maximale au parent {@code a * (1 + e)}, en UA. */
    public double apoapsis() { return semiMajorAxis * (1.0 + eccentricity); }

    /** Distance minimale au parent {@code a * (1 - e)}, en UA. */
    public double periapsis() { return semiMajorAxis * (1.0 - eccentricity); }

    // ---------------------------------------------------------------- copies modifiees

    public SpaceOrbit withShape(double semiMajorAxis, double eccentricity)
    {
        return new SpaceOrbit(semiMajorAxis, eccentricity, yaw, pitch, roll, period, phase);
    }

    public SpaceOrbit withOrientation(double yaw, double pitch, double roll)
    {
        return new SpaceOrbit(semiMajorAxis, eccentricity, yaw, pitch, roll, period, phase);
    }

    public SpaceOrbit withPeriod(double period)
    {
        return new SpaceOrbit(semiMajorAxis, eccentricity, yaw, pitch, roll, period, phase);
    }

    public SpaceOrbit withPhase(double phase)
    {
        return new SpaceOrbit(semiMajorAxis, eccentricity, yaw, pitch, roll, period, phase);
    }

    // ---------------------------------------------------------------- reseau (ordre : a, e, yaw, pitch, roll, period, phase)

    public void write(FriendlyByteBuf buf)
    {
        buf.writeDouble(semiMajorAxis);
        buf.writeDouble(eccentricity);
        buf.writeDouble(yaw);
        buf.writeDouble(pitch);
        buf.writeDouble(roll);
        buf.writeDouble(period);
        buf.writeDouble(phase);
    }

    /** Lecture stricte : leve sur une valeur non finie. */
    public static SpaceOrbit read(FriendlyByteBuf buf)
    {
        double a = buf.readDouble();
        double e = buf.readDouble();
        double yaw = buf.readDouble();
        double pitch = buf.readDouble();
        double roll = buf.readDouble();
        double period = buf.readDouble();
        double phase = buf.readDouble();
        return new SpaceOrbit(a, e, yaw, pitch, roll, period, phase);
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("a", semiMajorAxis);
        tag.putDouble("e", eccentricity);
        tag.putDouble("yaw", yaw);
        tag.putDouble("pitch", pitch);
        tag.putDouble("roll", roll);
        tag.putDouble("period", period);
        tag.putDouble("phase", phase);
        return tag;
    }

    /**
     * Lecture tolerante : une cle manquante prend sa valeur par defaut, une valeur hors bornes est bornee. Leve
     * seulement sur une valeur non finie (l'appelant saute alors le corps).
     */
    public static SpaceOrbit load(CompoundTag tag)
    {
        return new SpaceOrbit(number(tag, "a", DEFAULT_A), number(tag, "e", 0.0), number(tag, "yaw", 0.0),
                number(tag, "pitch", 0.0), number(tag, "roll", 0.0), number(tag, "period", DEFAULT_PERIOD),
                number(tag, "phase", 0.0));
    }

    private static double number(CompoundTag tag, String key, double fallback)
    {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : fallback;
    }
}
