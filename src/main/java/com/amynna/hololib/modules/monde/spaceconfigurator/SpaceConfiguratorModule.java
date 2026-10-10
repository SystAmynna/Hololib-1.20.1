package com.amynna.hololib.modules.monde.spaceconfigurator;

import com.amynna.hololib.holo.edit.HoloEditMath;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAccess;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceMotionSettings;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.math.SpaceUnits;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceEditResult;
import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import com.amynna.hololib.modules.dimension.space.model.SpaceOrbit;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem.SystemHeader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Module {@code hololib:space_configurator} (memoire de position, marker), reserve aux operateurs : cube editable
 * ({@link SpaceMapCube}) qui montre la carte galactique ou un systeme, et fenetre editable separee
 * ({@link SpaceConfiguratorWindow}, T24 : creee devant la base du cube par defaut, puis independante du cube). Il ne stocke <b>pas</b> la galaxie : il en garde une copie, recopiee depuis le
 * Space Environment de la dimension du marker quand sa revision change, et synchronisee a ses observateurs. Les
 * modifications passent par les methodes serveur de l'Environment ({@code SpaceEditResult} : motif du refus affiche
 * au joueur).
 * <p>
 * <b>Etat sauvegarde</b> : mode, systeme et corps selectionnes, corps zoome (T25), origine des coordonnees (T26),
 * cadrage galactique {@code (c, H)}, temps fige du mode systeme {@code t_edit}, marque {@code detached} (T24 : fenetre detachee du cube ; placement, acces, affichage et
 * visibilite des objets le sont par le framework). La copie est recalculee au premier tick.
 * <p>
 * <b>Fenetre</b> (T24) : son placement initial (devant la base du cube <b>par defaut</b>, {@link #windowPlacement})
 * est aussi son transform par defaut (RESET) ; ensuite le module n'y touche plus. Ancienne sauvegarde (sans
 * {@code detached}) : la fenetre est replacee une fois devant la base du cube <b>charge</b>, au premier tick serveur.
 * <p>
 * <b>Poignees</b> ({@link SpaceMapHandle}, cle {@code handle} ; {@link SpaceOrbitHandle}, cle {@code orbit}) : la
 * premiere existe seulement si un systeme selectionne figure dans la copie en mode galaxie, la seconde seulement si un
 * corps <b>a orbite</b> du systeme edite est selectionne en mode systeme. Creees dans {@code onLoad} si l'etat le
 * demande (cote client ; cote serveur la copie n'existe qu'au premier tick), puis ajoutees / retirees au changement
 * d'etat : serveur a chaque tick et apres chaque action, client dans {@code onSync}, seulement si absentes / presentes.
 * <p>
 * <b>Mode systeme</b> (T12) : a l'entree (« Editer »), le temps simule de l'Environment est fige ({@code t_edit},
 * jours) : le cube montre les corps a leurs positions de Kepler a {@code t_edit}, a l'echelle de
 * {@link SpaceSystemScale} (echelle lineaire, tout a l'echelle depuis T25 : plus de loupe des lunes, D13). « Animer »
 * fait avancer localement le temps d'affichage ({@link #displayDays}, client, rien n'est envoye) ; la poignee d'orbite
 * reste posee a {@code t_edit}.
 * <p>
 * <b>Zoom sur un corps</b> (T25, D17) : le corps zoome ({@code 0} = systeme entier) fait partie de l'etat
 * <b>sauvegarde et synchronise</b>, comme la selection : la poignee d'orbite est placee et relue par le serveur, qui
 * doit donc connaitre l'echelle de la vue (un zoom purement client fausserait la conversion de la poignee), et le cube
 * est commun a tous les operateurs. La vue est centree sur le corps ({@link SpaceSystemScale#zoomHalfExtent}) ; la
 * poignee d'orbite n'existe que pour un corps dont le parent est dans le voisinage du zoom (le corps zoome ou un de ses
 * descendants : {@link SpaceSystemScale#orbitInView}). Remis a {@code 0} au changement de mode ou de systeme et si le
 * corps disparait.
 * <p>
 * <b>Origine des coordonnees</b> (T26, C50) : id d'un systeme ({@code 0} = defaut), sauvegarde et synchronise ;
 * systeme effectif resolu par {@link SpaceCoordinates#origin} (le choisi, sinon {@code Sol}, sinon le premier). Remis a
 * {@code 0} (retour au defaut) quand la copie ne contient plus ce systeme. La croix du cube et la ligne d'information
 * de la fenetre donnent les coordonnees galactiques de la selection relativement a lui.
 * <p>
 * <b>Etat synchronise</b> ({@link #writeSync}, envoye seulement au changement de revision de l'Environment ou d'etat
 * d'interface, jamais par tick) :
 * <pre>
 * byte mode (ordinal), varint systeme selectionne (0 = aucun), varint corps selectionne (0 = aucun),
 * varint corps zoome (0 = systeme entier, T25), varint origine (0 = defaut, T26), double t_edit (jours),
 * double cx, double cy, double cz, double H (cadrage, al),
 * boolean environment
 * si environment : int revision copiee, varint systeme courant de l'Environment,
 *                  byte axe avant (indice de FORWARD_YAW, -1 = autre valeur),
 *                  double vitesse de distorsion (al/s, T24),
 *                  en-tetes de tous les systemes (SystemHeader.writeList),
 *                  boolean systeme edite [+ SpaceSystem.write] (present en mode systeme seulement)
 * </pre>
 * L'axe avant et la vitesse de distorsion sont relus dans les reglages de l'Environment chaque tick (changement de
 * l'instance des reglages) : ses reglages ne changent pas sa revision.
 * <b>Actions</b> ({@code sendAction(window, ...)}), premier octet = type ; le serveur verifie <b>d'abord</b> que le
 * joueur est operateur (refus silencieux sinon), puis que la source est la fenetre :
 * <pre>
 * 0 SELECT_SYSTEM + varint id   -> selectionne un systeme (0 = aucun) ; inconnu = message
 * 1 MODE + byte ordinal         -> GALAXY, ou SYSTEM si un systeme est selectionne (sinon message)
 * 2 REFRAME                     -> cadrage recalcule sur tous les systemes (doc 04 par. 7)
 * 3 ADD                         -> addSystem(nom par defaut, centre du cadrage), le nouveau systeme est selectionne
 * 4 REMOVE + varint id          -> removeSystem(id) (refuse pour le dernier) ; selection perdue si c'etait elle
 * 5 RENAME + varint id + utf    -> renameSystem(id, nom) (nom nettoye par le modele, vide refuse)
 * 6 SET_CURRENT + varint id     -> setCurrentSystem(id) (« Monde ici »)
 * 7 FORWARD + byte indice       -> setForward(FORWARD_YAW[i], FORWARD_PITCH[i]) (« Avant du monde »)
 * -- mode systeme (systeme edite = systeme selectionne cote serveur, jamais lu dans l'action) --
 * 8 SELECT_BODY + varint id     -> selectionne un corps du systeme edite (0 = aucun) ; inconnu = message
 * 9 ADD_BODY + byte type + varint parent
 *                               -> addBody(systeme, type, parent) (parent requis, compatibilite verifiee par le
 *                                  modele), le nouveau corps est selectionne
 * 10 REMOVE_BODY + varint id    -> removeBody(systeme, id) (descendants compris ; derniere etoile centrale refusee)
 * 11 UPDATE_BODY + varint id + byte champ + valeur
 *                               -> updateBody(systeme, corps modifie) ; champ : 0 nom (utf), 1 rayon km (double),
 *                                  2 couleur (int ARGB), 3 luminosite (double, etoiles), 4 periode jours (double,
 *                                  corps a orbite), 5 phase degres (double, corps a orbite) ; valeur non finie ou
 *                                  champ sans objet refuses ; bornes appliquees par le modele
 * -- reglages (T24) --
 * 12 WARP_SPEED + double        -> setWarpSpeed(al/s) (« Distorsion », mode galaxie) ; non finie refusee par
 *                                  l'Environment, sinon bornee
 * -- vue (T25) --
 * 13 ZOOM + varint id           -> zoom sur un corps du systeme edite (0 = systeme entier) ; mode systeme requis,
 *                                  corps inconnu = message
 * -- coordonnees (T26) --
 * 14 ORIGIN + varint id         -> origine des coordonnees (0 = defaut) ; systeme inconnu = message
 * </pre>
 * Tout refus de l'Environment (ou son absence) est affiche au joueur dans la barre d'action.
 * <b>Sondage de la poignee</b> ({@code docs/space/04-mathematiques.md} par. 7) : le module retient la derniere position
 * qu'il a ecrite ; un ecart de plus de {@value #HANDLE_EPSILON} bloc dans {@code serverTick} est une edition d'un
 * joueur : {@code q = (R^-1 . (P - t)) / sigma} (unites du cube), borne a {@code [-1, 1]^3}, puis
 * {@code g = c + q . H} et {@code moveSystem}. Le module reecrit la poignee (et retient la valeur) des que la position
 * voulue change : selection, cube, cadrage ou position du systeme. Le cadrage n'est pas recalcule quand une etoile en
 * sort : la poignee reste bornee au cube, « Recadrer » l'agrandit.
 * <p>
 * <b>Sondage de la poignee d'orbite</b> (doc 04 par. 7) : rotation et echelle comparees chaque {@code serverTick} aux
 * dernieres ecrites ({@value #ORBIT_ANGLE_EPSILON} degre, ecart relatif {@value #ORBIT_SCALE_EPSILON}). Rotation
 * changee : {@code R_o = R_cube^-1 . R_poignee} decompose en (yaw, pitch, roll) ; echelle {@code X} ou {@code Z}
 * changee : {@code a = scaleX / sigma . H}, {@code b = scaleZ / sigma . H} ({@code H} = demi-etendue de la vue :
 * systeme entier ou zoom), {@code e = sqrt(1 - (b/a)^2)} borne a {@code [0 ; 0,95]}, {@code 0} si {@code b >= a}.
 * Seules les composantes changees sont recalculees (pas de derive d'arrondi sur les autres), puis {@code updateBody} ;
 * la poignee est ensuite toujours reecrite depuis l'orbite (d'ou {@code scaleY = scaleX}). La conversion utilise
 * l'echelle de la vue <b>avant</b> la modification (celle que le joueur voyait).
 * <p>
 * <b>Sondage du cube</b> : le framework applique les editions sans callback ({@code HoloContainers.handleEdit}).
 * Chaque {@code serverTick} compare le transform du cube a la derniere valeur vue (copie exacte des neuf
 * composantes) ; a la difference : echelle ramenee a la moyenne si elle n'est plus uniforme (C11), valeur retenue. La
 * fenetre ne suit plus le cube (T24). Le client replace les poignees localement ({@code clientTick}, meme calcul, sans
 * toucher a l'echelle) pour qu'elles suivent le cube pendant la manipulation.
 * <p>
 * <b>Acces</b> de la fenetre : {@code HoloAccess.players(<operateurs connectes>)}, recalcule chaque tick serveur et
 * reaffecte seulement s'il change. Cube et fenetre ne sont dessines que chez un operateur ({@code shown()}, backend).
 * <p>
 * Le client passe par un {@link Backend} installe au setup client ({@code client.modules.monde.spaceconfigurator}).
 */
public final class SpaceConfiguratorModule extends HoloModule
{
    /** Mode du Configurator. */
    public enum Mode
    {
        /** Le cube montre la carte galactique. */
        GALAXY,
        /** Le cube montre le systeme selectionne (copie complete synchronisee). */
        SYSTEM
    }

    /** Type d'action : selection d'un systeme (varint id, 0 = aucun). */
    public static final int ACTION_SELECT_SYSTEM = 0;
    /** Type d'action : changement de mode (octet : ordinal de {@link Mode}). */
    public static final int ACTION_MODE = 1;
    /** Type d'action : « Recadrer ». */
    public static final int ACTION_REFRAME = 2;
    /** Type d'action : « Ajouter » (systeme au centre du cadrage). */
    public static final int ACTION_ADD = 3;
    /** Type d'action : « Retirer » (varint id). */
    public static final int ACTION_REMOVE = 4;
    /** Type d'action : renommer (varint id, chaine). */
    public static final int ACTION_RENAME = 5;
    /** Type d'action : « Monde ici » (varint id). */
    public static final int ACTION_SET_CURRENT = 6;
    /** Type d'action : « Avant du monde » (octet : indice de {@link #FORWARD_YAW}). */
    public static final int ACTION_FORWARD = 7;
    /** Type d'action : selection d'un corps du systeme edite (varint id, 0 = aucun). */
    public static final int ACTION_SELECT_BODY = 8;
    /** Type d'action : ajout d'un corps (octet : ordinal de {@link SpaceBodyKind}, varint id du parent). */
    public static final int ACTION_ADD_BODY = 9;
    /** Type d'action : retrait d'un corps et de ses descendants (varint id). */
    public static final int ACTION_REMOVE_BODY = 10;
    /** Type d'action : modification d'un champ d'un corps (varint id, octet champ, valeur). */
    public static final int ACTION_UPDATE_BODY = 11;
    /** Type d'action : vitesse de distorsion du saut (double, al/s ; T24). */
    public static final int ACTION_WARP_SPEED = 12;
    /** Type d'action : zoom sur un corps du systeme edite (varint id, 0 = systeme entier ; T25). */
    public static final int ACTION_ZOOM = 13;
    /** Type d'action : origine des coordonnees (varint id d'un systeme, 0 = defaut ; T26). */
    public static final int ACTION_ORIGIN = 14;
    /** Champ de {@link #ACTION_UPDATE_BODY} : nom (utf). */
    public static final int FIELD_NAME = 0;
    /** Champ : rayon en km (double). */
    public static final int FIELD_RADIUS = 1;
    /** Champ : couleur ARGB (int). */
    public static final int FIELD_COLOR = 2;
    /** Champ : luminosite relative au Soleil (double ; etoiles seulement). */
    public static final int FIELD_LUMINOSITY = 3;
    /** Champ : periode en jours (double ; corps a orbite). */
    public static final int FIELD_PERIOD = 4;
    /** Champ : phase en degres (double ; corps a orbite). */
    public static final int FIELD_PHASE = 5;
    /** Ecart (degres) au-dela duquel un angle de la poignee d'orbite a ete edite. */
    public static final float ORBIT_ANGLE_EPSILON = 1e-3f;
    /** Ecart relatif au-dela duquel une echelle de la poignee d'orbite a ete editee. */
    public static final float ORBIT_SCALE_EPSILON = 1e-5f;
    /** Ecart relatif du rapport {@code scaleZ / scaleX} sous lequel l'excentricite est gardee. */
    private static final double RATIO_EPSILON = 1e-6;
    /** Cosinus du pitch sous lequel la decomposition d'une rotation traite le blocage de cardan. */
    private static final double GIMBAL_EPSILON = 1e-3;
    /** Echelle minimale ecrite dans la poignee d'orbite (un transform exige une echelle positive). */
    private static final float MIN_ORBIT_SCALE = 1e-6f;
    /**
     * Demi-petit axe affiche minimal (unites du cube) d'une orbite pour avoir une poignee (T25) : en dessous (Phobos
     * dans la vue de Sol entier : 3e-7), l'echelle de la poignee serait bornee par {@link #MIN_ORBIT_SCALE} et sa
     * relecture fausse ; l'orbite s'edite alors dans le zoom sur le parent. Ne depend que de l'etat synchronise.
     */
    private static final double MIN_HANDLE_AXIS = 1e-5;
    /**
     * Choix de l'axe avant du monde : nord, est, sud, ouest, haut, bas (yaw en degres, convention de
     * {@link SpaceMotionSettings#forward} : 180 = nord {@code -Z}, -90 = est {@code +X}). Haut et bas sont bornes au
     * pitch maximal des reglages ({@value SpaceMotionSettings#MAX_FORWARD_PITCH} degres), avec le yaw du nord.
     */
    public static final double[] FORWARD_YAW = {180.0, -90.0, 0.0, 90.0, 180.0, 180.0};
    /** Pitch de chaque choix ({@code -80} = haut : {@code F.y = -sin(pitch)}). */
    public static final double[] FORWARD_PITCH = {0.0, 0.0, 0.0, 0.0,
            -SpaceMotionSettings.MAX_FORWARD_PITCH, SpaceMotionSettings.MAX_FORWARD_PITCH};
    /** Ecart (degres) sous lequel les reglages correspondent a un choix. */
    private static final double FORWARD_EPSILON = 0.01;
    /** Ecart (blocs) entre la poignee et la derniere position ecrite au-dela duquel c'est une edition. */
    public static final float HANDLE_EPSILON = 1e-4f;
    /** Longueur maximale (caracteres) d'un nom lu dans une action ; le modele le borne ensuite. */
    private static final int MAX_NAME_READ = SpaceLimits.MAX_NAME_LENGTH * 4;
    /** Niveau de permission d'un operateur (comme les commandes du mod et l'edition). */
    public static final int OP_LEVEL = 2;
    /** Demi-etendue minimale du cadrage galactique, al. */
    public static final double MIN_HALF_EXTENT = 1.0;
    /** Marge du recadrage : {@code H = nice(REFRAME_MARGIN x demi-etendue max)}. */
    public static final double REFRAME_MARGIN = 1.1;
    /** Ecart entre la fenetre et la face avant du cube (placement initial), en blocs. */
    public static final float WINDOW_GAP = 0.25f;
    /** Ecart relatif tolere entre les echelles du cube avant de les uniformiser. */
    private static final float UNIFORM_EPSILON = 1e-4f;
    private static final Component UNKNOWN_SYSTEM = Component.literal("Systeme inconnu");
    private static final Component NO_SELECTION = Component.literal("Selectionner d'abord un systeme");
    private static final Component NO_ENVIRONMENT =
            Component.literal("Aucun Space Environment dans cette dimension");
    private static final Component NOT_SYSTEM_MODE = Component.literal("Passer d'abord en mode systeme");
    private static final Component UNKNOWN_BODY = Component.literal("Corps inconnu");
    private static final Component PARENT_REQUIRED = Component.literal("Un parent est requis");
    private static final Component NON_FINITE = Component.literal("Valeur invalide");
    private static final Component FIELD_UNUSED = Component.literal("Ce champ ne s'applique pas a ce corps");

    private static Backend backend;

    // ---------------------------------------------------------------- etat sauvegarde et synchronise

    private Mode mode = Mode.GALAXY;
    /** Id du systeme selectionne, {@code 0} = aucun. */
    private int selectedSystem;
    /** Id du corps selectionne dans le systeme edite (mode systeme), {@code 0} = aucun. */
    private int selectedBody;
    /** Id du corps zoome dans le systeme edite (mode systeme, T25), {@code 0} = systeme entier. */
    private int zoomBody;
    /** Id du systeme origine des coordonnees (T26), {@code 0} = defaut ({@link SpaceCoordinates#origin}). */
    private int origin;
    /** Centre du cadrage galactique, al. */
    private double frameX;
    private double frameY;
    private double frameZ;
    /** Demi-etendue du cadrage, al ; {@code <= 0} : pas encore cadre (recadrage a la premiere copie). */
    private double frameHalf;
    /** Temps simule fige a l'entree du mode systeme ({@code t_edit}), jours. */
    private double editTime;
    /**
     * Faux apres la lecture d'une sauvegarde d'avant T24 : la fenetre sera replacee une fois devant la base du cube
     * charge (serveur). Vrai par defaut (installation par commande : {@code load} n'est pas appele).
     */
    private boolean windowDetached = true;

    // ---------------------------------------------------------------- copie synchronisee (recalculee)

    private boolean environment;
    private int copiedRevision;
    private int currentSystemId;
    /** Indice du choix de l'axe avant ({@link #FORWARD_YAW}), {@code -1} = autre valeur ou pas d'Environment. */
    private int forward = -1;
    /** Vitesse de distorsion de l'Environment (copie), al/s. */
    private double warpSpeed = SpaceMotionSettings.DEFAULT_WARP_SPEED;
    private List<SystemHeader> headers = List.of();
    private SpaceSystem editedSystem;

    // ---------------------------------------------------------------- serveur

    /** Environment de la derniere copie (une nouvelle instance force une recopie). */
    private SpaceEnvironmentModule lastEnvironment;
    private boolean copied;
    /** Reglages de l'Environment deja lus (instance remplacee a chaque changement). */
    private SpaceMotionSettings lastSettings;
    private final Set<UUID> ops = new HashSet<>();

    // ---------------------------------------------------------------- deux cotes (une instance par cote)

    /** Derniere valeur vue du transform du cube (sondage). */
    private final HoloTransform lastCube = new HoloTransform();
    private boolean cubeKnown;
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f axis = new Vector3f();

    /** Derniere position ecrite dans la poignee (repere du marker) ; valable si {@link #handleWritten}. */
    private final Vector3f lastHandle = new Vector3f();
    private boolean handleWritten;
    private final Vector3f point = new Vector3f();

    private SpaceMapCube cube;
    private SpaceConfiguratorWindow window;
    /** Poignee de l'etoile selectionnee, {@code null} si elle n'existe pas. */
    private SpaceMapHandle handle;

    /** Poignee d'orbite du corps selectionne (mode systeme), {@code null} si elle n'existe pas. */
    private SpaceOrbitHandle orbitHandle;
    /** Derniere valeur ecrite dans la poignee d'orbite ; valable si {@link #orbitWritten}. */
    private final HoloTransform lastOrbit = new HoloTransform();
    private boolean orbitWritten;
    /** Placement voulu de la poignee d'orbite, et transform de travail de la lecture. */
    private final HoloTransform wantedOrbit = new HoloTransform();
    private final HoloTransform orbitScratch = new HoloTransform();
    private final Quaternionf orbitRotation = new Quaternionf();
    /** Positions affichees des corps (unites du cube), tableau reutilise. */
    private final Vector3d[] bodyPoints = SpaceOrbits.newPositionArray();
    /** Systeme et zoom dont l'echelle a ete calculee ({@link #ensureScale}). */
    private SpaceSystem scaledSystem;
    private int scaledZoom;
    /** Indice du corps zoome dans le systeme edite, {@code -1} = systeme entier. */
    private int zoomIndex = -1;
    /** Demi-etendue de la vue (systeme entier ou zoom), UA. */
    private double viewHalf = 1.0;

    // ---------------------------------------------------------------- client (animation)

    /** « Animer » : le temps d'affichage avance localement (jamais envoye). */
    private boolean animating;
    /** Ticks client ecoules depuis le debut de l'animation. */
    private long animationTicks;

    // ---------------------------------------------------------------- client

    /** Pont vers le client, installe au setup client. */
    public interface Backend
    {
        /** Vrai si le joueur local est operateur (niveau {@value SpaceConfiguratorModule#OP_LEVEL}). */
        boolean localOp();
    }

    /** Branche le client (setup client). */
    public static void install(Backend backend)
    {
        SpaceConfiguratorModule.backend = Objects.requireNonNull(backend);
    }

    /** Vrai si le joueur local est operateur ; faux sans backend (serveur dedie). */
    static boolean localOp() { return backend != null && backend.localOp(); }

    // ---------------------------------------------------------------- cycle de vie

    /**
     * Cree le cube, la fenetre (devant la base du cube par defaut : placement initial et transform de RESET) et, si
     * l'etat le demande, la poignee (deux cotes, memes cles) ; affiche l'etat. Le framework restaure ensuite les
     * placements sauvegardes / recus.
     */
    @Override
    public void onLoad()
    {
        cube = addObject(new SpaceMapCube("cube"));
        HoloTransform initial = new HoloTransform();
        windowPlacement(cube.transform(), initial);
        window = addObject(new SpaceConfiguratorWindow("window", this, initial));
        updateHandle();
        refresh();
    }

    @Override
    public void onUnload()
    {
        lastEnvironment = null;
        lastSettings = null;
        handle = null;
        handleWritten = false;
        orbitHandle = null;
        orbitWritten = false;
        animating = false;
    }

    @Override
    public void save(CompoundTag tag)
    {
        tag.putString("mode", mode.name());
        tag.putInt("system", selectedSystem);
        tag.putInt("body", selectedBody);
        tag.putInt("zoom", zoomBody);
        tag.putInt("origin", origin);
        tag.putDouble("tedit", editTime);
        tag.putBoolean("detached", true);
        CompoundTag frame = new CompoundTag();
        frame.putDouble("x", frameX);
        frame.putDouble("y", frameY);
        frame.putDouble("z", frameZ);
        frame.putDouble("h", frameHalf);
        tag.put("frame", frame);
    }

    /** Lecture tolerante : mode inconnu = galaxie, cadrage invalide = recadrage a la premiere copie. */
    @Override
    public void load(CompoundTag tag)
    {
        mode = Mode.SYSTEM.name().equals(tag.getString("mode")) ? Mode.SYSTEM : Mode.GALAXY;
        selectedSystem = Math.max(0, tag.getInt("system"));
        selectedBody = Math.max(0, tag.getInt("body"));
        zoomBody = Math.max(0, tag.getInt("zoom"));
        origin = Math.max(0, tag.getInt("origin"));
        editTime = tag.getDouble("tedit");
        if (!Double.isFinite(editTime)) editTime = 0.0;
        // Sauvegarde d'avant T24 : fenetre replacee une fois au premier tick serveur.
        windowDetached = tag.getBoolean("detached");
        if (selectedSystem == 0) mode = Mode.GALAXY;
        if (mode != Mode.SYSTEM) zoomBody = 0;
        CompoundTag frame = tag.getCompound("frame");
        frameX = frame.getDouble("x");
        frameY = frame.getDouble("y");
        frameZ = frame.getDouble("z");
        frameHalf = frame.contains("h", Tag.TAG_DOUBLE) ? frame.getDouble("h") : 0.0;
        if (!validFrame(frameX, frameY, frameZ, frameHalf)) {
            frameX = frameY = frameZ = 0.0;
            frameHalf = 0.0;
        }
    }

    private static boolean validFrame(double x, double y, double z, double half)
    {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Double.isFinite(half)
                && half >= MIN_HALF_EXTENT;
    }

    // ---------------------------------------------------------------- serveur

    /**
     * Copie de l'Environment si sa revision ou ses reglages ont change, sondage du cube, placement unique de la fenetre
     * d'une ancienne sauvegarde, sondage des poignees, poignees creees / retirees / replacees, acces des operateurs.
     */
    @Override
    public void serverTick(ServerLevel level)
    {
        SpaceEnvironmentModule env = SpaceEnvironmentModule.of(level);
        updateCopy(env);
        pollCube();
        detachWindow();
        pollHandle(env);
        pollOrbit(env);
        updateHandle();
        updateAccess(level.getServer());
    }

    /**
     * Recopie depuis {@code env} si c'est un autre Environment ou si sa revision a change ; relit l'axe avant si
     * l'instance de ses reglages a change. Sinon rien.
     */
    private void updateCopy(SpaceEnvironmentModule env)
    {
        boolean changed = !copied || env != lastEnvironment
                || env != null && env.galaxyRevision() != copiedRevision;
        lastEnvironment = env;
        if (changed) {
            copied = true;
            copy(env);
            markDirty();
        }
        SpaceMotionSettings settings = env != null ? env.settings() : null;
        if (settings == lastSettings) return;
        lastSettings = settings;
        int choice = settings != null ? forwardChoice(settings) : -1;
        double warp = settings != null ? settings.warpSpeed() : SpaceMotionSettings.DEFAULT_WARP_SPEED;
        if (choice != forward || warp != warpSpeed) {
            forward = choice;
            warpSpeed = warp;
            markDirty();
        }
    }

    /** Indice du choix d'axe avant correspondant aux reglages, {@code -1} si aucun (haut / bas : yaw ignore). */
    private static int forwardChoice(SpaceMotionSettings settings)
    {
        for (int i = 0; i < FORWARD_YAW.length; i++) {
            if (Math.abs(settings.forwardPitch() - FORWARD_PITCH[i]) > FORWARD_EPSILON) continue;
            boolean vertical = Math.abs(FORWARD_PITCH[i]) >= SpaceMotionSettings.MAX_FORWARD_PITCH;
            if (vertical || Math.abs(Mth.wrapDegrees(settings.forwardYaw() - FORWARD_YAW[i])) <= FORWARD_EPSILON) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Copie des en-tetes de tous les systemes, du systeme courant et du systeme edite (mode systeme). Selection
     * invalide (systeme retire) : plus de selection, retour au mode galaxie. Sans Environment, la selection et le mode
     * sont gardes (il peut revenir, ou ne pas etre encore charge).
     */
    private void copy(SpaceEnvironmentModule env)
    {
        if (env == null) {
            environment = false;
            copiedRevision = 0;
            currentSystemId = 0;
            headers = List.of();
            editedSystem = null;
            forward = -1;
            warpSpeed = SpaceMotionSettings.DEFAULT_WARP_SPEED;
            lastSettings = null;
            return;
        }
        environment = true;
        copiedRevision = env.galaxyRevision();
        currentSystemId = env.currentSystemId();
        List<SpaceSystem> systems = env.galaxy();
        List<SystemHeader> list = new ArrayList<>(systems.size());
        for (SpaceSystem system : systems) list.add(system.header());
        headers = List.copyOf(list);
        if (selectedSystem != 0 && env.system(selectedSystem) == null) selectedSystem = 0;
        // Origine retiree : retour au defaut (Sol, sinon le premier).
        if (origin != 0 && env.system(origin) == null) origin = 0;
        if (selectedSystem == 0) mode = Mode.GALAXY;
        editedSystem = mode == Mode.SYSTEM ? env.system(selectedSystem) : null;
        if (editedSystem == null || editedSystem.body(selectedBody) == null) selectedBody = 0;
        if (editedSystem == null || editedSystem.body(zoomBody) == null) zoomBody = 0;
        if (frameHalf <= 0.0) reframe();
    }

    /**
     * Sondage du cube : a chaque changement de son transform (edition d'un joueur, RESET), echelle uniformisee
     * (moyenne des trois axes, C11). Le transform modifie est envoye par le framework au tick suivant. La fenetre ne
     * suit plus le cube (T24).
     */
    private void pollCube()
    {
        HoloTransform t = cube.transform();
        if (cubeKnown && sameTransform(t, lastCube)) return;
        float mean = meanScale(t);
        if (!uniform(t, mean)) t.setScale(mean);
        lastCube.set(t);
        cubeKnown = true;
    }

    /**
     * Ancienne sauvegarde (d'avant T24) : la fenetre est replacee une fois devant la base du cube charge (apres
     * {@link #pollCube}, echelle uniformisee), puis le module n'y touche plus ; la marque {@code detached} est ecrite a
     * la prochaine sauvegarde.
     */
    private void detachWindow()
    {
        if (windowDetached) return;
        windowDetached = true;
        windowPlacement(cube.transform(), window.transform());
        markDirty();
    }

    /** Acces de la fenetre : operateurs connectes ; reaffecte seulement si l'ensemble change. */
    private void updateAccess(MinecraftServer server)
    {
        ops.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(OP_LEVEL)) ops.add(player.getUUID());
        }
        HoloAccess access = window.access();
        Set<UUID> granted = access.mode() == HoloAccess.Mode.PLAYERS ? access.players() : Set.of();
        if (!granted.equals(ops)) window.setAccess(HoloAccess.players(ops));
    }

    /**
     * Operateurs seulement (verifie en premier, refus silencieux), source = la fenetre ; puis contenu valide contre
     * l'Environment de la dimension du joueur.
     */
    @Override
    public void handleAction(ServerPlayer player, HoloObject source, FriendlyByteBuf buf)
    {
        if (!player.hasPermissions(OP_LEVEL)) return;
        if (source == null || source != window) return;
        int action = buf.readByte();
        SpaceEnvironmentModule env = SpaceEnvironmentModule.of(player.serverLevel());
        updateCopy(env);
        switch (action) {
            case ACTION_SELECT_SYSTEM -> selectSystem(player, env, buf.readVarInt());
            case ACTION_MODE -> {
                int ordinal = buf.readByte();
                if (ordinal >= 0 && ordinal < Mode.values().length) setMode(player, env, Mode.values()[ordinal]);
            }
            case ACTION_REFRAME -> {
                reframe();
                markDirty();
            }
            case ACTION_ADD -> addSystem(player, env);
            case ACTION_REMOVE -> {
                int id = buf.readVarInt();
                if (env != null) report(player, env.removeSystem(id));
                else player.displayClientMessage(NO_ENVIRONMENT, true);
            }
            case ACTION_RENAME -> {
                int id = buf.readVarInt();
                String name = buf.readUtf(MAX_NAME_READ);
                if (env != null) report(player, env.renameSystem(id, name));
                else player.displayClientMessage(NO_ENVIRONMENT, true);
            }
            case ACTION_SET_CURRENT -> {
                int id = buf.readVarInt();
                if (env != null) report(player, env.setCurrentSystem(id));
                else player.displayClientMessage(NO_ENVIRONMENT, true);
            }
            case ACTION_FORWARD -> {
                int index = buf.readByte();
                if (index < 0 || index >= FORWARD_YAW.length) return;
                if (env != null) report(player, env.setForward(FORWARD_YAW[index], FORWARD_PITCH[index]));
                else player.displayClientMessage(NO_ENVIRONMENT, true);
            }
            case ACTION_SELECT_BODY -> selectBody(player, buf.readVarInt());
            case ACTION_ADD_BODY -> {
                int ordinal = buf.readByte();
                int parent = buf.readVarInt();
                SpaceBodyKind[] kinds = SpaceBodyKind.values();
                if (ordinal >= 0 && ordinal < kinds.length) addBody(player, env, kinds[ordinal], parent);
            }
            case ACTION_REMOVE_BODY -> {
                int id = buf.readVarInt();
                SpaceSystem system = systemForEdit(player, env);
                if (system != null) report(player, env.removeBody(system.id(), id));
            }
            case ACTION_UPDATE_BODY -> updateBody(player, env, buf);
            case ACTION_WARP_SPEED -> {
                double value = buf.readDouble();
                if (env != null) report(player, env.setWarpSpeed(value));
                else player.displayClientMessage(NO_ENVIRONMENT, true);
            }
            case ACTION_ZOOM -> zoom(player, buf.readVarInt());
            case ACTION_ORIGIN -> setOrigin(player, env, buf.readVarInt());
            default -> {
            }
        }
        // Effet visible des le prochain envoi : copie, axe avant et poignee a jour.
        updateCopy(env);
        updateHandle();
    }

    /** Origine des coordonnees : systeme {@code id} de l'Environment ({@code 0} = defaut). */
    private void setOrigin(ServerPlayer player, SpaceEnvironmentModule env, int id)
    {
        if (id < 0 || id == origin) return;
        if (id != 0 && (env == null || env.system(id) == null)) {
            player.displayClientMessage(env == null ? NO_ENVIRONMENT : UNKNOWN_SYSTEM, true);
            return;
        }
        origin = id;
        markDirty();
    }

    /** « Ajouter » : systeme au nom par defaut, au centre du cadrage, puis selectionne. */
    private void addSystem(ServerPlayer player, SpaceEnvironmentModule env)
    {
        if (env == null) {
            player.displayClientMessage(NO_ENVIRONMENT, true);
            return;
        }
        SpaceEditResult result = env.addSystem("", frameX, frameY, frameZ);
        if (!report(player, result)) return;
        updateCopy(env);
        selectedSystem = result.id();
        selectedBody = 0;
        zoomBody = 0;
        editedSystem = mode == Mode.SYSTEM ? env.system(selectedSystem) : null;
        markDirty();
    }

    // ---------------------------------------------------------------- actions du mode systeme (serveur)

    /**
     * Systeme edite pour une action du mode systeme : celui de l'Environment (pas la copie), en mode systeme
     * seulement ; {@code null} (avec un message) sinon.
     */
    private SpaceSystem systemForEdit(ServerPlayer player, SpaceEnvironmentModule env)
    {
        if (env == null) {
            player.displayClientMessage(NO_ENVIRONMENT, true);
            return null;
        }
        SpaceSystem system = mode == Mode.SYSTEM && selectedSystem != 0 ? env.system(selectedSystem) : null;
        if (system == null) player.displayClientMessage(NOT_SYSTEM_MODE, true);
        return system;
    }

    private void selectBody(ServerPlayer player, int id)
    {
        if (id < 0 || id == selectedBody) return;
        if (mode != Mode.SYSTEM || editedSystem == null) {
            player.displayClientMessage(NOT_SYSTEM_MODE, true);
            return;
        }
        if (id != 0 && editedSystem.body(id) == null) {
            player.displayClientMessage(UNKNOWN_BODY, true);
            return;
        }
        selectedBody = id;
        markDirty();
    }

    /**
     * Zoom sur le corps {@code id} du systeme edite ({@code 0} = systeme entier ; T25, D17). La poignee d'orbite est
     * replacee (ou retiree) a l'echelle de la nouvelle vue par {@code updateHandle} a la fin de l'action.
     */
    private void zoom(ServerPlayer player, int id)
    {
        if (id < 0 || id == zoomBody) return;
        if (mode != Mode.SYSTEM || editedSystem == null) {
            player.displayClientMessage(NOT_SYSTEM_MODE, true);
            return;
        }
        if (id != 0 && editedSystem.body(id) == null) {
            player.displayClientMessage(UNKNOWN_BODY, true);
            return;
        }
        zoomBody = id;
        markDirty();
    }

    /** « +Etoile », « +Planete », « +Lune », « +Mineur » : corps aux valeurs par defaut du type, puis selectionne. */
    private void addBody(ServerPlayer player, SpaceEnvironmentModule env, SpaceBodyKind kind, int parent)
    {
        SpaceSystem system = systemForEdit(player, env);
        if (system == null) return;
        if (parent <= 0) {
            player.displayClientMessage(PARENT_REQUIRED, true);
            return;
        }
        SpaceEditResult result = env.addBody(system.id(), kind, parent);
        if (!report(player, result)) return;
        updateCopy(env);
        selectedBody = result.id();
        markDirty();
    }

    /**
     * Modification d'un champ d'un corps du systeme edite ({@link #ACTION_UPDATE_BODY}) : valeur lue, verifiee
     * (finie, champ applicable au corps), corps modifie par le modele (bornes appliquees) puis {@code updateBody}.
     */
    private void updateBody(ServerPlayer player, SpaceEnvironmentModule env, FriendlyByteBuf buf)
    {
        int id = buf.readVarInt();
        int field = buf.readByte();
        String text = null;
        int color = 0;
        double number = Double.NaN;
        switch (field) {
            case FIELD_NAME -> text = buf.readUtf(MAX_NAME_READ);
            case FIELD_COLOR -> color = buf.readInt();
            case FIELD_RADIUS, FIELD_LUMINOSITY, FIELD_PERIOD, FIELD_PHASE -> number = buf.readDouble();
            default -> {
                return;
            }
        }
        SpaceSystem system = systemForEdit(player, env);
        if (system == null) return;
        SpaceBody body = system.body(id);
        if (body == null) {
            player.displayClientMessage(UNKNOWN_BODY, true);
            return;
        }
        if (text == null && field != FIELD_COLOR && !Double.isFinite(number)) {
            player.displayClientMessage(NON_FINITE, true);
            return;
        }
        SpaceBody updated;
        try {
            updated = switch (field) {
                case FIELD_NAME -> body.withName(text);
                case FIELD_RADIUS -> body.withRadius(number);
                case FIELD_COLOR -> body.withColor(color);
                case FIELD_LUMINOSITY -> body.kind() == SpaceBodyKind.STAR ? body.withLuminosity((float) number)
                        : null;
                case FIELD_PERIOD -> body.orbit() != null ? body.withOrbit(body.orbit().withPeriod(number)) : null;
                default -> body.orbit() != null ? body.withOrbit(body.orbit().withPhase(number)) : null;
            };
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(NON_FINITE, true);
            return;
        }
        if (updated == null) {
            player.displayClientMessage(FIELD_UNUSED, true);
            return;
        }
        if (updated.equals(body)) return;
        report(player, env.updateBody(system.id(), updated));
    }

    /** Affiche le motif d'un refus au joueur ; vrai si l'operation a reussi. */
    private static boolean report(ServerPlayer player, SpaceEditResult result)
    {
        if (result.success()) return true;
        if (result.message() != null) player.displayClientMessage(result.message(), true);
        return false;
    }

    private void selectSystem(ServerPlayer player, SpaceEnvironmentModule env, int id)
    {
        if (id < 0 || id == selectedSystem) return;
        if (id != 0 && (env == null || env.system(id) == null)) {
            player.displayClientMessage(env == null ? NO_ENVIRONMENT : UNKNOWN_SYSTEM, true);
            return;
        }
        selectedSystem = id;
        selectedBody = 0;
        zoomBody = 0;
        if (id == 0) mode = Mode.GALAXY;
        editedSystem = mode == Mode.SYSTEM ? env.system(id) : null;
        markDirty();
    }

    private void setMode(ServerPlayer player, SpaceEnvironmentModule env, Mode requested)
    {
        if (requested == mode) return;
        if (requested == Mode.SYSTEM) {
            if (env == null) {
                player.displayClientMessage(NO_ENVIRONMENT, true);
                return;
            }
            SpaceSystem system = selectedSystem == 0 ? null : env.system(selectedSystem);
            if (system == null) {
                player.displayClientMessage(NO_SELECTION, true);
                return;
            }
            editedSystem = system;
            // t_edit : temps simule courant de l'Environment, fige pendant toute l'edition.
            editTime = env.days();
        } else {
            editedSystem = null;
        }
        mode = requested;
        selectedBody = 0;
        zoomBody = 0;
        markDirty();
    }

    /**
     * « Recadrer » ({@code docs/space/04-mathematiques.md} par. 7) : centre = centre de la boite englobante des
     * systemes copies, demi-etendue = {@code nice(1,1 x demi-etendue max)}, au moins {@value #MIN_HALF_EXTENT} al.
     */
    private void reframe()
    {
        if (headers.isEmpty()) {
            frameX = frameY = frameZ = 0.0;
            frameHalf = MIN_HALF_EXTENT;
            return;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (SystemHeader header : headers) {
            minX = Math.min(minX, header.x());
            minY = Math.min(minY, header.y());
            minZ = Math.min(minZ, header.z());
            maxX = Math.max(maxX, header.x());
            maxY = Math.max(maxY, header.y());
            maxZ = Math.max(maxZ, header.z());
        }
        frameX = 0.5 * (minX + maxX);
        frameY = 0.5 * (minY + maxY);
        frameZ = 0.5 * (minZ + maxZ);
        double half = 0.5 * Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        frameHalf = Math.max(MIN_HALF_EXTENT, nice(REFRAME_MARGIN * half));
    }

    /**
     * Plus petite valeur de la forme {@code 1, 2 ou 5 x 10^k} superieure ou egale a {@code value} (graduations,
     * cadrage) ; {@code 1} pour une valeur nulle, negative ou non finie.
     */
    public static double nice(double value)
    {
        if (!(value > 0.0) || !Double.isFinite(value)) return 1.0;
        double magnitude = Math.pow(10.0, Math.floor(Math.log10(value)));
        double n = value / magnitude;
        double step = n <= 1.0 + 1e-9 ? 1.0 : n <= 2.0 + 1e-9 ? 2.0 : n <= 5.0 + 1e-9 ? 5.0 : 10.0;
        return step * magnitude;
    }

    // ---------------------------------------------------------------- placement initial de la fenetre (deux cotes)

    /**
     * Placement de la fenetre devant la base du cube {@code t}, ecrit dans {@code dest} (translation et rotation ;
     * echelle inchangee) : tournee comme le cube en lacet seulement (sa face {@code +Z} regarde dans la direction avant
     * du cube {@code f = (-sin yaw, 0, cos yaw)}), a {@value #WINDOW_GAP} bloc de l'enveloppe du cube dans cette
     * direction, bord bas au niveau du point le plus bas du cube. Les demi-etendues de l'enveloppe tiennent compte de
     * la rotation complete du cube (somme des projections de ses trois demi-axes) et de son echelle moyenne. Utilise
     * seulement pour le placement initial (cube par defaut) et une ancienne sauvegarde (T24).
     */
    private void windowPlacement(HoloTransform t, HoloTransform dest)
    {
        HoloEditMath.rotation(rotation, t);
        float scale = meanScale(t) * SpaceMapCube.HALF_SIZE;
        float yaw = t.yaw() * Mth.DEG_TO_RAD;
        float fx = -Mth.sin(yaw);
        float fz = Mth.cos(yaw);
        float vertical = 0f;
        float front = 0f;
        for (int i = 0; i < 3; i++) {
            axis.set(i == 0 ? 1f : 0f, i == 1 ? 1f : 0f, i == 2 ? 1f : 0f);
            rotation.transform(axis);
            vertical += Math.abs(axis.y);
            front += Math.abs(axis.x * fx + axis.z * fz);
        }
        vertical *= scale;
        front *= scale;
        float distance = front + WINDOW_GAP;
        dest.setTranslation(t.x() + fx * distance, t.y() - vertical + SpaceConfiguratorWindow.HALF_HEIGHT,
                t.z() + fz * distance);
        dest.setRotation(t.yaw(), 0f, 0f);
    }

    /**
     * Client : temps de l'animation ; poignees replacees sur le cube localement, y compris pendant une manipulation
     * (rien n'est envoye). La fenetre ne suit pas le cube (T24).
     */
    @Override
    public void clientTick()
    {
        if (animating) animationTicks++;
        if (cube == null) return;
        HoloTransform t = cube.transform();
        if (cubeKnown && sameTransform(t, lastCube)) return;
        placeHandle();
        placeOrbit();
        lastCube.set(t);
        cubeKnown = true;
    }

    // ---------------------------------------------------------------- poignee (deux cotes)

    /** Vrai si l'etat demande la poignee : mode galaxie et systeme selectionne present dans la copie. */
    private boolean handleWanted() { return mode == Mode.GALAXY && selectedHeader() != null; }

    /**
     * Cree ou retire les poignees selon l'etat (seulement si absentes / presentes : {@code addObject} leve sur une cle
     * deja utilisee), puis les replace si leur placement voulu a change.
     */
    private void updateHandle()
    {
        if (cube == null) return;
        updateOrbitHandle();
        boolean wanted = handleWanted();
        if (wanted && handle == null) {
            handle = object(SpaceMapHandle.KEY) instanceof SpaceMapHandle existing ? existing
                    : addObject(new SpaceMapHandle());
            handleWritten = false;
        } else if (!wanted && handle != null) {
            removeObject(SpaceMapHandle.KEY);
            handle = null;
            handleWritten = false;
        }
        placeHandle();
    }

    /**
     * Ecrit dans la poignee la position de l'etoile selectionnee (bornee au cube) si elle differe de la derniere
     * ecrite, et la retient. Repere du marker : {@code P = t + R . (sigma (.) q)} (doc 04 par. 7).
     */
    private void placeHandle()
    {
        if (handle == null || !handlePoint(point)) return;
        if (handleWritten && near(point, lastHandle)) return;
        handle.transform().setTranslation(point.x, point.y, point.z);
        lastHandle.set(point);
        handleWritten = true;
    }

    /** Position voulue de la poignee (repere du marker) ; faux sans systeme selectionne dans la copie. */
    private boolean handlePoint(Vector3f dest)
    {
        SystemHeader header = selectedHeader();
        if (header == null) return false;
        double half = frameHalf > 0.0 ? frameHalf : MIN_HALF_EXTENT;
        HoloTransform t = cube.transform();
        float h = SpaceMapCube.HALF_SIZE;
        dest.set(clampUnit((header.x() - frameX) / half) * h * t.scaleX(),
                clampUnit((header.y() - frameY) / half) * h * t.scaleY(),
                clampUnit((header.z() - frameZ) / half) * h * t.scaleZ());
        HoloEditMath.rotation(rotation, t).transform(dest);
        dest.add(t.x(), t.y(), t.z());
        return true;
    }

    /**
     * Serveur : la poignee a ete deplacee par un joueur (ecart a la derniere position ecrite) : position convertie en
     * unites du cube {@code q = (R^-1 . (P - t)) / sigma}, bornee a {@code [-1, 1]^3}, puis {@code g = c + q . H} et
     * {@code moveSystem}. La poignee est ensuite reecrite a la position du systeme (bornee).
     */
    private void pollHandle(SpaceEnvironmentModule env)
    {
        if (handle == null || !handleWritten) return;
        HoloTransform p = handle.transform();
        point.set(p.x(), p.y(), p.z());
        if (near(point, lastHandle)) return;
        handleWritten = false;
        SystemHeader header = selectedHeader();
        if (env == null || header == null) return;
        HoloTransform t = cube.transform();
        point.sub(t.x(), t.y(), t.z());
        HoloEditMath.rotation(rotation, t).conjugate().transform(point);
        float h = SpaceMapCube.HALF_SIZE;
        double half = frameHalf > 0.0 ? frameHalf : MIN_HALF_EXTENT;
        double qx = clampUnit(point.x / (h * Math.max(1e-6f, t.scaleX())));
        double qy = clampUnit(point.y / (h * Math.max(1e-6f, t.scaleY())));
        double qz = clampUnit(point.z / (h * Math.max(1e-6f, t.scaleZ())));
        env.moveSystem(header.id(), frameX + qx * half, frameY + qy * half, frameZ + qz * half);
        updateCopy(env);
    }

    // ---------------------------------------------------------------- poignee d'orbite (deux cotes)

    /**
     * Corps selectionne s'il a une orbite et que la vue du mode systeme la montre a l'echelle (sans zoom, ou parent
     * dans le voisinage du corps zoome : {@link SpaceSystemScale#orbitInView}) et assez grande pour une poignee
     * ({@link #MIN_HANDLE_AXIS}) ; {@code null} sinon.
     */
    private SpaceBody orbitBody()
    {
        if (mode != Mode.SYSTEM || editedSystem == null || selectedBody == 0) return null;
        SpaceBody body = editedSystem.body(selectedBody);
        if (body == null || body.orbit() == null || body.isRoot()) return null;
        ensureScale();
        if (body.orbit().semiMinorAxis() / viewHalf * SpaceMapCube.HALF_SIZE < MIN_HANDLE_AXIS) return null;
        return SpaceSystemScale.orbitInView(editedSystem, zoomIndex, editedSystem.indexOf(body.id())) ? body : null;
    }

    /** Cree ou retire la poignee d'orbite selon l'etat, puis la replace si son placement voulu a change. */
    private void updateOrbitHandle()
    {
        boolean wanted = orbitBody() != null;
        if (wanted && orbitHandle == null) {
            orbitHandle = object(SpaceOrbitHandle.KEY) instanceof SpaceOrbitHandle existing ? existing
                    : addObject(new SpaceOrbitHandle());
            orbitWritten = false;
        } else if (!wanted && orbitHandle != null) {
            removeObject(SpaceOrbitHandle.KEY);
            orbitHandle = null;
            orbitWritten = false;
        }
        placeOrbit();
    }

    /** Ecrit dans la poignee d'orbite son placement voulu s'il differe du dernier ecrit, et le retient. */
    private void placeOrbit()
    {
        if (orbitHandle == null || !orbitTransform(wantedOrbit)) return;
        if (orbitWritten && sameTransform(wantedOrbit, lastOrbit)) return;
        orbitHandle.transform().set(wantedOrbit);
        lastOrbit.set(wantedOrbit);
        orbitWritten = true;
    }

    /**
     * Placement voulu de la poignee d'orbite ({@code docs/space/04-mathematiques.md} par. 7) : translation = parent
     * affiche a {@code t_edit} dans la vue courante ({@code P = t + R . (sigma (.) q)}), rotation =
     * {@code R_cube . R_orbite}, echelle {@code (a_aff . sigma, a_aff . sigma, b_aff . sigma)} ({@code a_aff = a / H},
     * {@code H} de la vue). Faux sans corps a orbite selectionne (ou hors du voisinage du zoom).
     */
    private boolean orbitTransform(HoloTransform dest)
    {
        SpaceBody body = orbitBody();
        if (body == null || cube == null) return false;
        ensureScale();
        SpaceSystem system = editedSystem;
        int parent = system.parentIndex(system.indexOf(body.id()));
        if (parent < 0) return false;
        SpaceSystemScale.positions(system, editTime, viewHalf, zoomIndex, bodyPoints);
        Vector3d q = bodyPoints[parent];
        HoloTransform t = cube.transform();
        point.set((float) q.x * t.scaleX(), (float) q.y * t.scaleY(), (float) q.z * t.scaleZ());
        HoloEditMath.rotation(rotation, t).transform(point);
        dest.setTranslation(t.x() + point.x, t.y() + point.y, t.z() + point.z);
        SpaceOrbit orbit = body.orbit();
        orbitRotation.rotationYXZ(-(float) orbit.yaw() * Mth.DEG_TO_RAD, (float) orbit.pitch() * Mth.DEG_TO_RAD,
                (float) orbit.roll() * Mth.DEG_TO_RAD);
        setRotationYXZ(dest, rotation.mul(orbitRotation));
        double unit = SpaceMapCube.HALF_SIZE / viewHalf * meanScale(t);
        float scaleA = orbitScale(orbit.semiMajorAxis() * unit);
        dest.setScale(scaleA, scaleA, orbitScale(orbit.semiMinorAxis() * unit));
        return true;
    }

    private static float orbitScale(double value)
    {
        return (float) Math.max(MIN_ORBIT_SCALE, Math.min(HoloTransform.MAX_SCALE, value));
    }

    /**
     * Serveur : la poignee d'orbite a ete editee par un joueur (rotation ou echelle {@code X} / {@code Z} differente de
     * la derniere ecrite) : orbite recalculee (doc 04 par. 7, seulement les composantes changees) puis
     * {@code updateBody}. Tout autre ecart (echelle {@code Y}, translation) force seulement une reecriture.
     */
    private void pollOrbit(SpaceEnvironmentModule env)
    {
        if (orbitHandle == null || !orbitWritten) return;
        HoloTransform h = orbitHandle.transform();
        boolean rotated = !sameAngles(h, lastOrbit);
        boolean shaped = !sameScale(h.scaleX(), lastOrbit.scaleX()) || !sameScale(h.scaleZ(), lastOrbit.scaleZ());
        if (!rotated && !shaped) {
            if (!sameTransform(h, lastOrbit)) orbitWritten = false;
            return;
        }
        orbitWritten = false;
        SpaceBody body = orbitBody();
        if (env == null || body == null) return;
        ensureScale();
        SpaceOrbit orbit = body.orbit();
        if (rotated) {
            // R_o = R_cube^-1 . R_poignee
            HoloEditMath.rotation(orbitRotation, h);
            HoloEditMath.rotation(rotation, cube.transform()).conjugate().mul(orbitRotation);
            setRotationYXZ(orbitScratch, rotation);
            orbit = orbit.withOrientation(orbitScratch.yaw(), orbitScratch.pitch(), orbitScratch.roll());
        }
        if (shaped) {
            double unit = SpaceMapCube.HALF_SIZE / viewHalf * meanScale(cube.transform());
            double a = h.scaleX() / unit;
            double b = h.scaleZ() / unit;
            double e = 0.0;
            double ratio = b / a;
            double before = (double) lastOrbit.scaleZ() / lastOrbit.scaleX();
            if (Math.abs(ratio / before - 1.0) <= RATIO_EPSILON) {
                // Echelle uniforme (outil synchronise) : e inchangee, sans bruit d'arrondi (orbite circulaire gardee).
                e = orbit.eccentricity();
            } else if (b < a) {
                e = Math.min(SpaceLimits.MAX_ECCENTRICITY, Math.sqrt(1.0 - ratio * ratio));
            }
            if (!SpaceLimits.finite(a, e)) return;
            double previousA = orbit.semiMajorAxis();
            orbit = orbit.withShape(a, e);
            // 3e loi de Kepler : la periode suit le demi-grand axe (P proportionnelle a a^1,5), bornee par le modele.
            double period = orbit.period() * Math.pow(orbit.semiMajorAxis() / previousA, 1.5);
            if (Double.isFinite(period)) orbit = orbit.withPeriod(period);
        }
        try {
            env.updateBody(editedSystem.id(), body.withOrbit(orbit));
        } catch (IllegalArgumentException ignored) {
            return;
        }
        updateCopy(env);
    }

    /**
     * Pose la rotation {@code q} dans {@code dest} en (yaw, pitch, roll) selon la convention de
     * {@code HoloEditMath.rotation} (YXZ, {@code -yaw}), calculee en {@code double} depuis la matrice. Pres du blocage
     * de cardan (pitch a moins de 0,06 degre de +/- 90), le roulis est mis a 0 et le lacet porte toute la rotation
     * autour de la verticale : la decomposition de JOML ({@code getEulerAnglesYXZ}, utilisee par
     * {@code HoloEditMath.setRotation}) y perd jusqu'a 180 degres en {@code float} (aller-retour mesure : 0,1 degre au
     * pire ici). Sans allocation.
     */
    private static void setRotationYXZ(HoloTransform dest, Quaternionf q)
    {
        double x = q.x;
        double y = q.y;
        double z = q.z;
        double w = q.w;
        double r02 = 2.0 * (x * z + w * y);
        double r22 = 1.0 - 2.0 * (x * x + y * y);
        double r12 = 2.0 * (y * z - w * x);
        double cosPitch = Math.sqrt(r02 * r02 + r22 * r22);
        double pitch = Math.atan2(-r12, cosPitch);
        double yaw;
        double roll;
        if (cosPitch > GIMBAL_EPSILON) {
            yaw = Math.atan2(r02, r22);
            roll = Math.atan2(2.0 * (x * y + w * z), 1.0 - 2.0 * (x * x + z * z));
        } else {
            yaw = Math.atan2(-2.0 * (x * z - w * y), 1.0 - 2.0 * (y * y + z * z));
            roll = 0.0;
        }
        dest.setRotation((float) -Math.toDegrees(yaw), (float) Math.toDegrees(pitch), (float) Math.toDegrees(roll));
    }

    /** Memes angles a {@value #ORBIT_ANGLE_EPSILON} degre pres (ecart ramene a {@code [-180, 180]}). */
    private static boolean sameAngles(HoloTransform a, HoloTransform b)
    {
        return Math.abs(Mth.wrapDegrees(a.yaw() - b.yaw())) <= ORBIT_ANGLE_EPSILON
                && Math.abs(Mth.wrapDegrees(a.pitch() - b.pitch())) <= ORBIT_ANGLE_EPSILON
                && Math.abs(Mth.wrapDegrees(a.roll() - b.roll())) <= ORBIT_ANGLE_EPSILON;
    }

    private static boolean sameScale(float a, float b)
    {
        return Math.abs(a - b) <= ORBIT_SCALE_EPSILON * Math.max(Math.abs(a), Math.abs(b));
    }

    /**
     * Echelle de la vue du systeme edite (indice du corps zoome, demi-etendue), recalculee seulement quand la copie
     * ou le zoom change.
     */
    private void ensureScale()
    {
        if (editedSystem == scaledSystem && zoomBody == scaledZoom) return;
        scaledSystem = editedSystem;
        scaledZoom = zoomBody;
        if (editedSystem == null) {
            zoomIndex = -1;
            viewHalf = 1.0;
            return;
        }
        zoomIndex = zoomBody == 0 ? -1 : editedSystem.indexOf(zoomBody);
        viewHalf = SpaceSystemScale.viewHalf(editedSystem, zoomIndex);
    }

    private static float clampUnit(double value)
    {
        return (float) Math.max(-1.0, Math.min(1.0, value));
    }

    private static boolean near(Vector3f a, Vector3f b)
    {
        return Math.abs(a.x - b.x) <= HANDLE_EPSILON && Math.abs(a.y - b.y) <= HANDLE_EPSILON
                && Math.abs(a.z - b.z) <= HANDLE_EPSILON;
    }

    private static float meanScale(HoloTransform t) { return (t.scaleX() + t.scaleY() + t.scaleZ()) / 3f; }

    private static boolean uniform(HoloTransform t, float mean)
    {
        float tolerance = UNIFORM_EPSILON * mean;
        return Math.abs(t.scaleX() - mean) <= tolerance && Math.abs(t.scaleY() - mean) <= tolerance
                && Math.abs(t.scaleZ() - mean) <= tolerance;
    }

    /** Egalite exacte des neuf composantes. */
    private static boolean sameTransform(HoloTransform a, HoloTransform b)
    {
        return a.x() == b.x() && a.y() == b.y() && a.z() == b.z() && a.yaw() == b.yaw() && a.pitch() == b.pitch()
                && a.roll() == b.roll() && a.scaleX() == b.scaleX() && a.scaleY() == b.scaleY()
                && a.scaleZ() == b.scaleZ();
    }

    // ---------------------------------------------------------------- demandes du client

    /** Client : selectionne un systeme ({@code 0} = aucun). */
    void requestSelectSystem(int id) { sendTyped(ACTION_SELECT_SYSTEM, buf -> buf.writeVarInt(id)); }

    /** Client : change de mode (le serveur refuse le mode systeme sans selection). */
    void requestMode(Mode requested) { sendTyped(ACTION_MODE, buf -> buf.writeByte(requested.ordinal())); }

    /** Client : « Recadrer ». */
    void requestReframe() { sendTyped(ACTION_REFRAME, null); }

    /** Client : « Ajouter » (au centre du cadrage, selectionne ensuite). */
    void requestAdd() { sendTyped(ACTION_ADD, null); }

    /** Client : « Retirer » le systeme {@code id}. */
    void requestRemove(int id) { sendTyped(ACTION_REMOVE, buf -> buf.writeVarInt(id)); }

    /** Client : renomme le systeme {@code id}. */
    void requestRename(int id, String name)
    {
        String clean = SpaceLimits.sanitizeName(name);
        sendTyped(ACTION_RENAME, buf -> {
            buf.writeVarInt(id);
            buf.writeUtf(clean, MAX_NAME_READ);
        });
    }

    /** Client : « Monde ici » sur le systeme {@code id}. */
    void requestSetCurrent(int id) { sendTyped(ACTION_SET_CURRENT, buf -> buf.writeVarInt(id)); }

    /** Client : « Avant du monde » = choix {@code index} de {@link #FORWARD_YAW}. */
    void requestForward(int index) { sendTyped(ACTION_FORWARD, buf -> buf.writeByte(index)); }

    /** Client : selectionne un corps du systeme edite ({@code 0} = aucun). */
    void requestSelectBody(int id) { sendTyped(ACTION_SELECT_BODY, buf -> buf.writeVarInt(id)); }

    /** Client : ajoute un corps de type {@code kind} autour de {@code parentId} (selectionne ensuite). */
    void requestAddBody(SpaceBodyKind kind, int parentId)
    {
        sendTyped(ACTION_ADD_BODY, buf -> {
            buf.writeByte(kind.ordinal());
            buf.writeVarInt(parentId);
        });
    }

    /** Client : retire le corps {@code id} et ses descendants. */
    void requestRemoveBody(int id) { sendTyped(ACTION_REMOVE_BODY, buf -> buf.writeVarInt(id)); }

    /** Client : renomme le corps {@code id}. */
    void requestBodyName(int id, String name)
    {
        String clean = SpaceLimits.sanitizeName(name);
        sendUpdate(id, FIELD_NAME, buf -> buf.writeUtf(clean, MAX_NAME_READ));
    }

    /** Client : couleur du corps {@code id}. */
    void requestBodyColor(int id, int color) { sendUpdate(id, FIELD_COLOR, buf -> buf.writeInt(color)); }

    /**
     * Client : champ numerique ({@link #FIELD_RADIUS}, {@link #FIELD_LUMINOSITY}, {@link #FIELD_PERIOD},
     * {@link #FIELD_PHASE}).
     */
    void requestBodyNumber(int id, int field, double value) { sendUpdate(id, field, buf -> buf.writeDouble(value)); }

    /** Client : vitesse de distorsion du saut, al/s (bornee par l'Environment). */
    void requestWarpSpeed(double value) { sendTyped(ACTION_WARP_SPEED, buf -> buf.writeDouble(value)); }

    /** Client : zoom sur le corps {@code id} du systeme edite ({@code 0} = systeme entier). */
    void requestZoom(int id) { sendTyped(ACTION_ZOOM, buf -> buf.writeVarInt(id)); }

    /** Client : « Definir comme origine » le systeme {@code id} ({@code 0} = defaut). */
    void requestOrigin(int id) { sendTyped(ACTION_ORIGIN, buf -> buf.writeVarInt(id)); }

    private void sendUpdate(int id, int field, Consumer<FriendlyByteBuf> value)
    {
        sendTyped(ACTION_UPDATE_BODY, buf -> {
            buf.writeVarInt(id);
            buf.writeByte(field);
            value.accept(buf);
        });
    }

    /**
     * Client : « Animer » (mode systeme seulement). Le temps d'affichage repart de {@code t_edit} a chaque
     * basculement : a l'arret, le cube revient a l'etat edite (celui de la poignee d'orbite).
     */
    void setAnimating(boolean on)
    {
        animating = on && mode == Mode.SYSTEM;
        animationTicks = 0L;
        // Coordonnees de la ligne d'information (temps affiche) remises a jour au basculement.
        refresh();
    }

    private void sendTyped(int action, Consumer<FriendlyByteBuf> content)
    {
        if (window == null) return;
        sendAction(window, buf -> {
            buf.writeByte(action);
            if (content != null) content.accept(buf);
        });
    }

    // ---------------------------------------------------------------- synchronisation

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeByte(mode.ordinal());
        buf.writeVarInt(selectedSystem);
        buf.writeVarInt(selectedBody);
        buf.writeVarInt(zoomBody);
        buf.writeVarInt(origin);
        buf.writeDouble(editTime);
        buf.writeDouble(frameX);
        buf.writeDouble(frameY);
        buf.writeDouble(frameZ);
        buf.writeDouble(frameHalf);
        buf.writeBoolean(environment);
        if (!environment) return;
        buf.writeInt(copiedRevision);
        buf.writeVarInt(currentSystemId);
        buf.writeByte(forward);
        buf.writeDouble(warpSpeed);
        SystemHeader.writeList(buf, headers);
        buf.writeBoolean(editedSystem != null);
        if (editedSystem != null) editedSystem.write(buf);
    }

    /** Lecture stricte (en-tetes et systeme : leve sur une valeur invalide) ; mode inconnu = galaxie. */
    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        int ordinal = buf.readByte();
        Mode newMode = ordinal >= 0 && ordinal < Mode.values().length ? Mode.values()[ordinal] : Mode.GALAXY;
        int newSystem = Math.max(0, buf.readVarInt());
        int newBody = Math.max(0, buf.readVarInt());
        int newZoom = Math.max(0, buf.readVarInt());
        int newOrigin = Math.max(0, buf.readVarInt());
        double newEditTime = buf.readDouble();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        double half = buf.readDouble();
        boolean newEnvironment = buf.readBoolean();
        int revision = 0;
        int current = 0;
        int newForward = -1;
        double newWarp = SpaceMotionSettings.DEFAULT_WARP_SPEED;
        List<SystemHeader> newHeaders = List.of();
        SpaceSystem edited = null;
        if (newEnvironment) {
            revision = buf.readInt();
            current = buf.readVarInt();
            newForward = buf.readByte();
            if (newForward < -1 || newForward >= FORWARD_YAW.length) newForward = -1;
            double warp = buf.readDouble();
            if (Double.isFinite(warp)) newWarp = SpaceLimits.sanitizeWarpSpeed(warp);
            newHeaders = SystemHeader.readList(buf);
            if (buf.readBoolean()) edited = SpaceSystem.read(buf);
        }
        mode = newMode;
        selectedSystem = newSystem;
        selectedBody = newBody;
        zoomBody = newZoom;
        origin = newOrigin;
        editTime = Double.isFinite(newEditTime) ? newEditTime : 0.0;
        if (validFrame(x, y, z, half)) {
            frameX = x;
            frameY = y;
            frameZ = z;
            frameHalf = half;
        } else {
            frameX = frameY = frameZ = 0.0;
            frameHalf = MIN_HALF_EXTENT;
        }
        environment = newEnvironment;
        copiedRevision = revision;
        currentSystemId = current;
        forward = newForward;
        warpSpeed = newWarp;
        headers = newHeaders;
        editedSystem = edited;
    }

    /**
     * Client : poignees creees / retirees / replacees selon l'etat recu, fenetre mise a jour, animation arretee hors
     * du mode systeme.
     */
    @Override
    public void onSync()
    {
        if (mode != Mode.SYSTEM) setAnimating(false);
        updateHandle();
        refresh();
    }

    private void refresh()
    {
        if (window != null) window.showState();
    }

    // ---------------------------------------------------------------- lecture (renderers, T10 a T12)

    /** Cube (deux cotes). */
    public SpaceMapCube cube() { return cube; }

    /** Fenetre (deux cotes). */
    public SpaceConfiguratorWindow window() { return window; }

    /** Poignee de l'etoile selectionnee (deux cotes), {@code null} si elle n'existe pas. */
    public SpaceMapHandle handle() { return handle; }

    /** Poignee d'orbite du corps selectionne (deux cotes), {@code null} si elle n'existe pas. */
    public SpaceOrbitHandle orbitHandle() { return orbitHandle; }

    /** Temps simule fige du mode systeme {@code t_edit}, jours. */
    public double editTime() { return editTime; }

    /** Vrai si « Animer » est actif (client). */
    public boolean animating() { return animating; }

    /**
     * Temps d'affichage du mode systeme, en jours (client) : {@code t_edit}, plus le temps ecoule depuis le debut de
     * l'animation a l'echelle de temps de l'Environment (defaut si absent ou nulle). Meme valeur pour tous les appels
     * d'une frame. Sans allocation.
     */
    public double displayDays(float partialTick)
    {
        if (!animating) return editTime;
        SpaceEnvironmentModule env = SpaceEnvironmentModule.client();
        double scale = env != null ? env.settings().timeScale() : 0.0;
        if (!(scale > 0.0)) scale = SpaceMotionSettings.DEFAULT_TIME_SCALE;
        return editTime + (animationTicks + partialTick) * scale / SpaceUnits.SECONDS_PER_DAY;
    }

    /**
     * Demi-etendue de la vue du mode systeme, UA ({@link SpaceSystemScale}) : {@code H_sys} du systeme entier, ou
     * {@code H_zoom} du corps zoome ; {@code 1} sans systeme.
     */
    public double viewHalf()
    {
        ensureScale();
        return viewHalf;
    }

    /** Indice du corps zoome dans le systeme edite (copie), {@code -1} = systeme entier ou pas de systeme. */
    public int zoomIndex()
    {
        ensureScale();
        return zoomIndex;
    }

    /** Id du corps zoome (mode systeme, T25), {@code 0} = systeme entier. */
    public int zoomBody() { return zoomBody; }

    /** Corps zoome dans le systeme edite (copie), {@code null} sans zoom. Sans allocation. */
    public SpaceBody zoomBodyData()
    {
        return editedSystem == null || zoomBody == 0 ? null : editedSystem.body(zoomBody);
    }

    /** Corps selectionne dans le systeme edite (copie), {@code null} si aucun. Sans allocation. */
    public SpaceBody selectedBodyData()
    {
        return editedSystem == null || selectedBody == 0 ? null : editedSystem.body(selectedBody);
    }

    /** Id de l'origine des coordonnees choisie (T26), {@code 0} = defaut ; voir {@link #originHeader()}. */
    public int origin() { return origin; }

    /**
     * Systeme origine effectif des coordonnees galactiques (copie) : le choisi, sinon {@code Sol}, sinon le premier
     * ({@link SpaceCoordinates#origin}) ; {@code null} sans systeme. Sans allocation.
     */
    public SystemHeader originHeader() { return SpaceCoordinates.origin(headers, origin); }

    /** Choix courant de l'axe avant du monde (indice de {@link #FORWARD_YAW}), {@code -1} = autre ou inconnu. */
    public int forwardChoice() { return forward; }

    /** Vitesse de distorsion du saut de l'Environment (copie), al/s ; defaut sans Environment. */
    public double warpSpeed() { return warpSpeed; }

    /**
     * Vrai si le systeme selectionne est hors du cadrage (la poignee est alors bornee au bord du cube). Sans
     * allocation.
     */
    public boolean selectedOutsideFrame()
    {
        SystemHeader header = selectedHeader();
        if (header == null) return false;
        double half = frameHalf > 0.0 ? frameHalf : MIN_HALF_EXTENT;
        double limit = 1.0 + HANDLE_EPSILON;
        return Math.abs(header.x() - frameX) > limit * half || Math.abs(header.y() - frameY) > limit * half
                || Math.abs(header.z() - frameZ) > limit * half;
    }

    public Mode mode() { return mode; }

    /** Id du systeme selectionne, {@code 0} = aucun. */
    public int selectedSystem() { return selectedSystem; }

    /** Id du corps selectionne (mode systeme), {@code 0} = aucun. */
    public int selectedBody() { return selectedBody; }

    /** Centre du cadrage galactique, al. */
    public double frameX() { return frameX; }

    public double frameY() { return frameY; }

    public double frameZ() { return frameZ; }

    /** Demi-etendue du cadrage galactique, al ({@code q = (g - c) / H}) ; {@code 0} avant la premiere copie. */
    public double frameHalf() { return frameHalf; }

    /** Vrai si la dimension du marker a un Space Environment (copie). */
    public boolean environmentPresent() { return environment; }

    /** Revision de la galaxie de l'Environment deja copiee. */
    public int copiedRevision() { return copiedRevision; }

    /** Id du systeme courant de l'Environment (copie), {@code 0} sans Environment. */
    public int currentSystemId() { return currentSystemId; }

    /** En-tetes de tous les systemes (copie), dans l'ordre de la galaxie ; liste non modifiable. */
    public List<SystemHeader> headers() { return headers; }

    /** Systeme edite (copie complete), en mode systeme seulement ; {@code null} sinon. */
    public SpaceSystem editedSystem() { return editedSystem; }

    /** En-tete du systeme selectionne, {@code null} si aucun ou absent de la copie. Sans allocation. */
    public SystemHeader selectedHeader()
    {
        if (selectedSystem == 0) return null;
        for (int i = 0; i < headers.size(); i++) {
            SystemHeader header = headers.get(i);
            if (header.id() == selectedSystem) return header;
        }
        return null;
    }
}
