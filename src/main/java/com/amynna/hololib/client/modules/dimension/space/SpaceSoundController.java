package com.amynna.hololib.client.modules.dimension.space;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.modules.dimension.space.warp.SpaceWarpTimeline;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceJump;
import com.amynna.hololib.modules.dimension.space.math.SpaceMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

/**
 * Sons du vaisseau (T36, D26, C80), entendus par <b>tous les joueurs de la dimension</b> (le monde est le vaisseau) :
 * chaque client les deduit de l'etat affiche par son miroir de l'Environment, rien de plus sur le reseau. Sons non
 * positionnels (relatifs a l'auditeur, sans attenuation), categorie {@link SoundSource#AMBIENT}, evenements crees
 * cote client ({@link SoundEvent#createVariableRangeEvent}, aucun registre), declares dans
 * {@code assets/hololib/sounds.json} sans sous-titre (C15).
 * <ul>
 *     <li><b>Moteurs</b> (retouche T36, esprit « propulseurs doux ») : deux boucles modulees en continu, sans son
 *     ponctuel : un <b>ronronnement</b> grave de reacteur et un <b>sifflement</b> tonal de turbine, discret. Leur
 *     hauteur et leur volume suivent le niveau de vitesse affiche (logarithmique de {@code |V| / V_max}) et une
 *     <b>poussee lissee</b> deduite de l'evolution de {@code |V|} d'un tick a l'autre (positive en acceleration,
 *     negative au frein, au gaz oppose et pendant la charge du saut ; amplifiee en boost, reconnu au rapport des
 *     vitesses : pentes {@link SpaceMotion#BOOST_ACCELERATION} / {@link SpaceMotion#BOOST_DECELERATION}) ; lissage
 *     lent (environ une seconde) : montees et descentes glissees. A l'arret : silence, ou veille tres basse du
 *     ronronnement si un pilote est aux commandes ;</li>
 *     <li><b>Coque</b> : gemissements metalliques sourds et lointains (4 variantes) et, plus rarement, « tonks »
 *     etouffes (3 variantes), tires au hasard au-dela d'un seuil de taux de rotation mesure sur l'orientation affichee
 *     (joystick, roulis, alignement de la charge du saut) ;</li>
 *     <li><b>Saut</b> : pendant la <b>charge</b>, bourdonnement / sifflement en boucle dont hauteur et volume suivent
 *     la progression ({@code jumpChargeProgress}), coupe en fondu si la charge est annulee ; puis, cale sur les phases
 *     visuelles de T35 ({@link SpaceWarpTimeline}, instance separee lue dans le tick) : debut de {@code CONVERGE} =
 *     moteur du saut, debut de {@code WARP} = souffle du flash d'entree, puis grondement de distorsion et effet de
 *     vitesse en boucle avec des gemissements de coque, debut d'{@code ARRIVAL} = flash d'arrivee ; un joueur qui
 *     arrive en cours de phase n'entend que les boucles.</li>
 * </ul>
 * Appele par {@code SpaceEnvironmentModule.clientTick} via le backend ({@link SpaceEnvironmentClient}), jamais en
 * pause (la pause solo suspend aussi les sons en cours) ; arret propre quand le miroir est oublie (retrait du module,
 * changement de dimension, deconnexion : {@link #release}) ou change. Garde-fou : une boucle qui n'est plus pilotee
 * depuis {@value #STALE_TICKS} ticks du moteur de son s'arrete seule. Fil client.
 */
public final class SpaceSoundController
{
    // ---------------------------------------------------------------- evenements (sounds.json)

    private static final SoundEvent ENGINE_HUM = event("space.engine_hum");
    private static final SoundEvent ENGINE_WHINE = event("space.engine_whine");
    private static final SoundEvent JUMP_CHARGE = event("space.jump_charge");
    private static final SoundEvent HULL_GROAN = event("space.hull_groan");
    private static final SoundEvent HULL_TONK = event("space.hull_tonk");
    private static final SoundEvent WARP_ENGINE = event("space.warp_engine");
    private static final SoundEvent WARP_FLASH = event("space.warp_flash");
    private static final SoundEvent WARP_RUMBLE = event("space.warp_rumble");
    private static final SoundEvent WARP_WIND = event("space.warp_wind");
    private static final SoundEvent WARP_ARRIVAL = event("space.warp_arrival");

    // ---------------------------------------------------------------- moteurs : poussee

