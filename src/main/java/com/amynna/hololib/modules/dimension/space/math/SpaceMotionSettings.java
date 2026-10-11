package com.amynna.hololib.modules.dimension.space.math;

import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Vector3d;

/**
 * Reglages du mouvement du monde, de l'horloge des orbites et du saut (vitesse de distorsion, T15) (immuable), tenus
 * par le Space Environment, sauvegardes et synchronises avec son etat chaud (voir
 * {@code docs/space/04-mathematiques.md} par. 2, 3, 5, 8). Les valeurs par defaut ({@link #DEFAULT}) sont toutes ici.
 * <p>
 * Le constructeur borne chaque valeur et leve sur une valeur non finie (meme regle que le modele) : tout reglage
 * existant est valide.
 * <p>
 * <b>Horloge des orbites</b> (T38, D28) : echelle {@code timeScale} et <b>epoque</b> {@code (epochTick, epochDays)},
 * temps simule {@link #days} = {@code epochDays + (gameTime - epochTick + partialTick) * timeScale / 86 400}. L'epoque
 * n'est jamais reglee a la main : le Space Environment la pose au tick courant quand l'echelle change (temps simule
 * continu, aucun saut des astres) et a la migration d'un monde d'avant T38 ({@link #legacyClock()}).
 *
 * @param forwardYaw   yaw de l'axe avant du monde {@code F_W}, degres {@code [-180, 180[} (180 = nord)
 * @param forwardPitch pitch de l'axe avant, degres, borne a {@code [-80, 80]} (pour que l'axe droit
 *                     {@code F x Y} reste defini)
 * @param maxSpeed     vitesse nominale maximale {@code V_max}, km par tick
 * @param turnRate     taux de lacet / tangage a pleine deviation {@code turn}, degres par tick
 * @param rollSpeed    vitesse de roulis, degres par tick
 * @param timeScale    secondes simulees par tick pour les orbites ({@code 0} = orbites figees)
 * @param warpSpeed    vitesse de distorsion du saut interstellaire, annees-lumiere par seconde (T15), bornee par
 *                     {@link SpaceLimits#sanitizeWarpSpeed} ; n'intervient pas dans le pas de mouvement
 * @param epochTick    tick de jeu de l'epoque de l'horloge (T38) ; {@code 0} avec {@code epochDays = 0} = formule
 *                     d'avant T38
 * @param epochDays    temps simule a {@code epochTick}, jours (T38) ; fini
 */
