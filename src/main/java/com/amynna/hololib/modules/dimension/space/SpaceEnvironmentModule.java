package com.amynna.hololib.modules.dimension.space;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.dimension.DimensionHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceFrameState;
import com.amynna.hololib.modules.dimension.space.math.SpaceMotion;
import com.amynna.hololib.modules.dimension.space.math.SpaceMotionSettings;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.math.SpacePilotInput;
import com.amynna.hololib.modules.dimension.space.math.SpacePose;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceEditResult;
import com.amynna.hololib.modules.dimension.space.model.SpaceGalaxy;
import com.amynna.hololib.modules.dimension.space.model.SpaceGalaxy.BackgroundStar;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Module {@code hololib:space_environment} (memoire de dimension) : porte la galaxie de la dimension, le systeme
 * courant et la pose du monde dans ce systeme, simule le mouvement des deux cotes et expose l'API serveur du
 * Controller et du Configurator. Aucun objet holographique : le ciel est dessine par un {@code SkyRenderer} (T05).
 * Conception : {@code docs/space/02-architecture.md} par. 5, {@code 05-synchronisation.md}.
 * <p>
 * <b>Temps</b> : l'etat porte l'etiquette {@code stateTime} (tick de jeu de la pose). Un pas de
 * {@link SpaceMotion#step} mene de {@code t} a {@code t + 1} et recoit le temps de <b>fin</b> du pas,
 * {@code settings.days(t + 1, 0)}, des deux cotes. Serveur : un pas par tick jusqu'a {@code level.getGameTime()}.
 * <p>
 * <b>Referentiel</b> ({@code docs/space/04-mathematiques.md} par. 5 point 0, D7, D8) : la vitesse est relative au corps
 * de reference {@link #referenceBodyId()}, choisi par un score proximite x regard (visee du pilote, ou axe avant), avec
 * delai, cooldown et fondu du mouvement d'entrainement ({@link SpaceMotion}). Etat simule comme la pose
 * ({@link SpaceFrameState}) : chaque suite de pas a le sien (serveur, rejeu client, prediction du pilote), mis a jour
 * par {@link SpaceMotion#step} ; synchronise, sauvegarde, remis a jour par la pose par defaut ; un corps de reference
 * retire est remplace aussitot par le meilleur score (serveur, {@link SpaceMotion#refreshReference}). Une bascule
 * cote serveur demande un envoi.
 * <p>
 * <b>Synchronisation</b> ({@link #writeSync}) : bloc chaud puis bloc froid.
 * <pre>
 * chaud : long T0 (stateTime, -1 avant le premier tick), pose (7 double : x y z qx qy qz qw), double V (signee,
 *         relative au referentiel), etat du referentiel (5 varints : id du corps de reference (0 = aucun : recalcule
 *         au pas suivant), id du precedent, ticks depuis la bascule, id du candidat, ticks de candidature), entree du
 *         pilote (7 octets, frein et visee compris), boolean pilote present [+ UUID], reglages (6 double)
 * froid : varint L (octets qui suivent), int revision, systeme courant complet, etoiles de fond (varint + n x 20 o)
 * </pre>
 * Le client saute le bloc froid ({@code L - 4} octets) si la revision est celle qu'il connait. Le bloc froid est mis
 * en cache cote serveur et reconstruit quand la revision change.
 * <p>
 * <b>Politique d'envoi</b> (serveur, decidee dans {@link #serverTick}) : un envoi demande (edition qui change le bloc
 * froid, systeme courant, reglage, nouvelle entree du pilote, pilote pris ou rendu, changement de referentiel) part
 * au plus une fois tous les
 * {@value #MIN_SEND_INTERVAL} ticks ; monde en mouvement : correction tous les {@value #CORRECTION_INTERVAL} ticks, et
 * un envoi a l'arret ; une edition qui ne change pas le bloc froid n'est envoyee qu'au plus tous les
 * {@value #CORRECTION_INTERVAL} ticks (seul moyen de la faire sauvegarder : le framework sauve sur {@code markDirty}).
 * <p>
 * <b>Client</b> (T07e, {@code docs/space/taches/T07e-saccades.md}) : dead reckoning (rejeu depuis {@code T0}, puis
 * un pas par tick) ; chaque tick simule laisse sa pose d'affichage dans un <b>historique</b> ({@value #HISTORY} ticks)
 * et son etat simule dans un second. L'affichage suit une <b>horloge propre</b> ({@link #displayDays(float)}) : un tick
 * par tick client, qui rattrape sa consigne (temps du niveau moins un tick) en changeant sa vitesse (au plus 25 %) au
 * lieu de sauter quand le serveur recale le temps du niveau (toutes les 20 ticks) ; un observateur (pas le pilote
 * local) affiche en plus un leger retard (retard des etats recus + {@value #MIN_SEND_INTERVAL} ticks), si bien qu'un
 * etat recu corrige des ticks pas encore affiches : le tick exact du changement d'entree est retrouve
 * ({@link #findChange}, pas deterministe) et la trajectoire exacte reecrite ; a defaut, glissement lineaire dans
 * l'historique, ou correction douce sur {@value #SOFT_TICKS} ticks si l'etat arrive trop tard ; saut au-dela de 10
 * degres (orientation, ou deplacement apparent du corps le plus proche). Pilote local : prediction avec sa propre
 * entree ({@link #predictPilotInput}) et reconciliation lente (10 % de l'ecart par tick).
 * {@link #pose(float, SpacePose)} et {@link #bodyPositions} sont au meme temps ; la pose est interpolee entre deux
 * entrees de l'historique <b>dans le repere du referentiel</b> ({@link SpaceMotion#interpolate}, fondu compris).
 * <p>
 * Le client passe par un {@link Backend} installe au setup client ({@code client.modules.dimension.space}).
 */
public final class SpaceEnvironmentModule extends HoloModule
{
    /** Intervalle minimal entre deux envois demandes (entree du pilote, edition, reglage), en ticks. */
    public static final int MIN_SEND_INTERVAL = 4;
    /** Intervalle des corrections quand le monde bouge, et des envois d'editions hors bloc froid, en ticks. */
    public static final int CORRECTION_INTERVAL = 20;
    /** Silence du pilote apres lequel son entree est remise a zero, en ticks. */
    public static final int INPUT_TIMEOUT = 60;
    /** Silence du pilote apres lequel il est libere, en ticks. */
    public static final int PILOT_TIMEOUT = 200;
    /** Nombre maximal de pas rejoues (client) ou rattrapes (serveur) d'un coup ; au-dela, l'etat est pris tel quel. */
    public static final int MAX_REPLAY = 40;
    /** Duree de la correction douce, en ticks. */
    public static final int SOFT_TICKS = 5;
    /** Ecart angulaire au-dela duquel la correction est un saut, en radians. */
    private static final double SOFT_MAX_ANGLE = Math.toRadians(10.0);
    /** Part de l'ecart rattrapee a chaque tick par la prediction du pilote local. */
    private static final double RECONCILE = 0.1;
    /** Poses d'affichage gardees, une par tick simule (puissance de 2, au moins le retard maximal + 4). */
    private static final int HISTORY = 32;
    /** Horloge d'affichage : part de son ecart a la consigne rattrapee par tick. */
    private static final double CLOCK_GAIN = 0.1;
    /** Horloge d'affichage : ecart maximal de sa vitesse a 1 tick par tick (rattrapage sans saut). */
    private static final double CLOCK_STRETCH = 0.25;
    /** Horloge d'affichage : ecart a la consigne (ticks) tolere sans correction (battement des recalages). */
    private static final double CLOCK_DEADBAND = 1.0;
    /** Horloge d'affichage : ecart a la consigne (ticks) au-dela duquel elle saute. */
    private static final double CLOCK_SNAP = 20.0;
    /** Retard d'affichage maximal d'un observateur, en ticks. */
    private static final double MAX_OBSERVER_DELAY = 16.0;
    /** Oubli du retard constate des etats recus, en ticks par etat recu. */
    private static final double LAG_DECAY = 0.05;

    private static Backend backend;

    // ---------------------------------------------------------------- etat commun (serveur autorite, client miroir)

    /** Galaxie complete : serveur seulement (vide cote client). */
    private SpaceGalaxy galaxy = new SpaceGalaxy();
    private int currentId;
    /** Systeme courant (serveur : {@code galaxy.system(currentId)} ; client : copie recue). */
    private SpaceSystem current;
    /** Etoiles de fond (client : recues ; serveur : calculees avec le cache du bloc froid). */
    private List<BackgroundStar> stars = List.of();
    private int revision;
    private SpaceMotionSettings settings = SpaceMotionSettings.DEFAULT;
    /** Pose simulee au tick {@link #stateTime}. */
    private final SpacePose pose = new SpacePose();
    /** Vitesse nominale signee, km par tick ({@code V < 0} : marche arriere ; bornee par chaque pas). */
    private double speed;
    /** Referentiel de {@link #pose} (reference {@code 0} = aucun : le meilleur score au pas suivant). */
    private final SpaceFrameState frame = new SpaceFrameState();
    private long stateTime = -1;
    private SpacePilotInput input = SpacePilotInput.ZERO;
    private UUID pilot;
    /** Une instance par module : le module serveur et son miroir client tournent sur des fils differents. */
    private final SpaceMotion motion = new SpaceMotion();

    // ---------------------------------------------------------------- serveur

    private boolean poseUnset;
    /** Tick de la pose chargee (cle {@code time}), {@code -1} si inconnu ; consomme au premier tick serveur. */
    private long loadedTime = -1;
    private String pilotMarker;
    private long lastInputTime;
    private long lastSendTime = Long.MIN_VALUE / 2;
    private boolean sendPending;
    private boolean persistPending;
    private boolean wasMoving;
    private int nearestIndex = -1;
    private double nearestAltitude = Double.POSITIVE_INFINITY;
    private double referenceAltitude = Double.POSITIVE_INFINITY;
    private byte[] coldBytes;
    private int coldRevision;

    // ---------------------------------------------------------------- client

    private boolean synced;
    private boolean knowsCold;
    private SpacePilotInput localInput = SpacePilotInput.ZERO;
    private boolean predicting;
    private final SpacePose predicted = new SpacePose();
    private double predictedSpeed;
    private final SpaceFrameState predictedFrame = new SpaceFrameState();
    /** Pose affichee au tick {@link #stateTime} (derniere entree de l'historique). */
    private final SpacePose display = new SpacePose();
    private double displaySpeed;
    /** Referentiel de la pose affichee, apres le pas qui a mene a {@link #display}. */
    private final SpaceFrameState displayFrame = new SpaceFrameState();
    /**
     * Historique d'affichage : pose affichee et referentiel (apres le pas qui y mene) du tick {@code t} a l'indice
     * {@code t & (HISTORY - 1)}, valables de {@link #historyFirst} a {@link #stateTime}.
     */
    private final SpacePose[] historyPose = newPoses(HISTORY);
    private final SpaceFrameState[] historyFrame = newFrames(HISTORY);
    private long historyFirst;
    /** Temps d'affichage (ticks) au debut du tick client courant, et sa vitesse (ticks par tick client). */
    private double clockStart;
    private double clockRate = 1.0;
    /** Retard constate des etats recus (temps du niveau a la reception moins {@code T0}), maximum oublie lentement. */
    private double lagEstimate;
    /**
     * Historique de la simulation (pose, vitesse, referentiel du tick {@code t} a l'indice {@code t & (HISTORY - 1)},
     * valables de {@link #simFirst} a {@link #stateTime}) : retrouve le tick exact d'un changement d'entree
     * ({@link #findChange}).
     */
    private final SpacePose[] simPose = newPoses(HISTORY);
    private final SpaceFrameState[] simFrame = newFrames(HISTORY);
    private final double[] simSpeed = new double[HISTORY];
    private long simFirst;
    private final SpacePose searchPose = new SpacePose();
    private final SpaceFrameState searchFrame = new SpaceFrameState();
    /** Positions des corps pour remplir l'historique d'un saut. */
    private final Vector3d[] historyBodies = SpaceOrbits.newPositionArray();
    /** Instance du rendu ({@link #pose(float, SpacePose)}) : n'ecrase pas l'espace de travail de la simulation. */
    private final SpaceMotion displayMotion = new SpaceMotion();
    private final SpaceFrameState receivedFrame = new SpaceFrameState();
    private final Vector3d correctionPosition = new Vector3d();
    private final Quaterniond correctionRotation = new Quaterniond();
    private final Quaterniond rotationWork = new Quaterniond();
    private int correctionTicks;
    private final SpacePose received = new SpacePose();

    // ================================================================ acces

    /** Pont vers le client, installe au setup client. */
    public interface Backend
    {
        /** Miroir du module dans la dimension du joueur local, ou {@code null}. */
        SpaceEnvironmentModule clientInstance();

        /** Temps de jeu du niveau client ({@code level.getGameTime()}), 0 sans niveau. */
        long gameTime();

        /** UUID du joueur local, ou {@code null}. */
        UUID localPlayer();
    }

    /** Branche le client (setup client). */
    public static void install(Backend backend) { SpaceEnvironmentModule.backend = Objects.requireNonNull(backend); }

    /** Miroir client de la dimension du joueur local ; {@code null} sans backend (serveur dedie) ou sans module. */
    public static SpaceEnvironmentModule client() { return backend == null ? null : backend.clientInstance(); }

    /**
     * Module de la dimension {@code level} (fil serveur), ou {@code null} : memoire de dimension pas encore ouverte ou
     * module non installe.
     */
    public static SpaceEnvironmentModule of(ServerLevel level)
    {
        DimensionHoloContainer container = HoloContainers.dimension(level);
        if (container == null) return null;
        for (HoloModule module : container.modules()) {
            if (module instanceof SpaceEnvironmentModule environment) return environment;
        }
        return null;
    }

    // ================================================================ cycle de vie

    /** Le module n'a aucun objet : sans cela, aucun client ne le recevrait. */
    @Override
    public boolean sharedWithObservers() { return true; }

    /**
     * Aucun objet. Serveur : galaxie vide (premiere installation ou sauvegarde illisible) -> galaxie par defaut ;
     * systeme courant perdu -> premier systeme ; pose provisoire (calculee au temps 0, recalculee au premier tick).
     */
    @Override
    public void onLoad()
    {
        if (host().clientSide()) return;
        if (galaxy.isEmpty()) {
            galaxy = SpaceGalaxy.createDefault();
            currentId = galaxy.systems().get(0).id();
            poseUnset = true;
            revision++;
        } else if (!galaxy.contains(currentId)) {
            HoloLib.LOGGER.warn("Space : systeme courant {} absent, repli sur le premier systeme", currentId);
            currentId = galaxy.systems().get(0).id();
            poseUnset = true;
            revision++;
        }
        current = galaxy.system(currentId);
        if (poseUnset) {
            motion.defaultPose(current, settings.days(0L, 0f), settings, pose);
            speed = 0.0;
            frame.reset(motion.referenceBodyId());
        }
    }

    // ================================================================ sauvegarde

    /**
     * Cles : {@code galaxy, current, pose, time} (tick de la pose), {@code speed, frame} (etat du referentiel,
     * {@link SpaceFrameState#save}), {@code settings, rev}. Le pilote n'est pas sauvegarde.
     */
    @Override
    public void save(CompoundTag tag)
    {
        tag.put("galaxy", galaxy.save());
        tag.putInt("current", currentId);
        tag.put("pose", pose.save());
        tag.putLong("time", stateTime);
        tag.putDouble("speed", speed);
        tag.put("frame", frame.save());
        tag.put("settings", settings.save());
        tag.putInt("rev", revision);
    }

    /** Lecture tolerante ; {@link #onLoad} complete (galaxie par defaut, repli du systeme courant). */
    @Override
    public void load(CompoundTag tag)
    {
        galaxy = SpaceGalaxy.load(tag.getCompound("galaxy"));
        currentId = tag.getInt("current");
        if (tag.contains("pose", Tag.TAG_COMPOUND)) {
            pose.load(tag.getCompound("pose"));
            // Sauvegarde d'avant T13 : pas de cle "time", pas de recalage au premier tick.
            loadedTime = tag.contains("time", Tag.TAG_LONG) ? tag.getLong("time") : -1L;
        } else {
            poseUnset = true;
        }
        double loaded = tag.getDouble("speed");
        speed = Double.isFinite(loaded) ? loaded : 0.0;
        // Etat du referentiel ; sauvegarde d'avant T07d : seule la cle "ref" ; absent ou invalide : 0, le meilleur
        // score au premier pas.
        if (tag.contains("frame", Tag.TAG_COMPOUND)) frame.load(tag.getCompound("frame"));
        else frame.reset(Math.max(0, tag.getInt("ref")));
        try {
            settings = SpaceMotionSettings.load(tag.getCompound("settings"));
        } catch (RuntimeException e) {
            HoloLib.LOGGER.warn("Space : reglages illisibles, valeurs par defaut ({})", e.getMessage());
            settings = SpaceMotionSettings.DEFAULT;
        }
        revision = tag.getInt("rev");
    }

    // ================================================================ synchronisation

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        // Chaud.
        buf.writeLong(stateTime);
        pose.write(buf);
        buf.writeDouble(speed);
        frame.write(buf);
        input.write(buf);
        buf.writeBoolean(pilot != null);
        if (pilot != null) buf.writeUUID(pilot);
        settings.write(buf);
        // Froid (en cache).
        if (coldBytes == null || coldRevision != revision) rebuildCold();
        buf.writeVarInt(coldBytes.length);
        buf.writeBytes(coldBytes);
    }

    /** Bloc froid : revision, systeme courant, etoiles de fond. */
    private void rebuildCold()
    {
        stars = galaxy.backgroundStars(currentId);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeInt(revision);
            current.write(buf);
            BackgroundStar.writeList(buf, stars);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            coldBytes = bytes;
            coldRevision = revision;
        } finally {
            buf.release();
        }
    }

    /**
     * Lecture stricte rattrapee : un bloc chaud invalide laisse l'etat inchange (log) ; un bloc froid invalide garde
     * le systeme connu (log) et sera relu au prochain envoi.
     */
    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        try {
            long t0 = buf.readLong();
            received.read(buf);
            double v = buf.readDouble();
            if (!Double.isFinite(v)) throw new IllegalArgumentException("Vitesse invalide : " + v);
            receivedFrame.read(buf);
            SpacePilotInput in = SpacePilotInput.read(buf);
            UUID newPilot = buf.readBoolean() ? buf.readUUID() : null;
            SpaceMotionSettings newSettings = SpaceMotionSettings.read(buf);
            int length = buf.readVarInt();
            if (length < 4 || length > buf.readableBytes()) {
                throw new IllegalArgumentException("Bloc froid de longueur invalide : " + length);
            }
            int start = buf.readerIndex();
            int newRevision = buf.readInt();
            boolean systemChanged = false;
            if (!knowsCold || newRevision != revision) {
                try {
                    SpaceSystem system = SpaceSystem.read(buf);
                    List<BackgroundStar> list = BackgroundStar.readList(buf);
                    systemChanged = current == null || current.id() != system.id();
                    current = system;
                    currentId = system.id();
                    stars = list;
                    revision = newRevision;
                    knowsCold = true;
                } catch (RuntimeException e) {
                    HoloLib.LOGGER.warn("Space : bloc froid invalide (revision {}), ignore", newRevision, e);
                }
            }
            buf.readerIndex(start + length);
            // Aucun systeme connu (premier bloc froid invalide) : rien a simuler, on attend le prochain envoi.
            if (current == null) return;
            applyState(t0, v, in, newPilot, newSettings, systemChanged);
        } catch (RuntimeException e) {
            HoloLib.LOGGER.warn("Space : etat de l'environnement invalide, ignore", e);
        }
    }

    /**
     * Client : nouvel etat serveur (pose dans {@link #received}, referentiel dans {@link #receivedFrame}) au tick
     * {@code t0}. L'etat est rejoue jusqu'au dernier tick de l'historique, puis l'historique d'affichage est corrige
     * ({@link #correct}) ; premier etat, changement de systeme ou etat trop eloigne : saut.
     */
    private void applyState(long t0, double v, SpacePilotInput in, UUID newPilot, SpaceMotionSettings newSettings,
                            boolean systemChanged)
    {
        boolean jump = !synced || systemChanged || t0 < 0;
        // Etat en avance sur la simulation (il arrive avant le tick client qui amene le niveau a T0) : l'ancienne
        // trajectoire est d'abord prolongee jusqu'a T0 (anciennes entrees), pour comparer au meme tick.
        if (!jump && t0 > stateTime) {
            if (t0 - stateTime > HISTORY / 2) jump = true;
            else advance(t0);
        }
        if (t0 >= 0) {
            long lag = clientTime() - t0;
            lagEstimate = synced ? Math.max(lag, lagEstimate - LAG_DECAY) : lag;
        }
        settings = newSettings;
        pilot = newPilot;
        input = in;
        pose.set(received);
        speed = v;
        frame.set(receivedFrame);
        // Rejeu jusqu'au dernier tick de l'historique ; au premier etat, jusqu'au niveau.
        long last = jump ? Math.max(clientTime(), t0) : stateTime;
        if (t0 < 0 || last - t0 > MAX_REPLAY) {
            stateTime = Math.max(last, 0L);
            jump = true;
        } else {
            stateTime = t0;
        }
        if (jump) {
            replay(last);
            if (!synced) clockStart = clientTime() - 1.0 - observerDelay();
            synced = true;
            snapDisplay();
            return;
        }
        if (predicting) {
            // Pilote local : la prediction rejoint l'etat rejoue par reconciliation lente ({@link #advance}).
            replay(last);
            return;
        }
        correct(last);
    }

    /**
     * Client (pas de prediction) : l'etat serveur, pose au tick {@link #stateTime} {@code = T0}, remplace la
     * trajectoire de l'historique jusqu'a {@code last} sans toucher aux ticks deja affiches (jusqu'a la fin de
     * l'intervalle du tick client courant, {@code kept}).
     * <ul>
     *     <li>{@code T0 > kept} (cas courant chez un observateur, grace au retard d'affichage) : si l'ancienne
     *     simulation donnait deja l'etat recu a {@code T0}, rien a corriger ; sinon le tick du changement d'entree est
     *     cherche ({@link #findChange}) et la trajectoire exacte reecrite depuis ce tick ; a defaut, les poses des ticks
     *     {@code ]kept, T0[} glissent lineairement de l'ancienne trajectoire vers l'etat recu. La suite est l'etat
     *     rejoue exact : aucun ecart ne reste a absorber ;</li>
     *     <li>sinon : rejeu jusqu'a {@code kept}, puis l'ecart a ce tick est absorbe sur {@value #SOFT_TICKS} ticks
     *     (correction douce).</li>
     * </ul>
     * Ecart trop grand ({@link #tooFar}) : saut.
     */
    private void correct(long last)
    {
        long kept = Math.min(last, Math.max(historyFirst, (long) Math.ceil(clockStart + clockRate)));
        long t0 = stateTime;
        if (t0 > kept) {
            SpacePose old = historyPose[index(t0)];
            if (tooFar(old)) {
                replay(last);
                snapDisplay();
                return;
            }
            long change = sameAsState(t0) ? t0 : findChange(t0, Math.max(kept, last - HISTORY + 1));
            if (change >= 0) {
                // Tick du changement retrouve : trajectoire exacte reecrite de ce tick a T0.
                searchPose.set(simPose[index(change)]);
                searchFrame.set(simFrame[index(change)]);
                double v = simSpeed[index(change)];
                SpacePilotInput stepInput = stepInput();
                for (long t = change; t < t0 - 1; t++) {
                    v = motion.step(searchPose, v, searchFrame, stepInput, current, settings.days(t, 0f),
                            settings.days(t + 1, 0f), settings, searchPose);
                    int i = index(t + 1);
                    historyPose[i].set(searchPose);
                    historyFrame[i].set(searchFrame);
                    simPose[i].set(searchPose);
                    simFrame[i].set(searchFrame);
                    simSpeed[i] = v;
                }
            } else {
                // Sinon (plusieurs changements, etat non retrouve) : glissement lineaire de kept a T0.
                old.position().negate(correctionPosition).add(pose.position());
                pose.orientation().mul(rotationWork.set(old.orientation()).conjugate(), correctionRotation);
                for (long t = kept + 1; t < t0; t++) {
                    double w = (t - kept) / (double) (t0 - kept);
                    SpacePose entry = historyPose[index(t)];
                    entry.position().fma(w, correctionPosition);
                    rotationWork.identity().slerp(correctionRotation, w).mul(entry.orientation(),
                            entry.orientation());
                    entry.orientation().normalize();
                }
            }
            correctionTicks = 0;
            recordSim();
            store(pose, speed, frame);
            advance(last);
            return;
        }
        replay(kept);
        SpacePose old = historyPose[index(kept)];
        if (tooFar(old)) {
            replay(last);
            snapDisplay();
            return;
        }
        beginCorrection(old);
        advance(last);
    }

    /**
     * Client : tick {@code m} dans {@code [lowest, t0[} tel que l'ancienne simulation au tick {@code m}, rejouee
     * jusqu'a {@code T0} avec l'entree recue, redonne <b>exactement</b> l'etat recu ({@link #pose}, {@link #speed},
     * {@link #frame}) : l'entree a change a ce tick (le pas est deterministe bit a bit). Du plus recent au plus
     * ancien ; {@code -1} si aucun (plusieurs changements, ou ancienne simulation deja fausse).
     */
    private long findChange(long t0, long lowest)
    {
        SpacePilotInput stepInput = stepInput();
        for (long m = t0 - 1; m >= Math.max(lowest, simFirst); m--) {
            searchPose.set(simPose[index(m)]);
            searchFrame.set(simFrame[index(m)]);
            double v = simSpeed[index(m)];
            for (long t = m; t < t0; t++) {
                v = motion.step(searchPose, v, searchFrame, stepInput, current, settings.days(t, 0f),
                        settings.days(t + 1, 0f), settings, searchPose);
            }
            if (sameAsState(searchPose, v, searchFrame)) return m;
        }
        return -1;
    }

    /** Vrai si l'etat de l'historique de la simulation au tick {@code t} est exactement l'etat simule courant. */
    private boolean sameAsState(long t)
    {
        return t >= simFirst && sameAsState(simPose[index(t)], simSpeed[index(t)], simFrame[index(t)]);
    }

    /** Vrai si {@code (p, v, f)} est exactement (bit a bit) l'etat simule courant ({@link #pose}, {@link #speed}). */
    private boolean sameAsState(SpacePose p, double v, SpaceFrameState f)
    {
        return v == speed && f.sameAs(frame) && p.position().equals(pose.position())
                && p.orientation().equals(pose.orientation());
    }

    /** Client : rejeu de l'etat simule seul (sans historique d'affichage) jusqu'au tick {@code until}. */
    private void replay(long until)
    {
        SpacePilotInput stepInput = stepInput();
        while (stateTime < until) {
            speed = motion.step(pose, speed, frame, stepInput, current, settings.days(stateTime, 0f),
                    settings.days(stateTime + 1, 0f), settings, pose);
            stateTime++;
            recordSim();
        }
    }

    /** Client : etat simule du tick {@link #stateTime} dans l'historique de la simulation. */
    private void recordSim()
    {
        int i = index(stateTime);
        simPose[i].set(pose);
        simFrame[i].set(frame);
        simSpeed[i] = speed;
    }

    /**
     * Client : affichage, prediction et correction ramenes sur l'etat simule ; historique rempli d'un monde immobile
     * dans le repere du referentiel (pas de saut du referentiel quand l'horloge d'affichage est en retard).
     */
    private void snapDisplay()
    {
        predicted.set(pose);
        predictedSpeed = speed;
        predictedFrame.set(frame);
        correctionTicks = 0;
        int ref = current == null ? -1 : current.indexOf(frame.reference());
        boolean moving = ref >= 0 && !current.bodies().get(ref).isRoot();
        double rx = 0.0, ry = 0.0, rz = 0.0;
        if (moving) {
            SpaceOrbits.positions(current, settings.days(stateTime, 0f), historyBodies);
            rx = historyBodies[ref].x;
            ry = historyBodies[ref].y;
            rz = historyBodies[ref].z;
        }
        for (long t = stateTime - HISTORY + 1; t < stateTime; t++) {
            SpacePose entry = historyPose[index(t)];
            entry.set(pose);
            historyFrame[index(t)].set(frame);
            if (moving) {
                SpaceOrbits.positions(current, settings.days(t, 0f), historyBodies);
                entry.position().add(historyBodies[ref].x - rx, historyBodies[ref].y - ry, historyBodies[ref].z - rz);
            }
        }
        store(pose, speed, frame);
        recordSim();
        simFirst = stateTime;
        historyFirst = stateTime - HISTORY + 1;
        clockStart = Math.max(historyFirst, Math.min(clockStart, stateTime - 1.0));
    }

    /**
     * Client : vrai si la pose affichee {@code shown} est trop loin de l'etat simule (meme tick) pour une correction
     * douce : orientation a plus de 10 degres, ou ecart de position qui deplace le corps le plus proche de plus de
     * 10 degres vu depuis le monde. Ecart tolere {@code min(tan(10 deg) d / max(1, tan alpha), h / 2)} ({@code d} =
     * distance au centre du corps, {@code h} = altitude, {@code alpha} = rayon angulaire) : loin, c'est le
     * deplacement de son centre ; pres (jusqu'au plancher {@code 0,01 R}, D9), l'horizon bouge de
     * {@code tan alpha / d} radian par km radial, et la correction douce ne doit pas faire passer l'affichage sous la
     * surface (Terre au plancher : 32 km au lieu de 1 100 avant D9).
     */
    private boolean tooFar(SpacePose shown)
    {
        if (!shown.isFinite()) return true;
        int index = motion.nearestBody(pose, current, settings.days(stateTime, 0f));
        double tolerated = Double.POSITIVE_INFINITY;
        if (index >= 0) {
            double radius = current.bodies().get(index).radius();
            double h = Math.max(0.0, motion.nearestAltitude());
            // tan alpha = R / sqrt(d^2 - R^2) = R / sqrt(h (2R + h)) ; infini sur la surface (ecart tolere nul).
            double tanAlpha = h > 0.0 ? radius / Math.sqrt(h * (2.0 * radius + h)) : Double.POSITIVE_INFINITY;
            tolerated = Math.min(Math.tan(SOFT_MAX_ANGLE) * (h + radius) / Math.max(1.0, tanAlpha), 0.5 * h);
        }
        return pose.angleTo(shown) > SOFT_MAX_ANGLE || pose.distanceTo(shown) > tolerated;
    }

    /** Client : ecart entre {@code shown} et l'etat simule au meme tick, absorbe sur {@link #SOFT_TICKS} ticks. */
    private void beginCorrection(SpacePose shown)
    {
        shown.position().sub(pose.position(), correctionPosition);
        shown.orientation().mul(rotationWork.set(pose.orientation()).conjugate(), correctionRotation);
        correctionTicks = SOFT_TICKS;
    }

    /** Client : {@code shown} devient la pose affichee du tick {@link #stateTime} (derniere entree de l'historique). */
    private void store(SpacePose shown, double shownSpeed, SpaceFrameState shownFrame)
    {
        display.set(shown);
        displaySpeed = shownSpeed;
        displayFrame.set(shownFrame);
        historyPose[index(stateTime)].set(shown);
        historyFrame[index(stateTime)].set(shownFrame);
    }

    /** Indice de l'historique du tick {@code tick}. */
    private static int index(long tick) { return (int) (tick & (HISTORY - 1)); }

    /**
     * Retard d'affichage supplementaire d'un observateur (ticks) : retard constate des etats recus plus l'attente
     * maximale d'un envoi ({@value #MIN_SEND_INTERVAL} ticks), borne. Une entree changee par le pilote est ainsi
     * connue avant d'etre affichee : la correction se fait dans l'historique, sans aller-retour visible. Nul pour le
     * pilote local (prediction).
     */
    private double observerDelay()
    {
        if (predicting) return 0.0;
        return Math.max(0.0, Math.min(MAX_OBSERVER_DELAY, lagEstimate + MIN_SEND_INTERVAL));
    }

    // ================================================================ ticks

    /**
     * Pas de simulation jusqu'au tick courant, gestion du pilote, decision d'envoi (voir la documentation de la
     * classe).
     */
    @Override
    public void serverTick(ServerLevel level)
    {
        long now = level.getGameTime();
        if (loadedTime >= 0) {
            if (!poseUnset) rebaseLoadedPose(loadedTime, now);
            loadedTime = -1;
        }
        int previousReference = frame.reference();
        if (stateTime < 0 || now < stateTime || now - stateTime > MAX_REPLAY) stateTime = now;
        if (poseUnset) {
            poseUnset = false;
            motion.defaultPose(current, settings.days(stateTime, 0f), settings, pose);
            speed = 0.0;
            frame.reset(motion.referenceBodyId());
            sendPending = true;
        }
        managePilot(level, now);
        while (stateTime < now) {
            speed = motion.step(pose, speed, frame, input, current, settings.days(stateTime, 0f),
                    settings.days(stateTime + 1, 0f), settings, pose);
            stateTime++;
        }
        nearestIndex = motion.nearestBody(pose, current, settings.days(stateTime, 0f));
        nearestAltitude = motion.nearestAltitude();
        // Referentiel au tick courant : un corps retire est remplace aussitot par le meilleur score (le debut du pas
        // suivant le retrouverait a l'identique) ; altitude affichee par le Controller. Bascule -> envoi.
        motion.refreshReference(pose, input, current, settings.days(stateTime, 0f), settings, frame);
        referenceAltitude = motion.referenceAltitude();
        if (frame.reference() != previousReference) sendPending = true;

        boolean moving = moving();
        long sinceSend = now - lastSendTime;
        if (sendPending && sinceSend >= MIN_SEND_INTERVAL
                || (moving || persistPending) && sinceSend >= CORRECTION_INTERVAL
                || wasMoving && !moving) {
            send(now);
        }
        wasMoving = moving;
    }

    /**
     * Premier tick apres un chargement : la pose sauvegardee est celle du tick {@code saved}. La memoire de dimension
     * n'est reecrite que si elle a change ({@code markDirty}) : un monde arrete pres d'une planete (aucun envoi, mais
     * entraine par son referentiel) garde une pose ancienne alors que le temps de jeu, lui, a avance ; sans recalage,
     * le monde se retrouverait la ou etait la planete. Le monde est donc deplace comme l'aurait fait le mouvement
     * d'entrainement ({@link SpaceMotion}, translation seule) : du deplacement de son corps de reference entre
     * {@code saved} et {@code now}. Rien pour un referentiel immobile (corps sans parent) ou inconnu. Une seule
     * allocation, au chargement.
     */
    private void rebaseLoadedPose(long saved, long now)
    {
        if (now <= saved || current == null) return;
        int ref = current.indexOf(frame.reference());
        if (ref < 0 || current.bodies().get(ref).isRoot()) return;
        Vector3d[] bodies = SpaceOrbits.newPositionArray();
        SpaceOrbits.positions(current, settings.days(saved, 0f), bodies);
        double x = bodies[ref].x;
        double y = bodies[ref].y;
        double z = bodies[ref].z;
        SpaceOrbits.positions(current, settings.days(now, 0f), bodies);
        pose.position().add(bodies[ref].x - x, bodies[ref].y - y, bodies[ref].z - z);
    }

    /** Liberation automatique : joueur absent ou hors de la dimension, marker disparu, silence. */
    private void managePilot(ServerLevel level, long now)
    {
        if (pilot == null) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(pilot);
        if (player == null || player.serverLevel() != level || HoloContainers.position(pilotMarker) == null
                || now - lastInputTime > PILOT_TIMEOUT) {
            releasePilot(pilot);
            return;
        }
        if (now - lastInputTime > INPUT_TIMEOUT && !input.isZero()) {
            input = SpacePilotInput.ZERO;
            sendPending = true;
        }
    }

    /** Monde en mouvement : vitesse non nulle (dans un sens ou l'autre) ou rotation commandee. */
    private boolean moving() { return speed != 0.0 || input.rotating(); }

    private void send(long now)
    {
        markDirty();
        lastSendTime = now;
        sendPending = false;
        persistPending = false;
    }

    /**
     * Client : rejeu jusqu'au tick du niveau, prediction du pilote local et reconciliation lente, puis affichage
     * (correction douce).
     */
    @Override
    public void clientTick()
    {
        if (!synced) return;
        boolean local = isLocalPilot();
        if (local && !predicting) {
            predicting = true;
            predicted.set(display);
            predictedSpeed = displaySpeed;
            predictedFrame.set(displayFrame);
            correctionTicks = 0;
        } else if (!local && predicting) {
            predicting = false;
            localInput = SpacePilotInput.ZERO;
            if (tooFar(display)) snapDisplay();
            else beginCorrection(display);
        }
        // Horloge d'affichage : un tick par tick client ; elle rattrape sa consigne (temps du niveau moins un tick,
        // moins le retard d'un observateur) en changeant sa vitesse (10 % par tick d'ecart, au plus 25 %) : un recalage
        // du niveau par le serveur (toutes les 20 ticks) ne fait plus sauter l'affichage d'un tick.
        clockStart += clockRate;
        long level = clientTime();
        double error = level - 1.0 - observerDelay() - clockStart;
        if (Math.abs(error) > CLOCK_SNAP) {
            clockStart = level - 1.0 - observerDelay();
            error = 0.0;
        }
        double drift = Math.signum(error) * Math.max(0.0, Math.abs(error) - CLOCK_DEADBAND);
        clockRate = 1.0 + Math.max(-CLOCK_STRETCH, Math.min(CLOCK_STRETCH, drift * CLOCK_GAIN));
        // Simulation jusqu'au niveau, et au moins jusqu'a la fin de l'intervalle affiche pendant ce tick.
        long target = Math.max(level, (long) Math.ceil(clockStart + clockRate));
        if (target - stateTime > MAX_REPLAY) {
            stateTime = target;
            snapDisplay();
        } else {
            advance(target);
        }
    }

    /**
     * Client : pas de simulation jusqu'au tick {@code target} avec l'entree courante ; a chaque pas, prediction du
     * pilote local et reconciliation lente, ou correction douce, puis pose d'affichage du tick dans l'historique.
     */
    private void advance(long target)
    {
        SpacePilotInput stepInput = stepInput();
        while (stateTime < target) {
            double begin = settings.days(stateTime, 0f);
            double end = settings.days(stateTime + 1, 0f);
            speed = motion.step(pose, speed, frame, stepInput, current, begin, end, settings, pose);
            if (predicting) {
                predictedSpeed = motion.step(predicted, predictedSpeed, predictedFrame, stepInput, current, begin,
                        end, settings, predicted);
            }
            stateTime++;
            recordSim();
            if (predicting) {
                SpacePose.lerp(predicted, pose, RECONCILE, predicted);
                predictedSpeed += (speed - predictedSpeed) * RECONCILE;
                display.set(predicted);
                displaySpeed = predictedSpeed;
                displayFrame.set(predictedFrame);
            } else {
                if (correctionTicks > 0) correctionTicks--;
                display.set(pose);
                if (correctionTicks > 0) {
                    double f = correctionTicks / (double) SOFT_TICKS;
                    display.position().fma(f, correctionPosition);
                    rotationWork.identity().slerp(correctionRotation, f).mul(pose.orientation(), display.orientation());
                    display.orientation().normalize();
                }
                displaySpeed = speed;
                displayFrame.set(frame);
            }
            store(display, displaySpeed, displayFrame);
        }
    }

    /** Entree utilisee pour simuler : celle du pilote local s'il pilote, sinon la derniere recue. */
    private SpacePilotInput stepInput() { return isLocalPilot() ? localInput : input; }

    private long clientTime() { return backend == null ? Math.max(stateTime, 0L) : backend.gameTime(); }

    // ================================================================ API serveur : pilotage

    /**
     * Prend le pilotage pour {@code player} depuis le Controller du marker {@code markerId}. Accepte si personne ne
     * pilote ou si c'est deja lui (le marker est alors mis a jour), si le joueur est dans cette dimension et si le
     * marker est charge.
     */
    public boolean claimPilot(ServerPlayer player, String markerId)
    {
        requireServer();
        if (markerId == null || of(player.serverLevel()) != this || HoloContainers.position(markerId) == null) {
            return false;
        }
        UUID id = player.getUUID();
        if (pilot != null && !pilot.equals(id)) return false;
        lastInputTime = player.serverLevel().getGameTime();
        pilotMarker = markerId;
        if (pilot == null) {
            pilot = id;
            sendPending = true;
        }
        return true;
    }

    /** Rend le pilotage si {@code player} pilote ; l'entree est remise a zero (le monde garde sa vitesse). */
    public void releasePilot(UUID player)
    {
        requireServer();
        if (pilot == null || !pilot.equals(player)) return;
        pilot = null;
        pilotMarker = null;
        input = SpacePilotInput.ZERO;
        sendPending = true;
    }

    /** Nouvelle entree du pilote ; ignoree si {@code player} n'est pas le pilote. Envoyee au plus tous les 4 ticks. */
    public void pilotInput(ServerPlayer player, SpacePilotInput in)
    {
        requireServer();
        if (pilot == null || !pilot.equals(player.getUUID()) || in == null) return;
        lastInputTime = player.serverLevel().getGameTime();
        SpacePilotInput clean = in.sanitize();
        if (clean.equals(input)) return;
        input = clean;
        sendPending = true;
    }

    /** Pilote courant, ou {@code null} (deux cotes). */
    public UUID pilot() { return pilot; }

    /** Id du marker du Controller du pilote courant, ou {@code null} (serveur). */
    public String pilotMarker() { return pilotMarker; }

    /** Entree courante du pilote (serveur : appliquee ; client : derniere recue). */
    public SpacePilotInput input() { return input; }

    // ================================================================ API serveur : lecture

    /** Systemes de la galaxie, liste non modifiable d'elements immuables (serveur ; vide cote client). */
    public List<SpaceSystem> galaxy() { return galaxy.systems(); }

    /** Systeme d'id {@code id}, ou {@code null} (serveur). */
    public SpaceSystem system(int id) { return galaxy.system(id); }

    /** Revision de la galaxie : change a chaque edition reussie et au changement de systeme courant (deux cotes). */
    public int galaxyRevision() { return revision; }

    /** Id du systeme courant ({@code 0} cote client avant la premiere reception). */
    public int currentSystemId() { return currentId; }

    /** Systeme courant (serveur : celui de la galaxie ; client : copie recue, {@code null} avant reception). */
    public SpaceSystem currentSystem() { return current; }

    /**
     * Pose courante, <b>lecture seule</b> (instance interne) : serveur = pose simulee au tick courant ; client = pose
     * d'affichage du dernier tick simule (derniere entree de l'historique, un peu en avance sur l'image : voir
     * {@link #pose(float, SpacePose)}).
     */
    public SpacePose pose() { return host().clientSide() ? display : pose; }

    /**
     * Vitesse nominale signee, km par tick, relative au corps de reference ({@code < 0} : marche arriere ; client :
     * vitesse affichee).
     */
    public double speed() { return host().clientSide() ? displaySpeed : speed; }

    /** Reglages du mouvement et de l'horloge (deux cotes). */
    public SpaceMotionSettings settings() { return settings; }

    /** Corps de plus petite altitude au tick courant, ou {@code null} (serveur, calcule a chaque tick). */
    public SpaceBody nearestBody()
    {
        return nearestIndex < 0 || current == null || nearestIndex >= current.bodies().size() ? null
                : current.bodies().get(nearestIndex);
    }

    /** Indice de {@link #nearestBody()} dans le systeme courant, {@code -1} sans corps (serveur). */
    public int nearestBodyIndex() { return nearestIndex; }

    /** Altitude au-dessus de {@link #nearestBody()}, km (serveur). */
    public double nearestAltitude() { return nearestAltitude; }

    /**
     * Id du corps de reference ({@code 0} = aucun) : serveur = referentiel de la pose simulee au tick courant ;
     * client = referentiel de la pose affichee (prediction du pilote local comprise).
     */
    public int referenceBodyId() { return host().clientSide() ? displayFrame.reference() : frame.reference(); }

    /**
     * Poids du referentiel courant dans le mouvement d'entrainement, {@code [0, 1]} : monte de 0 a 1 (smoothstep) en
     * {@value SpaceMotion#BLEND_TICKS} ticks apres une bascule, {@code 1} hors fondu ; deux cotes, comme
     * {@link #referenceBodyId()}.
     */
    public double referenceBlend() { return host().clientSide() ? displayFrame.blend() : frame.blend(); }

    /**
     * Corps de reference dans le systeme courant, ou {@code null} (aucun systeme, ou corps retire en attendant le pas
     * suivant) ; deux cotes, comme {@link #referenceBodyId()}.
     */
    public SpaceBody referenceBody()
    {
        int id = referenceBodyId();
        return id == 0 || current == null ? null : current.body(id);
    }

    /** Altitude au-dessus du corps de reference au tick courant, km (serveur, calculee a chaque tick). */
    public double referenceAltitude() { return referenceAltitude; }

    /** Tick de jeu de l'etat courant ({@code -1} avant le premier tick serveur). */
    public long stateTime() { return stateTime; }

    /** Temps simule de l'etat courant, en jours (serveur). */
    public double days() { return settings.days(Math.max(stateTime, 0L), 0f); }

    // ================================================================ API serveur : edition

    /** Ajoute un systeme (avec une etoile par defaut) ; {@link SpaceEditResult#id()} = son id. */
    public SpaceEditResult addSystem(String name, double x, double y, double z)
    {
        requireServer();
        SpaceEditResult result = galaxy.addSystem(name, x, y, z);
        return edited(result, result.id(), null);
    }

    /** Retire un systeme ; refuse le dernier. S'il etait courant, le premier restant le devient (pose par defaut). */
    public SpaceEditResult removeSystem(int id)
    {
        requireServer();
        SpaceSystem before = galaxy.system(id);
        SpaceEditResult result = galaxy.removeSystem(id);
        if (result.success() && id == currentId) {
            currentId = galaxy.systems().get(0).id();
            current = galaxy.system(currentId);
            resetPose();
        }
        return edited(result, id, before);
    }

    public SpaceEditResult renameSystem(int id, String name)
    {
        requireServer();
        SpaceSystem before = galaxy.system(id);
        return edited(galaxy.renameSystem(id, name), id, before);
    }

    /** Deplace un systeme dans la galaxie (al). */
    public SpaceEditResult moveSystem(int id, double x, double y, double z)
    {
        requireServer();
        SpaceSystem before = galaxy.system(id);
        return edited(galaxy.moveSystem(id, x, y, z), id, before);
    }

    /**
     * Change le systeme du monde ; la pose est remise a la pose par defaut du systeme (aussi pour le systeme deja
     * courant : reinitialisation), vitesse nulle.
     */
    public SpaceEditResult setCurrentSystem(int id)
    {
        requireServer();
        if (!galaxy.contains(id)) return SpaceEditResult.refused("Systeme inconnu : " + id);
        currentId = id;
        current = galaxy.system(id);
        resetPose();
        revision++;
        sendPending = true;
        return SpaceEditResult.ok();
    }

    /** Ajoute un corps (valeurs par defaut de son type) ; {@link SpaceEditResult#id()} = son id. */
    public SpaceEditResult addBody(int systemId, SpaceBodyKind kind, int parentId)
    {
        requireServer();
        SpaceSystem before = galaxy.system(systemId);
        return edited(galaxy.addBody(systemId, kind, parentId), systemId, before);
    }

    /** Remplace les champs editables du corps de meme id ({@code kind} et {@code parentId} immuables). */
    public SpaceEditResult updateBody(int systemId, SpaceBody body)
    {
        requireServer();
        SpaceSystem before = galaxy.system(systemId);
        return edited(galaxy.updateBody(systemId, body), systemId, before);
    }

    /** Retire un corps et ses descendants ; refuse la derniere etoile centrale. */
    public SpaceEditResult removeBody(int systemId, int bodyId)
    {
        requireServer();
        SpaceSystem before = galaxy.system(systemId);
        return edited(galaxy.removeBody(systemId, bodyId), systemId, before);
    }

    /**
     * Apres une operation du modele : revision incrementee ; envoi si le bloc froid change (systeme courant, ou
     * etoile de fond du systeme touche), sinon envoi differe pour la sauvegarde.
     */
    private SpaceEditResult edited(SpaceEditResult result, int systemId, SpaceSystem before)
    {
        if (!result.success()) return result;
        revision++;
        current = galaxy.system(currentId);
        SpaceSystem after = galaxy.system(systemId);
        boolean cold = systemId == currentId || before == null || after == null
                || !BackgroundStar.of(before).equals(BackgroundStar.of(after));
        if (cold) sendPending = true;
        else persistPending = true;
        return result;
    }

    /**
     * Pose par defaut du systeme courant, vitesse nulle, referentiel = meilleur score a cette pose, sans fondu
     * (recalculee au premier tick si l'heure n'est pas connue).
     */
    private void resetPose()
    {
        if (stateTime < 0) poseUnset = true;
        motion.defaultPose(current, settings.days(Math.max(stateTime, 0L), 0f), settings, pose);
        speed = 0.0;
        frame.reset(motion.referenceBodyId());
    }

    // ================================================================ API serveur : reglages

    /** Axe avant du monde (yaw, pitch en degres ; pitch borne a 80). */
    public SpaceEditResult setForward(double yaw, double pitch)
    {
        requireServer();
        if (!Double.isFinite(yaw) || !Double.isFinite(pitch)) return nonFinite();
        return setSettings(settings.withForward(yaw, pitch));
    }

    /** Vitesse nominale maximale, km par tick. */
    public SpaceEditResult setMaxSpeed(double maxSpeed)
    {
        requireServer();
        if (!Double.isFinite(maxSpeed)) return nonFinite();
        return setSettings(settings.withMaxSpeed(maxSpeed));
    }

    /** Taux de lacet / tangage a pleine deviation, degres par tick. */
    public SpaceEditResult setTurnRate(double turnRate)
    {
        requireServer();
        if (!Double.isFinite(turnRate)) return nonFinite();
        return setSettings(settings.withTurnRate(turnRate));
    }

    /** Vitesse de roulis, degres par tick. */
    public SpaceEditResult setRollSpeed(double rollSpeed)
    {
        requireServer();
        if (!Double.isFinite(rollSpeed)) return nonFinite();
        return setSettings(settings.withRollSpeed(rollSpeed));
    }

    /** Secondes simulees par tick pour les orbites ({@code 0} = orbites figees). */
    public SpaceEditResult setTimeScale(double timeScale)
    {
        requireServer();
        if (!Double.isFinite(timeScale)) return nonFinite();
        return setSettings(settings.withTimeScale(timeScale));
    }

    private SpaceEditResult setSettings(SpaceMotionSettings value)
    {
        if (!value.equals(settings)) {
            settings = value;
            sendPending = true;
        }
        return SpaceEditResult.ok();
    }

    private static SpaceEditResult nonFinite() { return SpaceEditResult.refused("Valeur non finie"); }

    private static SpacePose[] newPoses(int count)
    {
        SpacePose[] array = new SpacePose[count];
        for (int i = 0; i < count; i++) array[i] = new SpacePose();
        return array;
    }

    private static SpaceFrameState[] newFrames(int count)
    {
        SpaceFrameState[] array = new SpaceFrameState[count];
        for (int i = 0; i < count; i++) array[i] = new SpaceFrameState();
        return array;
    }

    private void requireServer()
    {
        if (host().clientSide()) throw new IllegalStateException("Space Environment : methode serveur cote client");
    }

    // ================================================================ API client

    /** Vrai des que le miroir client a recu un etat valide (toujours vrai cote serveur). */
    public boolean synced() { return !host().clientSide() || synced; }

    /**
     * Pose interpolee pour le rendu au temps d'affichage ({@link #displayDays(float)}), entre les deux entrees de
     * l'historique qui l'encadrent, <b>dans le repere du referentiel</b> ({@link SpaceMotion#interpolate}) :
     * {@code s - P_frame} interpole en ligne droite, {@code P_frame} (position du referentiel, fondu compris) rajoute
     * au temps d'affichage ; {@code slerp} de l'orientation. Pres d'un corps a {@code V = 0}, l'ecart au corps est donc
     * constant a toute fraction de tick. Sans allocation (fil de rendu).
     */
    public SpacePose pose(float partialTick, SpacePose dest)
    {
        double tau = displayTime(partialTick);
        long k = displayTick(tau);
        int previous = (int) (k & (HISTORY - 1));
        int next = (int) ((k + 1) & (HISTORY - 1));
        return displayMotion.interpolate(historyPose[previous], historyPose[next], historyFrame[next], current,
                settings.days(k, 0f), settings.days(k + 1, 0f), settings.days(k, (float) (tau - k)), tau - k, dest);
    }

    /**
     * Temps simule de l'affichage, en jours. Horloge propre au client ({@link #displayTime}) : environ un tick de
     * retard sur le niveau (plus le retard d'un observateur, {@link #observerDelay}), sans saut quand le serveur
     * recale le temps du niveau. Deux appels dans la meme frame donnent le meme temps.
     */
    public double displayDays(float partialTick)
    {
        double tau = displayTime(partialTick);
        long k = displayTick(tau);
        return settings.days(k, (float) (tau - k));
    }

    /**
     * Temps d'affichage en ticks : debut du tick client courant plus {@code partialTick} fois la vitesse de l'horloge,
     * borne a l'historique.
     */
    private double displayTime(float partialTick)
    {
        double tau = clockStart + partialTick * clockRate;
        double first = Math.max(historyFirst, stateTime - HISTORY + 1);
        return Math.max(first, Math.min(tau, stateTime));
    }

    /** Tick de debut de l'intervalle d'historique qui contient {@code tau} (au plus {@code stateTime - 1}). */
    private long displayTick(double tau) { return Math.min((long) Math.floor(tau), stateTime - 1); }

    /**
     * Positions des corps du systeme courant (km, repere systeme) au temps {@link #displayDays(float)} ;
     * {@code dest[i]} pour {@code currentSystem().bodies().get(i)}. Sans allocation.
     *
     * @param dest au moins {@link com.amynna.hololib.modules.dimension.space.model.SpaceLimits#MAX_BODIES} vecteurs
     *             ({@link SpaceOrbits#newPositionArray()})
     * @return nombre de positions ecrites ({@code 0} sans systeme)
     */
    public int bodyPositions(float partialTick, Vector3d[] dest)
    {
        if (current == null) return 0;
        SpaceOrbits.positions(current, displayDays(partialTick), dest);
        return current.bodies().size();
    }

    /** Etoiles de fond : tous les systemes sauf le courant (liste non modifiable). */
    public List<BackgroundStar> backgroundStars() { return stars; }

    /** Vrai si le joueur local est le pilote (client). */
    public boolean isLocalPilot()
    {
        if (!host().clientSide() || pilot == null || backend == null) return false;
        return pilot.equals(backend.localPlayer());
    }

    /**
     * Client du pilote : entree appliquee immediatement par la prediction locale (sans attendre l'echo du serveur),
     * a appeler a chaque changement d'entree (l'envoi au serveur reste a la charge du Controller). Sans effet tant que
     * le joueur local n'est pas le pilote dans l'etat recu ; remise a zero a la fin du pilotage.
     */
    public void predictPilotInput(SpacePilotInput in)
    {
        if (!host().clientSide()) return;
        localInput = in == null ? SpacePilotInput.ZERO : in.sanitize();
    }
}