    /** Vitesse ({@code |V|}, km / tick) sous laquelle les moteurs se taisent s'il n'y a pas de poussee. */
    static final double ENGINE_RUNNING_SPEED = 0.5;
    /** Variation relative de {@code |V|} par tick qui compte comme poussee (sous : vitesse tenue). */
    static final double THRUST_EPSILON = 0.002;
    /**
     * Rapports {@code |V| / |V_prec|} qui distinguent le boost : au-dessus de {@value} en acceleration (1,05 sans
     * boost, 1,1576 avec) ...
     */
    static final double BOOST_ACCEL_RATIO = 1.1;
    /** ... et au-dessous de {@value} en deceleration (0,92 sans boost, 0,7787 avec). */
    static final double BOOST_DECEL_RATIO = 0.85;
    /** Ticks d'affilee d'une nouvelle poussee avant de la prendre (filtre les ecarts d'un tick de la correction). */
    static final int THRUST_CONFIRM_TICKS = 2;
    /** Poussee en boost (la poussee normale vaut 1, en valeur absolue). */
    static final float BOOST_FACTOR = 1.6f;
    /** Lissage par tick de la poussee : elle s'installe et retombe en environ une seconde. */
    static final float THRUST_GLIDE = 0.08f;

    // ---------------------------------------------------------------- moteurs : ronronnement (engine_hum)

    /** Volume en marche : base, gain sur le niveau de vitesse, gain sur la poussee (valeur absolue). */
    static final float HUM_VOLUME_BASE = 0.18f;
    static final float HUM_VOLUME_RANGE = 0.22f;
    static final float HUM_VOLUME_THRUST = 0.12f;
    /** Veille : volume a l'arret quand un pilote est aux commandes (sinon silence). */
    static final float HUM_STANDBY = 0.06f;
    /** Hauteur : base, gain sur le niveau, gain sur la poussee signee. */
    static final float HUM_PITCH_BASE = 0.8f;
    static final float HUM_PITCH_RANGE = 0.3f;
    static final float HUM_PITCH_THRUST = 0.08f;

    // ---------------------------------------------------------------- moteurs : sifflement (engine_whine)

    static final float WHINE_VOLUME_BASE = 0.03f;
    static final float WHINE_VOLUME_RANGE = 0.1f;
    static final float WHINE_VOLUME_THRUST = 0.08f;
    static final float WHINE_PITCH_BASE = 0.6f;
    static final float WHINE_PITCH_RANGE = 0.8f;
    static final float WHINE_PITCH_THRUST = 0.22f;

    /** Lissage par tick du volume et de la hauteur des deux boucles moteur (glissement d'environ une seconde). */
    static final float ENGINE_VOLUME_GLIDE = 0.06f;
    static final float ENGINE_PITCH_GLIDE = 0.05f;

    // ---------------------------------------------------------------- charge du saut (jump_charge)

    /** Volume et hauteur de la boucle de charge, de la progression 0 a 1. */
    static final float CHARGE_VOLUME_MIN = 0.1f;
    static final float CHARGE_VOLUME_MAX = 0.5f;
    static final float CHARGE_PITCH_MIN = 0.55f;
    static final float CHARGE_PITCH_MAX = 1.8f;
    /** Courbe de la hauteur : {@code progression^}{@value}, la tension monte surtout a la fin. */
    static final double CHARGE_PITCH_POWER = 1.3;
    /** Lissage par tick : montee (suit la progression) et fondu de coupure (annulation, depart : ~0,5 s). */
    static final float CHARGE_GLIDE = 0.5f;
    static final float CHARGE_RELEASE_GLIDE = 0.3f;

    // ---------------------------------------------------------------- coque

    /** Taux de rotation (degres par tick, lisse) au-dela duquel la coque travaille, et taux d'intensite pleine. */
    static final double HULL_THRESHOLD = 0.35;
    static final double HULL_FULL = 2.0;
    /** Chance par tick d'un son de coque, de l'intensite nulle a pleine. */
    static final float HULL_CHANCE_MIN = 0.012f;
    static final float HULL_CHANCE_MAX = 0.05f;
    /** Ecart minimal entre deux sons de coque, ticks. */
    static final int HULL_GAP = 30;
    /** Part des « tonks » parmi les sons de coque (le reste : gemissements). */
    static final float TONK_SHARE = 0.25f;
    /** Volume d'un gemissement : base + gain sur l'intensite (plus un peu d'aleatoire) ; volume d'un tonk. */
    static final float GROAN_VOLUME_MIN = 0.1f;
    static final float GROAN_VOLUME_RANGE = 0.16f;
    static final float TONK_VOLUME_MIN = 0.08f;
    static final float TONK_VOLUME_RANGE = 0.1f;
    /** Rotation par tick au-dela de laquelle c'est un saut d'affichage (resynchronisation, arrivee), pas un virage. */
    static final double SNAP_DEGREES = 10.0;
    /** Lissage du taux de rotation (part du nouveau tick). */
    static final double RATE_SMOOTHING = 0.4;