public record SpaceMotionSettings(double forwardYaw, double forwardPitch, double maxSpeed, double turnRate,
                                  double rollSpeed, double timeScale, double warpSpeed, long epochTick,
                                  double epochDays)
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
    /**
     * Echelle de temps par defaut (T38, D28) : 360 s simulees par tick (un jour = 240 ticks = 12 s, un mois lunaire
     * ~ 5,5 min, une annee terrestre ~ 73 min).
     */
    public static final double DEFAULT_TIME_SCALE = 360.0;
    /**
     * Ancienne echelle par defaut (avant T38) : un monde charge avec elle et sans epoque passe a
     * {@link #DEFAULT_TIME_SCALE} ({@link #legacyClock()}).
     */
    public static final double LEGACY_TIME_SCALE = 3_600.0;
    /**
     * Vitesse de distorsion par defaut, al/s (bornes : {@link SpaceLimits#MIN_WARP_SPEED} a
     * {@link SpaceLimits#MAX_WARP_SPEED}).
     */
    public static final double DEFAULT_WARP_SPEED = 0.5;

    /** Bornes. */
    public static final double MAX_FORWARD_PITCH = 80.0;
    public static final double MIN_MAX_SPEED = 1.0;
    public static final double MAX_MAX_SPEED = 1.0e9;
    public static final double MIN_RATE = 0.01;
    public static final double MAX_RATE = 30.0;
    public static final double MAX_TIME_SCALE = 1.0e7;

    /** Reglages par defaut. */
    public static final SpaceMotionSettings DEFAULT = new SpaceMotionSettings(DEFAULT_FORWARD_YAW,
            DEFAULT_FORWARD_PITCH, DEFAULT_MAX_SPEED, DEFAULT_TURN_RATE, DEFAULT_ROLL_SPEED, DEFAULT_TIME_SCALE,
            DEFAULT_WARP_SPEED, 0L, 0.0);

    public SpaceMotionSettings
    {
        forwardYaw = SpaceLimits.sanitizeAngle(forwardYaw);
        forwardPitch = clamp(forwardPitch, -MAX_FORWARD_PITCH, MAX_FORWARD_PITCH);
        maxSpeed = clamp(maxSpeed, MIN_MAX_SPEED, MAX_MAX_SPEED);
        turnRate = clamp(turnRate, MIN_RATE, MAX_RATE);
        rollSpeed = clamp(rollSpeed, MIN_RATE, MAX_RATE);
        timeScale = clamp(timeScale, 0.0, MAX_TIME_SCALE);
        warpSpeed = SpaceLimits.sanitizeWarpSpeed(warpSpeed);
        if (!Double.isFinite(epochDays)) throw new IllegalArgumentException("Epoque non finie : " + epochDays);
    }

    // ---------------------------------------------------------------- copies modifiees

    public SpaceMotionSettings withForward(double yaw, double pitch)
    {
        return new SpaceMotionSettings(yaw, pitch, maxSpeed, turnRate, rollSpeed, timeScale,
                warpSpeed, epochTick, epochDays);
    }

    public SpaceMotionSettings withMaxSpeed(double maxSpeed)
    {
        return new SpaceMotionSettings(forwardYaw, forwardPitch, maxSpeed, turnRate, rollSpeed, timeScale,
                warpSpeed, epochTick, epochDays);
    }

    public SpaceMotionSettings withTurnRate(double turnRate)
    {
        return new SpaceMotionSettings(forwardYaw, forwardPitch, maxSpeed, turnRate, rollSpeed, timeScale,
                warpSpeed, epochTick, epochDays);
    }

    public SpaceMotionSettings withRollSpeed(double rollSpeed)
    {
        return new SpaceMotionSettings(forwardYaw, forwardPitch, maxSpeed, turnRate, rollSpeed, timeScale,
                warpSpeed, epochTick, epochDays);
    }

    /**
     * Echelle de temps, epoque <b>gardee</b> : seul le Space Environment change l'echelle, et il repose alors l'epoque
     * au tick courant (sinon le temps simule sauterait).
     */
    public SpaceMotionSettings withTimeScale(double timeScale)
    {
        return new SpaceMotionSettings(forwardYaw, forwardPitch, maxSpeed, turnRate, rollSpeed, timeScale,
                warpSpeed, epochTick, epochDays);
    }

    /** Vitesse de distorsion, al/s (bornee par le constructeur). */
    public SpaceMotionSettings withWarpSpeed(double warpSpeed)
    {
        return new SpaceMotionSettings(forwardYaw, forwardPitch, maxSpeed, turnRate, rollSpeed, timeScale,
                warpSpeed, epochTick, epochDays);
    }

    /** Epoque de l'horloge (T38) : temps simule {@code days} au tick {@code tick}. Reservee au Space Environment. */
    public SpaceMotionSettings withEpoch(long tick, double days)
    {
        return new SpaceMotionSettings(forwardYaw, forwardPitch, maxSpeed, turnRate, rollSpeed, timeScale,
                warpSpeed, tick, days);
    }

    /** Copie avec l'horloge (echelle et epoque) de {@code clock}, les autres reglages de celle-ci. */
    public SpaceMotionSettings withClock(SpaceMotionSettings clock)
    {
        return new SpaceMotionSettings(forwardYaw, forwardPitch, maxSpeed, turnRate, rollSpeed, clock.timeScale,
                warpSpeed, clock.epochTick, clock.epochDays);
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

    // ---------------------------------------------------------------- horloge des orbites (T38)

    /**
     * Temps simule en jours au tick {@code gameTime} + {@code partialTick} :
     * {@code epochDays + (gameTime - epochTick + partialTick) * timeScale / 86 400} ({@link SpaceUnits#days}). Seul
     * calcul du temps simule des modules Space (serveur, rejeu client, prediction, affichage, Configurator) ;
     * deterministe (memes octets, memes resultats des deux cotes).
     */
    public double days(long gameTime, float partialTick)
    {
        return SpaceUnits.days(gameTime, partialTick, epochTick, epochDays, timeScale);
    }

    /** Jours simules ecoules en {@code ticks} ticks a l'echelle de ces reglages (animation, sans epoque). */
    public double elapsedDays(double ticks)
    {
        return SpaceUnits.elapsedDays(ticks, timeScale);
    }

    /** Vrai si {@code other} a la meme horloge (echelle et epoque, bit a bit) : meme temps simule a tout tick. */
    public boolean sameClock(SpaceMotionSettings other)
    {
        return Double.compare(timeScale, other.timeScale) == 0 && epochTick == other.epochTick
                && Double.compare(epochDays, other.epochDays) == 0;
    }

    /**
     * Horloge d'avant T38 a migrer : sans epoque ({@code (0, 0)} : cles absentes, ou jamais posee) et a l'ancienne
     * echelle par defaut {@link #LEGACY_TIME_SCALE}. Une autre echelle (reglee a la main) est gardee telle quelle.
     */
    public boolean legacyClock()
    {
        return epochTick == 0L && epochDays == 0.0 && timeScale == LEGACY_TIME_SCALE;
    }

    // ---------------------------------------------------------------- reseau
    // Ordre : fyaw, fpitch, vmax, turn, roll, tscale, warp (7 double ; warp ajoute en T15), puis l'epoque (T38) :
    // long etick, double edays. 72 octets.

    public void write(FriendlyByteBuf buf)
    {
        buf.writeDouble(forwardYaw);
        buf.writeDouble(forwardPitch);
        buf.writeDouble(maxSpeed);
        buf.writeDouble(turnRate);
        buf.writeDouble(rollSpeed);
        buf.writeDouble(timeScale);
        buf.writeDouble(warpSpeed);
        buf.writeLong(epochTick);
        buf.writeDouble(epochDays);
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
        double warp = buf.readDouble();
        long tick = buf.readLong();
        double days = buf.readDouble();
        return new SpaceMotionSettings(yaw, pitch, maxSpeed, turn, roll, scale, warp, tick, days);
    }

    // ---------------------------------------------------------------- NBT

    /**
     * Format : {@code { fyaw, fpitch, vmax, turn, roll, tscale, warp, etick, edays }} (cles courtes et stables ;
     * {@code warp} depuis T15 ; epoque {@code etick} (long) / {@code edays} (double) depuis T38, toujours ecrites).
     */
    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("fyaw", forwardYaw);
        tag.putDouble("fpitch", forwardPitch);
        tag.putDouble("vmax", maxSpeed);
        tag.putDouble("turn", turnRate);
        tag.putDouble("roll", rollSpeed);
        tag.putDouble("tscale", timeScale);
        tag.putDouble("warp", warpSpeed);
        tag.putLong("etick", epochTick);
        tag.putDouble("edays", epochDays);
        return tag;
    }

    /**
     * Lecture tolerante : cle manquante ou valeur non finie = valeur par defaut, valeur hors bornes bornee. Une
     * sauvegarde d'avant T15 (sans {@code warp}) prend {@link #DEFAULT_WARP_SPEED} ; d'avant T38 (sans
     * {@code etick} / {@code edays}), l'epoque {@code (0, 0)} (formule d'avant T38, migree par le Space Environment :
     * {@link #legacyClock()}). Sans {@code tscale} : {@link #DEFAULT_TIME_SCALE}, ou {@link #LEGACY_TIME_SCALE} si
     * l'epoque manque aussi (defaut de l'epoque ou la sauvegarde a ete ecrite).
     */
    public static SpaceMotionSettings load(CompoundTag tag)
    {
        boolean epoch = tag.contains("etick", Tag.TAG_ANY_NUMERIC);
        long epochTick = epoch ? tag.getLong("etick") : 0L;
        double scale = number(tag, "tscale", epoch ? DEFAULT_TIME_SCALE : LEGACY_TIME_SCALE);
        return new SpaceMotionSettings(number(tag, "fyaw", DEFAULT_FORWARD_YAW),
                number(tag, "fpitch", DEFAULT_FORWARD_PITCH), number(tag, "vmax", DEFAULT_MAX_SPEED),
                number(tag, "turn", DEFAULT_TURN_RATE), number(tag, "roll", DEFAULT_ROLL_SPEED), scale,
                number(tag, "warp", DEFAULT_WARP_SPEED), epochTick, number(tag, "edays", 0.0));
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
