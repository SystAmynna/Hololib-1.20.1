package com.amynna.hololib.modules.dimension.space.math;

import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Vector3d;

/**
 * Reglages du mouvement du monde et de l'horloge des orbites (immuable), tenus par le Space Environment, sauvegardes et
 * synchronises avec son etat chaud (voir {@code docs/space/04-mathematiques.md} par. 2, 3, 5). Les valeurs par defaut
 * ({@link #DEFAULT}) sont toutes ici.
 * <p>
 * Le constructeur borne chaque valeur et leve sur une valeur non finie (meme regle que le modele) : tout reglage
 * existant est valide.
 *
 * @param forwardYaw   yaw de l'axe avant du monde {@code F_W}, degres {@code [-180, 180[} (180 = nord)
 * @param forwardPitch pitch de l'axe avant, degres, borne a {@code [-80, 80]} (pour que l'axe droit
 *                     {@code F x Y} reste defini)
 * @param maxSpeed     vitesse nominale maximale {@code V_max}, km par tick
 * @param turnRate     taux de lacet / tangage a pleine deviation {@code turn}, degres par tick
 * @param rollSpeed    vitesse de roulis, degres par tick
 * @param timeScale    secondes simulees par tick pour les orbites ({@code 0} = orbites figees)
 */
public record SpaceMotionSettings(double forwardYaw, double forwardPitch, double maxSpeed, double turnRate,
                                  double rollSpeed, double timeScale)
{
    /** Axe avant par defaut : nord ({@code (0, 0, -1)}). */
    public static final double DEFAULT_FORWARD_YAW = 180.0;
    public static final double DEFAULT_FORWARD_PITCH = 0.0;
    /** {@code V_max} par defaut, km par tick. */
    public static final double DEFAULT_MAX_SPEED = 1.0e7;
    /** {@code turn} par defaut, degres par tick. */
    public static final double DEFAULT_TURN_RATE = 1.5;
    /** {@code rollSpeed} par defaut, degres par tick. */
    public static final double DEFAULT_ROLL_SPEED = 2.0;
    /** Echelle de temps par defaut : 3 600 s simulees par tick (un tick = une heure, une annee ~ 7 min 18 s). */
    public static final double DEFAULT_TIME_SCALE = 3_600.0;

    /** Bornes. */
    public static final double MAX_FORWARD_PITCH = 80.0;
    public static final double MIN_MAX_SPEED = 1.0;
    public static final double MAX_MAX_SPEED = 1.0e9;
    public static final double MIN_RATE = 0.01;
    public static final double MAX_RATE = 30.0;
    public static final double MAX_TIME_SCALE = 1.0e7;

    /** Reglages par defaut. */
    public static final SpaceMotionSettings DEFAULT = new SpaceMotionSettings(DEFAULT_FORWARD_YAW,
            DEFAULT_FORWARD_PITCH, DEFAULT_MAX_SPEED, DEFAULT_TURN_RATE, DEFAULT_ROLL_SPEED, DEFAULT_TIME_SCALE);

    public SpaceMotionSettings
    {
        forwardYaw = SpaceLimits.sanitizeAngle(forwardYaw);
        forwardPitch = clamp(forwardPitch, -MAX_FORWARD_PITCH, MAX_FORWARD_PITCH);
        maxSpeed = clamp(maxSpeed, MIN_MAX_SPEED, MAX_MAX_SPEED);
        turnRate = clamp(turnRate, MIN_RATE, MAX_RATE);
        rollSpeed = clamp(rollSpeed, MIN_RATE, MAX_RATE);
        timeScale = clamp(timeScale, 0.0, MAX_TIME_SCALE);
    }

    // ---------------------------------------------------------------- copies modifiees

    public SpaceMotionSettings withForward(double yaw, double pitch)
    {
        return new SpaceMotionSettings(yaw, pitch, maxSpeed, turnRate, rollSpeed, timeScale);
    }

    public SpaceMotionSettings withMaxSpeed(double maxSpeed)
    {
        return new SpaceMotionSettings(forwardYaw, forwardPitch, maxSpeed, turnRate, rollSpeed, timeScale);
    }

    public SpaceMotionSettings withTurnRate(double turnRate)
    {
        return new SpaceMotionSettings(forwardYaw, forwardPitch, maxSpeed, turnRate, rollSpeed, timeScale);
    }

    public SpaceMotionSettings withRollSpeed(double rollSpeed)
    {
        return new SpaceMotionSettings(forwardYaw, forwardPitch, maxSpeed, turnRate, rollSpeed, timeScale);
    }

    public SpaceMotionSettings withTimeScale(double timeScale)
    {
        return new SpaceMotionSettings(forwardYaw, forwardPitch, maxSpeed, turnRate, rollSpeed, timeScale);
    }

    // ---------------------------------------------------------------- axes du monde (sans allocation)

    /**
     * Axe avant du monde {@code F_W} (unitaire, axes du monde) : {@code (-sin yaw cos pitch, -sin pitch,
     * cos yaw cos pitch)}.
     */
    public Vector3d forward(Vector3d dest)
    {
        double yaw = Math.toRadians(forwardYaw);
        double pitch = Math.toRadians(forwardPitch);
        double horizontal = StrictMath.cos(pitch);
        dest.x = -StrictMath.sin(yaw) * horizontal;
        dest.y = -StrictMath.sin(pitch);
        dest.z = StrictMath.cos(yaw) * horizontal;
        return dest;
    }

    /** Axe droit du monde {@code R_W = normalize(F_W x Y) = (-cos yaw, 0, -sin yaw)} (est pour un avant au nord). */
    public Vector3d right(Vector3d dest)
    {
        double yaw = Math.toRadians(forwardYaw);
        dest.x = -StrictMath.cos(yaw);
        dest.y = 0.0;
        dest.z = -StrictMath.sin(yaw);
        return dest;
    }

    /** Axe haut du monde {@code U_W = R_W x F_W} ({@code (0, 1, 0)} pour un avant horizontal). */
    public Vector3d up(Vector3d dest)
    {
        double yaw = Math.toRadians(forwardYaw);
        double pitch = Math.toRadians(forwardPitch);
        double sinPitch = StrictMath.sin(pitch);
        dest.x = -StrictMath.sin(yaw) * sinPitch;
        dest.y = StrictMath.cos(pitch);
        dest.z = StrictMath.cos(yaw) * sinPitch;
        return dest;
    }

    /** Temps simule en jours au tick {@code gameTime} + {@code partialTick} ({@link SpaceUnits#days}). */
    public double days(long gameTime, float partialTick)
    {
        return SpaceUnits.days(gameTime, partialTick, timeScale);
    }

    // ---------------------------------------------------------------- reseau (ordre : fyaw, fpitch, vmax, turn, roll, tscale)

    public void write(FriendlyByteBuf buf)
    {
        buf.writeDouble(forwardYaw);
        buf.writeDouble(forwardPitch);
        buf.writeDouble(maxSpeed);
        buf.writeDouble(turnRate);
        buf.writeDouble(rollSpeed);
        buf.writeDouble(timeScale);
    }

    /** Lecture stricte : leve sur une valeur non finie (les valeurs finies sont bornees). */
    public static SpaceMotionSettings read(FriendlyByteBuf buf)
    {
        double yaw = buf.readDouble();
        double pitch = buf.readDouble();
        double maxSpeed = buf.readDouble();
        double turn = buf.readDouble();
        double roll = buf.readDouble();
        double scale = buf.readDouble();
        return new SpaceMotionSettings(yaw, pitch, maxSpeed, turn, roll, scale);
    }

    // ---------------------------------------------------------------- NBT

    /** Format : {@code { fyaw, fpitch, vmax, turn, roll, tscale }} (cles courtes et stables). */
    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("fyaw", forwardYaw);
        tag.putDouble("fpitch", forwardPitch);
        tag.putDouble("vmax", maxSpeed);
        tag.putDouble("turn", turnRate);
        tag.putDouble("roll", rollSpeed);
        tag.putDouble("tscale", timeScale);
        return tag;
    }

    /** Lecture tolerante : cle manquante ou valeur non finie = valeur par defaut, valeur hors bornes bornee. */
    public static SpaceMotionSettings load(CompoundTag tag)
    {
        return new SpaceMotionSettings(number(tag, "fyaw", DEFAULT_FORWARD_YAW),
                number(tag, "fpitch", DEFAULT_FORWARD_PITCH), number(tag, "vmax", DEFAULT_MAX_SPEED),
                number(tag, "turn", DEFAULT_TURN_RATE), number(tag, "roll", DEFAULT_ROLL_SPEED),
                number(tag, "tscale", DEFAULT_TIME_SCALE));
    }

    private static double number(CompoundTag tag, String key, double fallback)
    {
        if (!tag.contains(key, Tag.TAG_ANY_NUMERIC)) return fallback;
        double value = tag.getDouble(key);
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double min, double max)
    {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Valeur non finie : " + value);
        return Math.max(min, Math.min(max, value));
    }
}