    // ---------------------------------------------------------------- saut

    /** Une phase entamee depuis plus de ticks que cela (joueur arrive en cours) ne rejoue pas son son de debut. */
    static final double LATE_TICKS = 6.0;
    static final float WARP_ENGINE_VOLUME = 0.9f;
    static final float WARP_FLASH_VOLUME = 0.85f;
    static final float WARP_ARRIVAL_VOLUME = 0.9f;
    /** Grondement de distorsion (boucle) : volume en voyage, part pendant la convergence. */
    static final float WARP_RUMBLE_VOLUME = 0.7f;
    static final float WARP_RUMBLE_CONVERGE = 0.4f;
    /** Effet de vitesse (boucle de souffle) : volume en voyage. */
    static final float WARP_WIND_VOLUME = 0.4f;
    /** Lissage par tick des volumes des boucles du saut. */
    static final float WARP_GLIDE = 0.15f;
    /** Coque pendant le voyage : chance par tick, ecart minimal (ticks), volume. */
    static final float WARP_HULL_CHANCE = 0.025f;
    static final int WARP_HULL_GAP = 25;
    static final float WARP_HULL_VOLUME_MIN = 0.18f;
    static final float WARP_HULL_VOLUME_RANGE = 0.12f;

    // ---------------------------------------------------------------- boucles

    /** Volume sous lequel une boucle est arretee (et pas encore lancee). */
    static final float SILENT = 0.004f;
    /** Ticks du moteur de son sans pilotage apres lesquels une boucle s'arrete seule. */
    static final int STALE_TICKS = 4;
    /** Attente avant de relancer une boucle refusee par le moteur de son (volume de categorie nul, canaux pleins). */
    static final int RETRY_TICKS = 20;

    private static final SpaceSoundController INSTANCE = new SpaceSoundController();

    // ---------------------------------------------------------------- etat

    private SpaceEnvironmentModule tracked;
    private final SpaceWarpTimeline timeline = SpaceWarpTimeline.separate();
    private final RandomSource random = RandomSource.create();
    private SpaceWarpTimeline.Phase lastPhase = SpaceWarpTimeline.Phase.NONE;

    private double lastSpeed;
    private boolean hasSpeed;
    private int thrust;
    private boolean boost;
    private int pendingThrust;
    private int pendingTicks;
    /** Poussee lissee, signee ({@code [-BOOST_FACTOR, BOOST_FACTOR]}). */
    private float thrustLevel;

    private final Quaterniond lastOrientation = new Quaterniond();
    private boolean hasOrientation;
    private double rotationRate;
    private int hullCooldown;

    private final Loop hum = new Loop(ENGINE_HUM);
    private final Loop whine = new Loop(ENGINE_WHINE);
    private final Loop charge = new Loop(JUMP_CHARGE);
    private final Loop rumble = new Loop(WARP_RUMBLE);
    private final Loop wind = new Loop(WARP_WIND);

    /** Derniers sons ponctuels lances, coupes avec le reste. */
    private final SoundInstance[] recent = new SoundInstance[8];
    private int recentIndex;

    private SpaceSoundController() {}

    // ---------------------------------------------------------------- entree

    /** Un tick client du miroir {@code environment} (backend, fin de {@code clientTick}). */
    public static void tick(SpaceEnvironmentModule environment) { INSTANCE.update(environment); }

    /** Miroir oublie : s'il est suivi, tous les sons s'arretent. */
    public static void release(SpaceEnvironmentModule environment)
    {
        if (INSTANCE.tracked == environment) INSTANCE.stopAll();
    }

    // ---------------------------------------------------------------- tick

