package com.amynna.hololib.modules.dimension.space;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.dimension.DimensionHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceFrameState;
import com.amynna.hololib.modules.dimension.space.math.SpaceJump;
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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
 * <b>Synchronisation</b> ({@link #writeSync}) : bloc chaud, revision annoncee, bloc froid <b>optionnel</b> (D16, T15b).
 * <pre>
 * chaud : long T0 (stateTime, -1 avant le premier tick), pose (7 double : x y z qx qy qz qw), double V (signee,
 *         relative au referentiel), etat du referentiel (5 varints : id du corps de reference (0 = aucun : recalcule
 *         au pas suivant), id du precedent, ticks depuis la bascule, id du candidat, ticks de candidature), entree du
 *         pilote (7 octets, frein et visee compris, + varint de la cible si le drapeau du saut est leve,
 *         {@link SpacePilotInput#write}), boolean pilote present [+ UUID], reglages (7 double, vitesse de distorsion
 *         comprise, {@link SpaceMotionSettings#write}), saut ({@link SpaceJump#write} : 1 octet sans saut, 84 a 89
 *         pendant un saut)
 * entete : varint revision (celle du systeme courant et des etoiles de fond de cet etat), varint L (0 = pas de bloc
 *         froid)
 * froid : (L octets, si L &gt; 0) systeme courant complet, etoiles de fond (varint + n x (varint id + nom UTF + 20 o),
 *         {@link BackgroundStar#write})
 * </pre>
 * <b>Inclusion du bloc froid</b> (serveur) : le framework appelle {@code writeSync} soit pour l'envoi de fin de tick qui
 * suit un {@code markDirty} (meme octets pour tous les joueurs de la dimension), soit hors de cet envoi : instantane
 * d'un joueur qui arrive (connexion, changement de dimension, reapparition) ou installation du module. Le module sait
 * lequel : il ne marque l'etat sale que dans {@link #send}, juste avant l'envoi de fin de tick ({@code flushExpected}).
 * Hors envoi de fin de tick : bloc froid <b>toujours</b> inclus (le destinataire n'a peut-etre rien). Dans l'envoi de
 * fin de tick : inclus seulement si la revision a change depuis le dernier bloc froid diffuse (edition, restauration
 * T14, changement de systeme), ou s'il est force : arrivee d'un joueur sans instantane reconnu (suivi de
 * {@code level.players()} a chaque tick, filet de securite), ou demande d'un client qui ne l'a pas
 * ({@link #ACTION_REQUEST_COLD}, au plus une fois tous les {@value #CORRECTION_INTERVAL} ticks). Sans joueur dans la
 * dimension, la revision courante est reputee connue de tous (chaque arrivant la recevra dans son instantane). Le bloc
 * froid est mis en cache et reconstruit quand la revision change.
 * <p>
 * <b>Client</b> : un etat dont la revision annoncee n'est pas celle du bloc froid connu (ou sans aucun bloc froid
 * connu) est ignore : le miroir n'est plus {@link #synced()} (ciel reduit au fond), et il redemande le bloc froid au
 * serveur ({@value #CORRECTION_INTERVAL} ticks entre deux demandes) ; le premier etat complet recu le resynchronise
 * (saut). Un bloc froid deja connu (meme revision) est saute ({@code L} octets).
 * <p>
 * <b>Politique d'envoi</b> (serveur, decidee dans {@link #serverTick}) : un envoi demande (edition qui change le bloc
 * froid, systeme courant, reglage, nouvelle entree du pilote, pilote pris ou rendu, changement de referentiel) part
 * au plus une fois tous les
 * {@value #MIN_SEND_INTERVAL} ticks ; monde en mouvement : correction tous les {@value #CORRECTION_INTERVAL} ticks, et
 * un envoi a l'arret ; une edition qui ne change pas le bloc froid n'est envoyee qu'au plus tous les
 * {@value #CORRECTION_INTERVAL} ticks (seul moyen de la faire sauvegarder : le framework sauve sur {@code markDirty}).
 * <p>
 * <b>Persistance</b> (T14, {@code docs/space/03-modele-donnees.md} par. 4) : la memoire de dimension n'est reecrite
 * qu'apres un {@code markDirty} ; l'etat vivant est en plus recopie a chaque sauvegarde du niveau dans
 * {@link SpaceArchive}, branchee au premier tick serveur. Au chargement, l'archive remplace l'etat de la memoire si
 * elle est plus recente ; a l'installation par commande (aucun etat), elle le fournit ; au retrait
 * ({@link #onUnload}), elle recoit l'etat. Dans tous les cas, la pose est recalee au premier tick (C37,
 * {@link #rebaseLoadedPose}).
 * <p>
 * <b>Client</b> (T07e, {@code docs/space/05-synchronisation.md} par. 2) : dead reckoning (rejeu depuis {@code T0}, puis
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
 * <b>Saut</b> (T21, {@code docs/space/04-mathematiques.md} par. 8) : etat {@link SpaceJump} porte par chaque suite de
 * pas (serveur, rejeu, prediction, historiques), avance par {@link SpaceMotion} dans le meme pas que le mouvement,
 * declenche par l'entree du pilote (drapeau + id de cible) ; cibles cherchees dans la galaxie (serveur) ou les etoiles
 * de fond (client), memes coordonnees ({@link #jumpTarget}). L'arrivee (systeme courant <- cible, revision, pose
 * pres de l'etoile, C59) est faite par le serveur seul ({@link #arrive}) ; un client reste au bout du voyage jusqu'a l'etat
 * qui porte le nouveau systeme (meme paquet que son bloc froid). Changement de phase -> envoi ; saut en cours = monde
 * en mouvement (correction toutes les {@value #CORRECTION_INTERVAL} ticks). Sauvegarde sous la cle {@code jump} ;
 * recalage C37 inactif pendant un saut. Lecture : {@link #jump()}, {@link #jumpAt(float)}, progressions et temps
 * restant au temps d'affichage ({@link #jumpChargeProgress}, {@link #jumpTravelProgress},
 * {@link #jumpRemainingSeconds}), {@link #warpSpeed()}.
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
    /** Action client -> serveur (premier octet) : le client n'a pas le bloc froid de la revision annoncee. */
    public static final int ACTION_REQUEST_COLD = 0;
    /**
     * Banc en jeu (T15b) : vrai = taille de chaque etat ecrit par {@link #writeSync} dans le log (bloc froid inclus ou
     * non). Desactive dans le code livre.
     */
    private static final boolean LOG_SYNC_SIZES = false;
    /** Part de l'ecart rattrapee a chaque tick par la prediction du pilote local. */
    private static final double RECONCILE = 0.1;
    /**
     * Ticks consecutifs de desaccord entre le saut predit du pilote local et le saut rejoue depuis le serveur au-dela
     * desquels la prediction est ramenee sur l'etat rejoue ({@link #reconcileJump}) : plus que la latence d'un
     * envoi (au plus {@value #CORRECTION_INTERVAL} ticks entre deux etats pendant un saut).
     */
    private static final int JUMP_DESYNC = 2 * CORRECTION_INTERVAL;
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
    /** Saut de {@link #pose} (T21) : avance dans le meme pas que le mouvement. */
    private final SpaceJump jump = new SpaceJump();
    /** Cibles du saut : galaxie (serveur), etoiles de fond (client) ; voir {@link #jumpTarget}. */
    private final SpaceJump.Targets targets = this::jumpTarget;
    /** Une instance par module : le module serveur et son miroir client tournent sur des fils differents. */
    private final SpaceMotion motion = new SpaceMotion();

    // ---------------------------------------------------------------- serveur

    private boolean poseUnset;
    /** Tick de la pose chargee (cle {@code time}), {@code -1} si inconnu ; consomme au premier tick serveur. */
    private long loadedTime = -1;
    /** Vrai si l'etat vient d'une sauvegarde ({@link #load}) ; faux pour une installation par commande. */
    private boolean stateLoaded;
    /** Tick de la pose de l'etat charge depuis la memoire ({@code -1} si inconnu), compare a l'archive. */
    private long loadedStamp = -1;
    /** Archive du niveau ({@link SpaceArchive}), branchee au premier tick serveur ; {@code null} avant. */
    private SpaceArchive archive;
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
    /** Vrai entre {@link #send} ({@code markDirty}) et l'ecriture de l'envoi de fin de tick qui le suit. */
    private boolean flushExpected;
    /** Revision du dernier bloc froid diffuse a toute la dimension (connue de tous les joueurs presents). */
    private int broadcastRevision;
    /** Bloc froid a inclure dans le prochain envoi de fin de tick ; vrai au depart (personne ne le connait encore). */
    private boolean coldForced = true;
    /** Ecritures hors envoi de fin de tick (instantanes, installation) depuis le tick precedent. */
    private int snapshotWrites;
    /** Joueurs de la dimension au tick precedent. */
    private final Set<UUID> present = new HashSet<>();
    /** Tick de la derniere demande de bloc froid acceptee. */
    private long lastColdRequest = Long.MIN_VALUE / 2;
    /** Vecteur de travail de l'arrivee d'un saut. */
    private final Vector3d jumpWork = new Vector3d();

    // ---------------------------------------------------------------- client

    private boolean synced;
    private boolean knowsCold;
    /** Vrai si un etat a ete ignore faute du bloc froid de sa revision : demande au serveur en cours. */
    private boolean awaitingCold;
    /** Temps client de la derniere demande de bloc froid. */
    private long coldRequestTime = Long.MIN_VALUE / 2;
    private SpacePilotInput localInput = SpacePilotInput.ZERO;
    private boolean predicting;
    private final SpacePose predicted = new SpacePose();
    private double predictedSpeed;
    private final SpaceFrameState predictedFrame = new SpaceFrameState();
    private final SpaceJump predictedJump = new SpaceJump();
    /** Ticks consecutifs ou le saut predit et le saut rejoue depuis le serveur different (phase ou cible). */
    private int jumpMismatch;
    /** Pose affichee au tick {@link #stateTime} (derniere entree de l'historique). */
    private final SpacePose display = new SpacePose();
    private double displaySpeed;
    /** Referentiel de la pose affichee, apres le pas qui a mene a {@link #display}. */
    private final SpaceFrameState displayFrame = new SpaceFrameState();
    /** Saut de la pose affichee, apres le pas qui a mene a {@link #display}. */
    private final SpaceJump displayJump = new SpaceJump();
    /**
     * Historique d'affichage : pose affichee et referentiel (apres le pas qui y mene) du tick {@code t} a l'indice
     * {@code t & (HISTORY - 1)}, valables de {@link #historyFirst} a {@link #stateTime}.
     */
    private final SpacePose[] historyPose = newPoses(HISTORY);
    private final SpaceFrameState[] historyFrame = newFrames(HISTORY);
    private final SpaceJump[] historyJump = newJumps(HISTORY);
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
    private final SpaceJump[] simJump = newJumps(HISTORY);
    private final double[] simSpeed = new double[HISTORY];
    private long simFirst;
    private final SpacePose searchPose = new SpacePose();
    private final SpaceFrameState searchFrame = new SpaceFrameState();
    private final SpaceJump searchJump = new SpaceJump();
    /** Positions des corps pour remplir l'historique d'un saut. */
    private final Vector3d[] historyBodies = SpaceOrbits.newPositionArray();
    /** Instance du rendu ({@link #pose(float, SpacePose)}) : n'ecrase pas l'espace de travail de la simulation. */
    private final SpaceMotion displayMotion = new SpaceMotion();
    private final SpaceFrameState receivedFrame = new SpaceFrameState();
    private final SpaceJump receivedJump = new SpaceJump();
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
     * L'archive ({@link SpaceArchive}) n'est lue qu'au premier tick serveur ({@link #bindArchive}) : {@code onLoad}
     * ne connait pas le niveau.
     */
    @Override
    public void onLoad()
    {
        if (host().clientSide()) return;
        completeState();
    }

    /**
     * Serveur : l'etat est recopie dans l'archive ({@link SpaceArchive#store}), qui le rendra a une reinstallation
     * (retrait par commande ; aussi au dechargement du niveau, sans effet sur le disque : la derniere sauvegarde du
     * niveau a deja recopie l'etat vivant). Rien si l'archive n'est pas encore branchee (aucun tick).
     */
    @Override
    public void onUnload()
    {
        if (host().clientSide() || archive == null) return;
        CompoundTag state = new CompoundTag();
        save(state);
        archive.store(state, stateTime);
        archive.detach(this);
        archive = null;
    }

    /**
     * Serveur : complete un etat charge, restaure ou vide (galaxie par defaut, repli du systeme courant, pose par
     * defaut provisoire si elle manque).
     */
    private void completeState()
    {
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
            jump.reset();
        }
    }

    // ================================================================ sauvegarde

    /**
     * Cles : {@code galaxy, current, pose, time} (tick de la pose), {@code speed, frame} (etat du referentiel,
     * {@link SpaceFrameState#save}), {@code settings} ({@link SpaceMotionSettings#save}, vitesse de distorsion
     * comprise), {@code rev}, {@code jump} (saut en cours seulement, {@link SpaceJump#save} ; absent = pas de saut).
     * Le pilote n'est pas sauvegarde. Aussi appele par
     * {@link SpaceArchive} (copie de l'etat vivant a chaque sauvegarde du niveau).
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
        if (jump.active()) tag.put("jump", jump.save());
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
            loadedStamp = loadedTime;
        } else {
            poseUnset = true;
        }
        stateLoaded = true;
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
        // Saut en cours (T21, C43) : un voyage reprend ; une charge s'annule au premier pas (pilote non sauvegarde).
        String problem = jump.load(tag.getCompound("jump"));
        if (problem != null) HoloLib.LOGGER.warn("Space : saut sauvegarde illisible ({}), ignore", problem);
    }

    // ================================================================ synchronisation

    /**
     * Bloc chaud, revision, puis bloc froid seulement si le destinataire peut en avoir besoin (voir la documentation
     * de la classe) : toujours hors envoi de fin de tick (instantane, installation) ; dans l'envoi de fin de tick, si
     * la revision a change depuis le dernier bloc froid diffuse ou s'il est force.
     */
    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        int start = buf.writerIndex();
        // Chaud.
        buf.writeLong(stateTime);
        pose.write(buf);
        buf.writeDouble(speed);
        frame.write(buf);
        input.write(buf);
        buf.writeBoolean(pilot != null);
        if (pilot != null) buf.writeUUID(pilot);
        settings.write(buf);
        jump.write(buf);
        // Entete et froid (en cache).
        boolean flushing = flushExpected;
        flushExpected = false;
        boolean withCold = !flushing || coldForced || revision != broadcastRevision;
        if (!flushing) {
            snapshotWrites++;
        } else if (withCold) {
            broadcastRevision = revision;
            coldForced = false;
        }
        buf.writeVarInt(revision);
        if (withCold) {
            if (coldBytes == null || coldRevision != revision) rebuildCold();
            buf.writeVarInt(coldBytes.length);
            buf.writeBytes(coldBytes);
        } else {
            buf.writeVarInt(0);
        }
        if (LOG_SYNC_SIZES) {
            HoloLib.LOGGER.info("Space : etat {} o ({}, bloc froid {} o, revision {})", buf.writerIndex() - start,
                    flushing ? "envoi de fin de tick" : "instantane", withCold ? coldBytes.length : 0, revision);
        }
    }

    /** Bloc froid : systeme courant, etoiles de fond (la revision est dans l'entete). Jamais vide. */
    private void rebuildCold()
    {
        stars = galaxy.backgroundStars(currentId);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
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
     * le systeme connu (log). Un etat dont le bloc froid n'est pas connu (revision annoncee differente, ou aucun bloc
     * froid recu) n'est pas applique : le miroir cesse d'etre {@link #synced()} et redemande le bloc froid
     * ({@link #clientTick}).
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
            receivedJump.read(buf);
            int newRevision = buf.readVarInt();
            int length = buf.readVarInt();
            if (length < 0 || length > buf.readableBytes()) {
                throw new IllegalArgumentException("Bloc froid de longueur invalide : " + length);
            }
            boolean systemChanged = false;
            if (length > 0 && (!knowsCold || newRevision != revision)) {
                int start = buf.readerIndex();
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
                buf.readerIndex(start + length);
            } else {
                buf.skipBytes(length);
            }
            if (!knowsCold || newRevision != revision) {
                // Bloc froid de cette revision inconnu : etat ignore (le systeme qu'il suppose n'est pas connu), ciel
                // reduit au fond jusqu'a reception ; le prochain etat complet resynchronise (saut).
                if (!awaitingCold) {
                    HoloLib.LOGGER.debug("Space : etat de revision {} sans son bloc froid (connue : {}), en attente",
                            newRevision, knowsCold ? revision : "aucune");
                }
                awaitingCold = true;
                synced = false;
                return;
            }
            awaitingCold = false;
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
        boolean snap = !synced || systemChanged || t0 < 0;
        // Etat en avance sur la simulation (il arrive avant le tick client qui amene le niveau a T0) : l'ancienne
        // trajectoire est d'abord prolongee jusqu'a T0 (anciennes entrees), pour comparer au meme tick.
        if (!snap && t0 > stateTime) {
            if (t0 - stateTime > HISTORY / 2) snap = true;
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
        jump.set(receivedJump);
        // Rejeu jusqu'au dernier tick de l'historique ; au premier etat, jusqu'au niveau.
        long last = snap ? Math.max(clientTime(), t0) : stateTime;
        if (t0 < 0 || last - t0 > MAX_REPLAY) {
            stateTime = Math.max(last, 0L);
            snap = true;
        } else {
            stateTime = t0;
        }
        if (snap) {
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
                searchJump.set(simJump[index(change)]);
                double v = simSpeed[index(change)];
                SpacePilotInput stepInput = stepInput();
                for (long t = change; t < t0 - 1; t++) {
                    v = motion.step(searchPose, v, searchFrame, searchJump, targets, stepInput, current,
                            settings.days(t, 0f), settings.days(t + 1, 0f), settings, searchPose);
                    int i = index(t + 1);
                    historyPose[i].set(searchPose);
                    historyFrame[i].set(searchFrame);
                    historyJump[i].set(searchJump);
                    simPose[i].set(searchPose);
                    simFrame[i].set(searchFrame);
                    simJump[i].set(searchJump);
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
            store(pose, speed, frame, jump);
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
            searchJump.set(simJump[index(m)]);
            double v = simSpeed[index(m)];
            for (long t = m; t < t0; t++) {
                v = motion.step(searchPose, v, searchFrame, searchJump, targets, stepInput, current,
                        settings.days(t, 0f), settings.days(t + 1, 0f), settings, searchPose);
            }
            if (sameAsState(searchPose, v, searchFrame, searchJump)) return m;
        }
        return -1;
    }

    /** Vrai si l'etat de l'historique de la simulation au tick {@code t} est exactement l'etat simule courant. */
    private boolean sameAsState(long t)
    {
        return t >= simFirst && sameAsState(simPose[index(t)], simSpeed[index(t)], simFrame[index(t)],
                simJump[index(t)]);
    }

    /**
     * Vrai si {@code (p, v, f, j)} est exactement (bit a bit) l'etat simule courant ({@link #pose}, {@link #speed},
     * {@link #frame}, {@link #jump}).
     */
    private boolean sameAsState(SpacePose p, double v, SpaceFrameState f, SpaceJump j)
    {
        return v == speed && f.sameAs(frame) && j.sameAs(jump) && p.position().equals(pose.position())
                && p.orientation().equals(pose.orientation());
    }

    /** Client : rejeu de l'etat simule seul (sans historique d'affichage) jusqu'au tick {@code until}. */
    private void replay(long until)
    {
        SpacePilotInput stepInput = stepInput();
        while (stateTime < until) {
            speed = motion.step(pose, speed, frame, jump, targets, stepInput, current, settings.days(stateTime, 0f),
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
        simJump[i].set(jump);
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
        predictedJump.set(jump);
        jumpMismatch = 0;
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
            historyJump[index(t)].set(jump);
            if (moving) {
                SpaceOrbits.positions(current, settings.days(t, 0f), historyBodies);
                entry.position().add(historyBodies[ref].x - rx, historyBodies[ref].y - ry, historyBodies[ref].z - rz);
            }
        }
        store(pose, speed, frame, jump);
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
    private void store(SpacePose shown, double shownSpeed, SpaceFrameState shownFrame, SpaceJump shownJump)
    {
        display.set(shown);
        displaySpeed = shownSpeed;
        displayFrame.set(shownFrame);
        displayJump.set(shownJump);
        historyPose[index(stateTime)].set(shown);
        historyFrame[index(stateTime)].set(shownFrame);
        historyJump[index(stateTime)].set(shownJump);
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
        // Un markDirty non suivi de son envoi de fin de tick (impossible en principe) ne doit pas priver un instantane
        // de son bloc froid.
        flushExpected = false;
        if (archive == null) bindArchive(level);
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
            jump.reset();
            sendPending = true;
        }
        managePilot(level, now);
        SpaceJump.Phase previousPhase = jump.phase();
        while (stateTime < now) {
            speed = motion.step(pose, speed, frame, jump, targets, input, current, settings.days(stateTime, 0f),
                    settings.days(stateTime + 1, 0f), settings, pose);
            stateTime++;
            if (jump.arrived()) arrive();
        }
        // Debut ou annulation de la charge, debut du voyage, arrivee : envoi (au plus tous les 4 ticks).
        if (jump.phase() != previousPhase) sendPending = true;
        nearestIndex = motion.nearestBody(pose, current, settings.days(stateTime, 0f));
        nearestAltitude = motion.nearestAltitude();
        // Referentiel au tick courant : un corps retire est remplace aussitot par le meilleur score (le debut du pas
        // suivant le retrouverait a l'identique) ; altitude affichee par le Controller. Bascule -> envoi.
        motion.refreshReference(pose, input, current, settings.days(stateTime, 0f), settings, frame);
        referenceAltitude = motion.referenceAltitude();
        if (frame.reference() != previousReference) sendPending = true;
        trackPlayers(level);

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
        // Saut en cours (C43) : pose figee (voyage) ou pilotee (charge, annulee au premier pas) : pas de recalage.
        if (now <= saved || current == null || jump.active()) return;
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

    /**
     * Premier tick serveur : branche l'archive du niveau ({@link SpaceArchive}). Si elle contient un etat et que le
     * module a ete installe sans etat (commande), ou que l'archive est plus recente que l'etat de la memoire (la
     * memoire n'a pas ete reecrite a la derniere sauvegarde du niveau : monde immobile, ou moins de
     * {@value #CORRECTION_INTERVAL} ticks depuis le dernier envoi), l'etat archive remplace l'etat courant. Le recalage
     * C37 ({@link #rebaseLoadedPose}) suit, depuis le tick archive.
     */
    private void bindArchive(ServerLevel level)
    {
        archive = SpaceArchive.of(level);
        if (archive.present() && (!stateLoaded || archive.saved() > loadedStamp)) {
            restore(archive.state(), stateLoaded ? "plus recente que la memoire" : "reinstallation");
        }
        archive.attach(this);
    }

    /**
     * Serveur, avant le premier pas : remplace tout l'etat par {@code tag} (format de {@link #save}). Les ids de la
     * galaxie sont ceux de l'etat archive ; la revision est portee au-dessus de celle deja envoyee (un client qui a
     * recu la galaxie par defaut a l'installation ne garde pas son bloc froid) ; envoi demande.
     */
    private void restore(CompoundTag tag, String reason)
    {
        int known = revision;
        galaxy = new SpaceGalaxy();
        poseUnset = false;
        loadedTime = -1;
        load(tag);
        completeState();
        revision = Math.max(revision, known) + 1;
        coldBytes = null;
        sendPending = true;
        HoloLib.LOGGER.info("Space : etat restaure depuis l'archive ({}, tick {})", reason, archive.saved());
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

    /**
     * Monde en mouvement : vitesse non nulle (dans un sens ou l'autre), rotation commandee, ou saut en cours (charge :
     * alignement ; voyage : correction toutes les {@value #CORRECTION_INTERVAL} ticks).
     */
    private boolean moving() { return speed != 0.0 || input.rotating() || jump.active(); }

    /**
     * Serveur : fin du voyage ({@link SpaceJump#arrived()}), dans le pas qui y mene (D10, C59, doc 04 par. 8). Systeme
     * courant <- cible (revision incrementee : le bloc froid part avec l'envoi qui suit), pose d'arrivee pres de
     * l'etoile principale du systeme cible, face a elle ({@link SpaceJump#arrivalPose}), {@code V = 0}, referentiel =
     * etoile principale sans fondu, saut termine. Cible retiree entre-temps (ou devenue le systeme courant) : le saut
     * est abandonne, le monde reste dans le systeme d'origine, a l'arret.
     */
    private void arrive()
    {
        int target = jump.target();
        SpaceSystem system = galaxy.system(target);
        if (system == null || target == currentId) {
            HoloLib.LOGGER.warn("Space : systeme cible du saut {} introuvable, saut abandonne", target);
            jump.reset();
            speed = 0.0;
            sendPending = true;
            return;
        }
        jump.arrivalPose(system, pose.orientation(), settings, jumpWork, pose);
        currentId = target;
        current = system;
        speed = 0.0;
        frame.reset(system.primaryStar().id());
        jump.reset();
        revision++;
        sendPending = true;
    }

    /**
     * Cible de saut ({@link SpaceJump.Targets}) : position de l'etoile de fond du systeme {@code systemId}, coordonnees
     * arrondies en {@code float}. Serveur : systeme de la galaxie (meme arrondi que {@code BackgroundStar.of}) ;
     * client : etoile de fond recue. Faux pour le systeme courant ou un id inconnu.
     */
    private boolean jumpTarget(int systemId, Vector3d dest)
    {
        if (systemId <= 0 || systemId == currentId) return false;
        if (host().clientSide()) {
            BackgroundStar star = backgroundStar(systemId);
            if (star == null) return false;
            dest.set(star.x(), star.y(), star.z());
            return true;
        }
        SpaceSystem system = galaxy.system(systemId);
        if (system == null) return false;
        dest.set((float) system.x(), (float) system.y(), (float) system.z());
        return true;
    }

    /**
     * Suivi des joueurs de la dimension (filet de securite du bloc froid) : chaque arrivant a normalement recu un
     * instantane avec le bloc froid (ecriture hors envoi de fin de tick, comptee dans {@link #snapshotWrites}) ; s'il y a
     * plus d'arrivants que d'instantanes depuis le tick precedent, le bloc froid est force dans le prochain envoi (au
     * plus {@value #MIN_SEND_INTERVAL} ticks). Personne dans la dimension : la revision courante est reputee connue de
     * tous (le prochain arrivant la recevra dans son instantane). Sans allocation hors changement de la liste.
     */
    private void trackPlayers(ServerLevel level)
    {
        List<ServerPlayer> players = level.players();
        int count = players.size();
        int arrivals = 0;
        for (int i = 0; i < count; i++) {
            if (!present.contains(players.get(i).getUUID())) arrivals++;
        }
        if (arrivals > 0 || present.size() != count) {
            present.clear();
            for (int i = 0; i < count; i++) present.add(players.get(i).getUUID());
        }
        if (arrivals > snapshotWrites) {
            coldForced = true;
            sendPending = true;
        }
        snapshotWrites = 0;
        if (count == 0) {
            broadcastRevision = revision;
            coldForced = false;
        }
    }

    /**
     * Action d'un client de la dimension ({@link #ACTION_REQUEST_COLD}) : il n'a pas le bloc froid de la revision
     * annoncee. Bloc froid force dans le prochain envoi, au plus une fois tous les {@value #CORRECTION_INTERVAL} ticks
     * (toute la dimension le recoit : pas d'envoi cible).
     */
    @Override
    public void handleAction(ServerPlayer player, FriendlyByteBuf buf)
    {
        if (buf.readUnsignedByte() != ACTION_REQUEST_COLD) return;
        long now = player.serverLevel().getGameTime();
        if (now - lastColdRequest < CORRECTION_INTERVAL && now >= lastColdRequest) return;
        lastColdRequest = now;
        coldForced = true;
        sendPending = true;
    }

    /** Marque l'etat sale : il est ecrit par l'envoi de fin de tick du framework, juste apres ce tick. */
    private void send(long now)
    {
        markDirty();
        flushExpected = true;
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
        if (awaitingCold) requestCold();
        if (!synced) return;
        boolean local = isLocalPilot();
        if (local && !predicting) {
            predicting = true;
            predicted.set(display);
            predictedSpeed = displaySpeed;
            predictedFrame.set(displayFrame);
            predictedJump.set(displayJump);
            jumpMismatch = 0;
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
            speed = motion.step(pose, speed, frame, jump, targets, stepInput, current, begin, end, settings, pose);
            if (predicting) {
                predictedSpeed = motion.step(predicted, predictedSpeed, predictedFrame, predictedJump, targets,
                        stepInput, current, begin, end, settings, predicted);
            }
            stateTime++;
            recordSim();
            if (predicting) {
                reconcileJump();
                SpacePose.lerp(predicted, pose, RECONCILE, predicted);
                predictedSpeed += (speed - predictedSpeed) * RECONCILE;
                display.set(predicted);
                displaySpeed = predictedSpeed;
                displayFrame.set(predictedFrame);
                displayJump.set(predictedJump);
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
                displayJump.set(jump);
            }
            store(display, displaySpeed, displayFrame, displayJump);
        }
    }

    /**
     * Client du pilote : le saut predit suit son propre compte (la charge commence des l'appui, avant l'echo du
     * serveur), decale du saut rejoue depuis l'etat serveur de la latence. S'ils different (phase ou cible) plus de
     * {@value #JUMP_DESYNC} ticks d'affilee (saut refuse par le serveur, annule par lui, cible inconnue d'un cote),
     * la prediction est ramenee sur l'etat rejoue (pose, vitesse, referentiel, saut) : sans cela, un voyage predit que
     * le serveur n'a pas fait resterait fige au bout de son compte.
     */
    private void reconcileJump()
    {
        if (predictedJump.phase() == jump.phase() && predictedJump.target() == jump.target()) {
            jumpMismatch = 0;
            return;
        }
        if (++jumpMismatch <= JUMP_DESYNC) return;
        jumpMismatch = 0;
        predicted.set(pose);
        predictedSpeed = speed;
        predictedFrame.set(frame);
        predictedJump.set(jump);
    }

    /**
     * Client en attente du bloc froid de la revision annoncee : demande au serveur ({@link #ACTION_REQUEST_COLD}), au
     * plus une fois tous les {@value #CORRECTION_INTERVAL} ticks (une demande perdue ou refusee est refaite).
     */
    private void requestCold()
    {
        long now = clientTime();
        if (now - coldRequestTime < CORRECTION_INTERVAL && now >= coldRequestTime) return;
        coldRequestTime = now;
        sendAction(buf -> buf.writeByte(ACTION_REQUEST_COLD));
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
     * etoile de fond du systeme touche : position, eclat, et depuis T15 nom), sinon envoi differe pour la sauvegarde.
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
        // Changement de systeme ou reinitialisation : un saut en cours est abandonne.
        jump.reset();
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

    /**
     * Vitesse de distorsion du saut, al/s (T15) : refusee si non finie, sinon bornee a
     * {@code [SpaceLimits.MIN_WARP_SPEED, SpaceLimits.MAX_WARP_SPEED]} ; envoyee avec les autres reglages (bloc chaud,
     * au plus tous les {@value #MIN_SEND_INTERVAL} ticks).
     */
    public SpaceEditResult setWarpSpeed(double warpSpeed)
    {
        requireServer();
        if (!Double.isFinite(warpSpeed)) return nonFinite();
        return setSettings(settings.withWarpSpeed(warpSpeed));
    }

    /** Vitesse de distorsion du saut, al/s (deux cotes ; client : derniere valeur recue). */
    public double warpSpeed() { return settings.warpSpeed(); }

    // ================================================================ API : saut (T21, lecture seule)

    /**
     * Saut, <b>lecture seule</b> (instance interne) : serveur = etat simule au tick courant ; client = saut de la pose
     * affichee au dernier tick simule (prediction du pilote local comprise), comme {@link #pose()}. Pour un rendu,
     * preferer {@link #jumpAt(float)} (meme temps que {@link #pose(float, SpacePose)}).
     */
    public SpaceJump jump() { return host().clientSide() ? displayJump : jump; }

    /**
     * Saut au temps d'affichage, <b>lecture seule</b> : client = etat de l'historique a la fin de l'intervalle affiche
     * (le tick {@code k + 1} si le temps d'affichage est {@code k + f}, comme {@link #pose(float, SpacePose)}) ;
     * serveur = {@link #jump()}. Avec {@link #jumpFraction(float)} pour les progressions de {@link SpaceJump}.
     */
    public SpaceJump jumpAt(float partialTick)
    {
        if (!host().clientSide()) return jump;
        double tau = displayTime(partialTick);
        return historyJump[(int) ((displayTick(tau) + 1) & (HISTORY - 1))];
    }

    /**
     * Fraction {@code [0, 1]} de l'intervalle affiche qui mene a {@link #jumpAt(float)} ({@code f} pour un temps
     * d'affichage {@code k + f}) ; serveur : {@code 1} (etat au tick courant).
     */
    public double jumpFraction(float partialTick)
    {
        if (!host().clientSide()) return 1.0;
        double tau = displayTime(partialTick);
        return Math.max(0.0, Math.min(1.0, tau - displayTick(tau)));
    }

    /**
     * Progression de la charge au temps d'affichage, {@code [0, 1]} (roue de charge, T22) : {@code 1} pendant le
     * voyage, {@code 0} sans saut ({@link SpaceJump#chargeProgress}).
     */
    public double jumpChargeProgress(float partialTick)
    {
        return jumpAt(partialTick).chargeProgress(jumpFraction(partialTick));
    }

    /** Progression du voyage au temps d'affichage, {@code [0, 1]} ({@code 0} hors voyage ; distorsion, T23). */
    public double jumpTravelProgress(float partialTick)
    {
        return jumpAt(partialTick).travelProgress(jumpFraction(partialTick));
    }

    /**
     * Temps de voyage restant au temps d'affichage, en secondes ({@link SpaceJump#remainingSeconds}) : pendant la
     * charge, la duree entiere du voyage a venir ; {@code 0} sans saut.
     */
    public double jumpRemainingSeconds(float partialTick)
    {
        return jumpAt(partialTick).remainingSeconds(jumpFraction(partialTick));
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

    private static SpaceJump[] newJumps(int count)
    {
        SpaceJump[] array = new SpaceJump[count];
        for (int i = 0; i < count; i++) array[i] = new SpaceJump();
        return array;
    }

    private void requireServer()
    {
        if (host().clientSide()) throw new IllegalStateException("Space Environment : methode serveur cote client");
    }

    // ================================================================ API client

    /**
     * Vrai si le miroir client a recu un etat valide dont il connait le bloc froid (systeme courant et etoiles de fond
     * de la revision annoncee) ; redevient faux tant qu'un etat recu annonce une revision dont le bloc froid manque
     * (T15b). Toujours vrai cote serveur.
     */
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
     * Temps d'affichage en ticks (meme horloge que {@link #displayDays(float)}, sans l'echelle de temps des orbites) :
     * continu d'une frame a l'autre, il avance meme orbites figees et ne saute pas au changement de systeme (arrivee
     * d'un saut). Pour animer un effet du ciel sans le temps de jeu de Minecraft (C20 ; distorsion du saut, T23).
     * Serveur : tick courant. Lecture seule, sans allocation.
     */
    public double displayTicks(float partialTick)
    {
        return host().clientSide() ? displayTime(partialTick) : stateTime;
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

    /**
     * Etoiles de fond : tous les systemes sauf le courant, dans l'ordre de la galaxie (liste non modifiable). Client :
     * derniere liste recue ; serveur : celle du dernier bloc froid construit (preferer {@link #system(int)}).
     */
    public List<BackgroundStar> backgroundStars() { return stars; }

    /**
     * Etoile de fond du systeme d'id {@code systemId}, ou {@code null} (systeme courant, inconnu, ou rien recu).
     * Recherche lineaire sans allocation (au plus {@code SpaceLimits.MAX_SYSTEMS} etoiles) ; meme source que
     * {@link #backgroundStars()}.
     */
    public BackgroundStar backgroundStar(int systemId)
    {
        List<BackgroundStar> list = stars;
        for (int i = 0, n = list.size(); i < n; i++) {
            BackgroundStar star = list.get(i);
            if (star.id() == systemId) return star;
        }
        return null;
    }

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