    private void update(SpaceEnvironmentModule env)
    {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        if (env != tracked) {
            stopAll();
            tracked = env;
        }
        if (!env.synced()) {
            // Etat inconnu : tout se tait (en fondu), rien ne demarre.
            hasSpeed = false;
            hasOrientation = false;
            thrust = 0;
            thrustLevel = 0f;
            lastPhase = SpaceWarpTimeline.Phase.NONE;
            hum.drive(sounds, 0f, hum.pitch, ENGINE_VOLUME_GLIDE, 1f);
            whine.drive(sounds, 0f, whine.pitch, ENGINE_VOLUME_GLIDE, 1f);
            charge.drive(sounds, 0f, charge.pitch, CHARGE_RELEASE_GLIDE, 1f);
            rumble.drive(sounds, 0f, rumble.pitch, WARP_GLIDE, 1f);
            wind.drive(sounds, 0f, wind.pitch, WARP_GLIDE, 1f);
            return;
        }
        SpaceWarpTimeline t = timeline.read(env, 0f);
        charge(sounds, env);
        jump(sounds, t);
        engine(sounds, env, t);
        hull(sounds, env, t);
        lastPhase = t.phase();
    }

    /** Charge du saut : boucle qui suit la progression, coupee en fondu hors de la charge. */
    private void charge(SoundManager sounds, SpaceEnvironmentModule env)
    {
        if (env.jumpAt(0f).phase() == SpaceJump.Phase.CHARGE) {
            double p = Math.max(0.0, Math.min(1.0, env.jumpChargeProgress(0f)));
            float volume = CHARGE_VOLUME_MIN + (CHARGE_VOLUME_MAX - CHARGE_VOLUME_MIN) * (float) p;
            float pitch = CHARGE_PITCH_MIN
                    + (CHARGE_PITCH_MAX - CHARGE_PITCH_MIN) * (float) Math.pow(p, CHARGE_PITCH_POWER);
            charge.drive(sounds, volume, pitch, CHARGE_GLIDE, 1f);
        } else {
            charge.drive(sounds, 0f, charge.pitch, CHARGE_RELEASE_GLIDE, 1f);
        }
    }

    /** Sons de debut de phase et boucles du voyage. */
    private void jump(SoundManager sounds, SpaceWarpTimeline t)
    {
        SpaceWarpTimeline.Phase phase = t.phase();
        boolean fresh = t.phaseTicks() < LATE_TICKS;
        if (phase != lastPhase && fresh) {
            switch (phase) {
                case CONVERGE -> play(sounds, WARP_ENGINE, WARP_ENGINE_VOLUME, 1f);
                case WARP -> play(sounds, WARP_FLASH, WARP_FLASH_VOLUME, 1f);
                case ARRIVAL -> play(sounds, WARP_ARRIVAL, WARP_ARRIVAL_VOLUME, 1f);
                default -> {}
            }
        }
        float u = (float) t.phaseProgress();
        float rumbleVolume = 0f, windVolume = 0f;
        float rumblePitch = 0.85f, windPitch = 0.7f;
        if (phase == SpaceWarpTimeline.Phase.CONVERGE) {
            rumbleVolume = WARP_RUMBLE_VOLUME * WARP_RUMBLE_CONVERGE * u;
            windVolume = WARP_WIND_VOLUME * u * u;
            windPitch = 0.7f + 0.3f * u;
        } else if (phase == SpaceWarpTimeline.Phase.WARP) {
            rumbleVolume = WARP_RUMBLE_VOLUME;
            rumblePitch = 0.85f + 0.2f * u;
            windVolume = WARP_WIND_VOLUME * (0.7f + 0.3f * t.sceneRamp());
            windPitch = 1f + 0.35f * u;
        } else {
            rumblePitch = rumble.pitch;
            windPitch = wind.pitch;
        }
        rumble.drive(sounds, rumbleVolume, rumblePitch, WARP_GLIDE, 1f);
        wind.drive(sounds, windVolume, windPitch, WARP_GLIDE, 1f);
    }

    /** Ronronnement et sifflement des moteurs, modules en continu par la vitesse et la poussee lissee. */
    private void engine(SoundManager sounds, SpaceEnvironmentModule env, SpaceWarpTimeline t)
    {
        double v = Math.abs(env.speed());
        if (!Double.isFinite(v)) v = 0.0;
        double maxSpeed = Math.max(SpaceMotion.MIN_SPEED, env.settings().maxSpeed());
        float level = (float) Math.max(0.0, Math.min(1.0, Math.log1p(v) / Math.log1p(maxSpeed)));
        // Poussee : sens de l'evolution de |V| depuis le tick precedent (deduite de l'etat, la meme chez tous).
        int direction = 0;
        boolean boosted = false;
        if (hasSpeed) {
            if (v > lastSpeed * (1.0 + THRUST_EPSILON) && v > 0.0) {
                direction = 1;
                boosted = lastSpeed >= SpaceMotion.MIN_SPEED && v > lastSpeed * BOOST_ACCEL_RATIO;
            } else if (v < lastSpeed * (1.0 - THRUST_EPSILON)) {
                direction = -1;
                boosted = v >= SpaceMotion.MIN_SPEED && v < lastSpeed * BOOST_DECEL_RATIO;
            }
        }
        lastSpeed = v;
        hasSpeed = true;
        if (direction == thrust) {
            pendingTicks = 0;
            if (direction != 0) boost = boosted;
        } else {
            if (direction != pendingThrust) {
                pendingThrust = direction;
                pendingTicks = 0;
            }
            if (++pendingTicks >= THRUST_CONFIRM_TICKS) {
                thrust = direction;
                boost = boosted;
                pendingTicks = 0;
            }
        }
        float thrustTarget = thrust * (boost ? BOOST_FACTOR : 1f);
        thrustLevel += (thrustTarget - thrustLevel) * THRUST_GLIDE;
        float push = Math.abs(thrustLevel);

        boolean running = v >= ENGINE_RUNNING_SPEED || thrust != 0 || push > 0.05f;
        float humVolume = running ? HUM_VOLUME_BASE + HUM_VOLUME_RANGE * level + HUM_VOLUME_THRUST * push
                : env.pilot() != null ? HUM_STANDBY : 0f;
        float humPitch = HUM_PITCH_BASE + HUM_PITCH_RANGE * level + HUM_PITCH_THRUST * thrustLevel;
        float whineVolume = running ? WHINE_VOLUME_BASE + WHINE_VOLUME_RANGE * level + WHINE_VOLUME_THRUST * push : 0f;
        float whinePitch = WHINE_PITCH_BASE + WHINE_PITCH_RANGE * level + WHINE_PITCH_THRUST * thrustLevel;
        float mute = warpMute(t);
        hum.drive(sounds, humVolume, humPitch, ENGINE_VOLUME_GLIDE, ENGINE_PITCH_GLIDE, mute);
        whine.drive(sounds, whineVolume, whinePitch, ENGINE_VOLUME_GLIDE, ENGINE_PITCH_GLIDE, mute);
    }

    /** Facteur des boucles moteur pendant la distorsion : le grondement du voyage les remplace. */
    private static float warpMute(SpaceWarpTimeline t)
    {
        return switch (t.phase()) {
            case CONVERGE -> 1f - (float) t.phaseProgress();
            case WARP -> 0f;
            case ARRIVAL -> (float) t.phaseProgress();
            default -> 1f;
        };
    }

    /** Coque : taux de rotation de l'orientation affichee ; aleatoire pendant le voyage. */
    private void hull(SoundManager sounds, SpaceEnvironmentModule env, SpaceWarpTimeline t)
    {
        Quaterniondc q = env.pose().orientation();
        double angle = 0.0;
        if (hasOrientation) {
            double dot = Math.abs(q.x() * lastOrientation.x + q.y() * lastOrientation.y + q.z() * lastOrientation.z
                    + q.w() * lastOrientation.w);
            angle = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, dot)));
            if (!(angle <= SNAP_DEGREES)) angle = 0.0;
        }
        lastOrientation.set(q);
        hasOrientation = true;
        rotationRate += (angle - rotationRate) * RATE_SMOOTHING;
        if (hullCooldown > 0) {
            hullCooldown--;
            return;
        }
        if (t.phase() == SpaceWarpTimeline.Phase.WARP) {
            if (random.nextFloat() < WARP_HULL_CHANCE) {
                hullSound(sounds, WARP_HULL_VOLUME_MIN + WARP_HULL_VOLUME_RANGE * random.nextFloat(), 0.8f, 0.15f);
                hullCooldown = WARP_HULL_GAP;
            }
            return;
        }
        if (rotationRate <= HULL_THRESHOLD) return;
        float intensity = (float) Math.min(1.0, (rotationRate - HULL_THRESHOLD) / (HULL_FULL - HULL_THRESHOLD));
        if (random.nextFloat() < HULL_CHANCE_MIN + (HULL_CHANCE_MAX - HULL_CHANCE_MIN) * intensity) {
            hullSound(sounds, GROAN_VOLUME_MIN + GROAN_VOLUME_RANGE * intensity * (0.6f + 0.4f * random.nextFloat()),
                    0.85f, 0.25f);
            hullCooldown = HULL_GAP;
        }
    }

    /**
     * Un son de coque : tonk ({@link #TONK_SHARE}, volume propre) ou gemissement de volume {@code groanVolume} ;
     * hauteur tiree dans {@code [pitch, pitch + spread]}.
     */
    private void hullSound(SoundManager sounds, float groanVolume, float pitch, float spread)
    {
        float p = pitch + spread * random.nextFloat();
        if (random.nextFloat() < TONK_SHARE) {
            play(sounds, HULL_TONK, TONK_VOLUME_MIN + TONK_VOLUME_RANGE * random.nextFloat(), p);
        } else {
            play(sounds, HULL_GROAN, groanVolume, p);
        }
    }

    // ---------------------------------------------------------------- lecture

    private void play(SoundManager sounds, SoundEvent event, float volume, float pitch)
    {
        SoundInstance sound = new SimpleSoundInstance(event.getLocation(), SoundSource.AMBIENT, volume, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        sounds.play(sound);
        recent[recentIndex] = sound;
        recentIndex = (recentIndex + 1) % recent.length;
    }

    /** Arrete tout (boucles et derniers sons ponctuels) et oublie l'etat suivi. */
    private void stopAll()
    {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        hum.stop(sounds);
        whine.stop(sounds);
        charge.stop(sounds);
        rumble.stop(sounds);
        wind.stop(sounds);
        for (int i = 0; i < recent.length; i++) {
            if (recent[i] != null) sounds.stop(recent[i]);
            recent[i] = null;
        }
        tracked = null;
        lastPhase = SpaceWarpTimeline.Phase.NONE;
        hasSpeed = false;
        thrust = 0;
        boost = false;
        pendingThrust = 0;
        pendingTicks = 0;
        thrustLevel = 0f;
        hasOrientation = false;
        rotationRate = 0.0;
        hullCooldown = 0;
    }

    private static SoundEvent event(String path)
    {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, path));
    }

    // ---------------------------------------------------------------- boucles

    /**
     * Une boucle pilotee : volume et hauteur lisses par tick ; l'instance de son est creee au premier volume audible,
     * arretee sous {@link #SILENT}, relancee si le moteur de son l'a abandonnee.
     */
    private static final class Loop
    {
        private final SoundEvent event;
        private LoopSound sound;
        private float volume;
        private float pitch = 1f;
        private int retry;

        Loop(SoundEvent event) { this.event = event; }

        void drive(SoundManager sounds, float targetVolume, float targetPitch, float glide, float mute)
        {
            drive(sounds, targetVolume, targetPitch, glide, glide, mute);
        }

        void drive(SoundManager sounds, float targetVolume, float targetPitch, float volumeGlide, float pitchGlide,
                   float mute)
        {
            // Boucle muette : la hauteur part directement de sa cible (pas de glissement audible au demarrage).
            if (sound == null && volume < SILENT) pitch = targetPitch;
            volume += (targetVolume * mute - volume) * volumeGlide;
            pitch += (targetPitch - pitch) * pitchGlide;
            if (retry > 0) retry--;
            if (sound != null && !sounds.isActive(sound)) {
                // Abandonnee par le moteur de son (volume de categorie nul, canaux pleins) : relance plus tard.
                sound.discard();
                sound = null;
                retry = RETRY_TICKS;
            }
            if (volume < SILENT) {
                if (sound != null) stop(sounds);
                if (targetVolume * mute < SILENT) volume = 0f;
                return;
            }
            if (sound == null) {
                if (retry > 0) return;
                sound = new LoopSound(event, volume, pitch);
                sounds.play(sound);
                if (!sounds.isActive(sound)) {
                    sound.discard();
                    sound = null;
                    retry = RETRY_TICKS;
                }
                return;
            }
            sound.set(volume, pitch);
        }

        void stop(SoundManager sounds)
        {
            if (sound != null) {
                sound.discard();
                sounds.stop(sound);
                sound = null;
            }
            volume = 0f;
            retry = 0;
        }
    }

    /** Instance de boucle non positionnelle ; s'arrete seule si elle n'est plus pilotee. */
    private static final class LoopSound extends AbstractTickableSoundInstance
    {
        private int stale;

        LoopSound(SoundEvent event, float volume, float pitch)
        {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
            this.volume = volume;
            this.pitch = pitch;
        }

        void set(float volume, float pitch)
        {
            this.volume = volume;
            this.pitch = pitch;
            stale = 0;
        }

        void discard() { stop(); }

        @Override
        public void tick()
        {
            if (++stale > STALE_TICKS) stop();
        }

        @Override
        public boolean canStartSilent() { return true; }
    }
}
