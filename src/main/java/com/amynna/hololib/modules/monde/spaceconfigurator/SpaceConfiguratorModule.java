package com.amynna.hololib.modules.monde.spaceconfigurator;

import com.amynna.hololib.holo.edit.HoloEditMath;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAccess;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.modules.dimension.space.SpaceBodyTextures;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceMotionSettings;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceEditResult;
import com.amynna.hololib.modules.dimension.space.model.SpaceGalaxy;
import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import com.amynna.hololib.modules.dimension.space.model.SpaceOrbit;
import com.amynna.hololib.modules.dimension.space.model.SpaceRotation;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem.SystemHeader;
import com.amynna.hololib.modules.dimension.space.model.SpaceTextureRef;
import com.amynna.hololib.modules.dimension.space.texture.SpaceTextureStore;
import com.amynna.hololib.modules.monde.spacemap.SpaceMapCube;
import com.amynna.hololib.modules.monde.spacemap.SpaceMapModule;
import com.amynna.hololib.modules.monde.spacemap.SpaceMapWindow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Module {@code hololib:space_configurator} (memoire de position, marker), reserve aux operateurs : <b>fenetre
 * d'edition</b> ({@link SpaceConfiguratorWindow}) et <b>poignees</b> ({@link SpaceMapHandle}, {@link SpaceOrbitHandle})
 * de la galaxie du Space Environment de la dimension du marker. Depuis T42 (D20, C78), il n'a plus de carte : il se
 * branche sur la <b>Space Map du meme marker</b> ({@link SpaceMapModule#of}), dont la selection est l'objet edite
 * (systeme sur la carte galactique, corps dans la vue systeme) et dont le cube et la vue (centre, zoom, cadrage)
 * portent les poignees. Il ne stocke ni la galaxie ni sa copie (celle de la Map est lue) ; les modifications passent
 * par les methodes serveur de l'Environment ({@code SpaceEditResult} : motif du refus affiche au joueur).
 * <p>
 * <b>Sans Space Map</b> sur le marker (C78) : fenetre affichee avec « Space Map requise sur ce marker », boutons
 * d'edition desactives, aucune poignee, toute action refusee avec ce message.
 * <p>
 * <b>Etat sauvegarde</b> : la marque {@code mapped} seulement (placement, acces, affichage et visibilite de la fenetre
 * le sont par le framework). Une sauvegarde d'avant T42 (sans {@code mapped} : mode, selection, zoom, origine,
 * cadrage, temps fige) est relue sans erreur, ces cles sont ignorees (L22 : la vue est a recreer par la Map) et la
 * fenetre est remise une fois a son placement par defaut au premier tick serveur (elle etait devant l'ancien cube, a
 * la place de la fenetre de la Map).
 * <p>
 * <b>Fenetre</b> : placement par defaut (aussi RESET) a droite de la fenetre de la Space Map placee devant la base du
 * cube par defaut ({@link #defaultWindowTransform}) ; ensuite le module n'y touche plus.
 * <p>
 * <b>Etat synchronise</b> ({@link #writeSync}, envoye au changement seulement, jamais par tick) :
 * <pre>
 * double t_edit (jours, temps fige de la poignee d'orbite), boolean environment
 * si environment : byte axe avant (indice de FORWARD_YAW, -1 = autre valeur), double vitesse de distorsion (al/s),
 *                  double echelle de temps (s simulees / tick)
 * </pre>
 * Les reglages sont relus dans l'Environment quand l'instance de ses reglages change (ils ne changent pas sa revision).
 * <p>
 * <b>Actions</b> ({@code sendAction(window, ...)}), premier octet = type ; le serveur verifie <b>d'abord</b> que le
 * joueur est operateur (refus silencieux sinon, C9), puis que la source est la fenetre, puis la presence de la Space
 * Map (sinon message) et de l'Environment. Les actions portent les ids qu'elles visent (jamais lus dans la selection
 * de la Map au moment de la reception : une selection changee entre-temps par un autre joueur ne detourne pas
 * l'edition) :
 * <pre>
 * -- galaxie --
 * 0 ADD                          -> addSystem(nom par defaut, centre de la carte galactique de la Map), puis la Map le
 *                                   selectionne et le centre
 * 1 REMOVE + varint id           -> removeSystem(id) (refuse pour le dernier)
 * 2 RENAME + varint id + utf     -> renameSystem(id, nom) (nom nettoye par le modele, vide refuse)
 * 3 SET_CURRENT + varint id      -> setCurrentSystem(id) (« Monde ici »)
 * 4 FORWARD + byte indice        -> setForward(FORWARD_YAW[i], FORWARD_PITCH[i]) (« Avant du monde »)
 * -- corps (systeme vise porte par l'action) --
 * 5 ADD_BODY + varint systeme + byte type + varint parent
 *                                -> addBody(systeme, type, parent) (parent requis, compatibilite verifiee par le
 *                                   modele), puis la Map selectionne le nouveau corps (cadrage garde)
 * 6 REMOVE_BODY + varint systeme + varint id
 *                                -> removeBody(systeme, id) (descendants compris ; derniere etoile centrale refusee)
 * 7 UPDATE_BODY + varint systeme + varint id + byte champ + valeur
 *                                -> updateBody(systeme, corps modifie) ; champ : 0 nom (utf), 1 rayon km (double),
 *                                   2 couleur (int ARGB), 3 luminosite (double, etoiles), 4 periode jours (double,
 *                                   corps a orbite), 5 phase degres (double, corps a orbite), 6 masse kg (double, T39 :
 *                                   strictement positive) ; T44 (rotation propre, C71 ; double) : 7 periode de rotation
 *                                   heures (signee, 0 = synchrone), 8 obliquite, 9 noeud du pole, 10 phase de rotation
 *                                   (degres) ; 11 texture (utf : auto ou builtin:id d'une texture integree connue, ou
 *                                   custom:sha1 d'une texture importee stockee pour la dimension, T45) ; valeur non
 *                                   finie ou champ sans objet refuses ; bornes appliquees par le modele ; seul le
 *                                   champ vise change, sur la valeur courante du serveur
 * -- reglages --
 * 8 WARP_SPEED + double          -> setWarpSpeed(al/s) (« Distorsion ») ; non finie refusee, sinon bornee
 * 9 TIME_SCALE + double          -> setTimeScale(s simulees / tick) (« Temps », C119) : toute la galaxie de la
 *                                   dimension, epoque reposee sans saut des astres ; bornee a [0, MAX_TIME_SCALE]
 * -- vue --
 * 10 FRAME_ORBIT + varint systeme + varint id
 *                                -> la Map centre sa vue systeme sur le parent du corps, orbite entiere dans le cube
 *                                   ({@link SpaceMapModule#frameOrbit} ; « Orbite ») : la poignee d'orbite apparait
 * -- corps (D34) --
 * 11 RESET_BODY + varint systeme + varint id
 *                                -> updateBody(systeme, valeurs par defaut) : le corps de memes ids (et de meme type et
 *                                   meme parent) de la galaxie par defaut ({@code SpaceGalaxy.createDefault}, creee une
 *                                   fois) ; sinon un astre neuf de son type (nom, rayon, couleur, luminosite 1, masse
 *                                   estimee, rotation du type, texture auto), orbite actuelle gardee
 * 12 TELEPORT + varint systeme + varint id
 *                                -> env.teleportTo(systeme, id) : vaisseau pres du corps, V = 0, referentiel = le corps,
 *                                   changement de systeme si besoin
 * </pre>
 * Tout refus de l'Environment (ou son absence) est affiche au joueur dans la barre d'action.
 * <p>
 * <b>Poignee de l'etoile</b> ({@link SpaceMapHandle}, cle {@code handle}, C4) : existe si la Map montre la carte
 * galactique avec un systeme selectionne ; posee sur son etoile dans le repere du cube de la Map
 * ({@code q = (g - c) / H} borne a {@code [-1, 1]^3}, puis {@code P = t + R . (sigma (.) q)}). Le module retient la
 * position ecrite, le systeme, le cadrage {@code (c, H)} et le transform du cube de cette ecriture ; un ecart de plus
 * de {@value #HANDLE_EPSILON} bloc dans {@code serverTick} est une edition d'un joueur, convertie avec ces valeurs
 * retenues ({@code q = (R^-1 . (P - t)) / sigma}, borne, {@code g = c + q . H}, {@code moveSystem}). Un deplacement
 * hors du cadrage reste borne au cube (la Map le recadre).
 * <p>
 * <b>Poignee d'orbite</b> ({@link SpaceOrbitHandle}, cle {@code orbit}, C5) : existe si la Map montre un systeme dont
 * le corps selectionne a une orbite affichee a l'echelle de sa vue : demi-petit axe d'au moins
 * {@value #MIN_HANDLE_AXIS} unite du cube, apoapside d'au plus {@value #MAX_HANDLE_REACH} unites (la Map dessine alors
 * l'ellipse entiere, C109), parent affiche a moins de {@value #PARENT_LIMIT} unites du centre. Posee sur le parent
 * affiche au <b>temps fige</b> {@code t_edit} (T42, 4 bis) : la Map suit le temps vivant, mais le serveur relit la
 * poignee et refuse toute edition qui deplace sa translation (outils Rotation et Echelle seulement) ; elle ne peut donc
 * pas suivre le parent tick apres tick sans trafic. {@code t_edit} = {@code days()} de l'Environment, repris
 * seulement quand le contexte change (systeme montre ou sa copie, corps selectionne, centre ou demi-etendue de la vue :
 * selection, zoom, edition) : aucun envoi par tick. Le renderer de la poignee decale l'ellipse du deplacement du
 * parent entre {@code t_edit} et le temps vivant (gizmos et visee restent a {@code t_edit}) ; avec « Orbite », le
 * parent est au centre de la vue et ne derive pas. Rotation {@code R_cube . R_orbite}, echelle
 * {@code (a_aff . sigma, a_aff . sigma, b_aff . sigma)}. Sondage (doc 04 par. 7) : rotation et echelle comparees
 * chaque {@code serverTick} aux dernieres ecrites ({@value #ORBIT_ANGLE_EPSILON} degre, ecart relatif
 * {@value #ORBIT_SCALE_EPSILON}), conversion avec le transform du cube, l'unite de la vue et les ids retenus a
 * l'ecriture ; seules les composantes changees sont recalculees ; la periode suit {@code a} (C36).
 * <p>
 * <b>Objets dynamiques</b> : poignees creees et retirees des deux cotes, memes cles, seulement si absentes /
 * presentes ({@code addObject} leve sur une cle deja utilisee) : serveur chaque tick et apres chaque action, client
 * chaque tick (l'etat de la Map arrive dans ses propres paquets). Le serveur les replace des que leur placement voulu
 * change ; le client ne les place lui-meme qu'a leur creation (le transform envoye par le serveur avant la creation
 * locale est perdu) et quand le cube de la Map bouge (elles le suivent pendant la manipulation) ; sinon le transform du
 * serveur fait foi.
 * <p>
 * <b>Acces</b> de la fenetre : {@code HoloAccess.players(<operateurs connectes>)}, recalcule chaque tick serveur et
 * reaffecte seulement s'il change. Fenetre et poignees ne sont dessinees que chez un operateur ({@code shown()},
 * backend) ; la Map, elle, est visible de tous.
 * <p>
 * <b>Import d'une texture</b> (T45, D27) : le bouton « Importer » de la fenetre passe par le backend
 * ({@link Backend#importTexture}) : choix du fichier, preparation et envoi au serveur par le canal des textures Space
 * ({@code modules.dimension.space.texture}), puis, la texture stockee, demande d'application par l'action 7
 * ({@link #applyImportedTexture} : {@code custom:<sha1>}, refusee si le fichier n'est pas stocke pour la dimension).
 * <p>
 * Le client passe par un {@link Backend} installe au setup client ({@code client.modules.monde.spaceconfigurator}).
 */
public final class SpaceConfiguratorModule extends HoloModule
{
    /** Type d'action : « Ajouter » (systeme au centre de la carte galactique de la Map). */
    public static final int ACTION_ADD = 0;
    /** Type d'action : « Retirer » (varint id). */
    public static final int ACTION_REMOVE = 1;
    /** Type d'action : renommer (varint id, chaine). */
    public static final int ACTION_RENAME = 2;
    /** Type d'action : « Monde ici » (varint id). */
    public static final int ACTION_SET_CURRENT = 3;
    /** Type d'action : « Avant du monde » (octet : indice de {@link #FORWARD_YAW}). */
    public static final int ACTION_FORWARD = 4;
    /** Type d'action : ajout d'un corps (varint systeme, octet : ordinal de {@link SpaceBodyKind}, varint parent). */
    public static final int ACTION_ADD_BODY = 5;
    /** Type d'action : retrait d'un corps et de ses descendants (varint systeme, varint id). */
    public static final int ACTION_REMOVE_BODY = 6;
    /** Type d'action : modification d'un champ d'un corps (varint systeme, varint id, octet champ, valeur). */
    public static final int ACTION_UPDATE_BODY = 7;
    /** Type d'action : vitesse de distorsion du saut (double, al/s). */
    public static final int ACTION_WARP_SPEED = 8;
    /** Type d'action : echelle de temps des astres (double, secondes simulees par tick ; C119). */
    public static final int ACTION_TIME_SCALE = 9;
    /** Type d'action : vue de la Map cadree sur l'orbite d'un corps (varint systeme, varint id ; « Orbite »). */
    public static final int ACTION_FRAME_ORBIT = 10;
    /** Type d'action : corps remis a ses valeurs par defaut (varint systeme, varint id ; « RESET », D34). */
    public static final int ACTION_RESET_BODY = 11;
    /** Type d'action : vaisseau teleporte pres d'un corps (varint systeme, varint id ; « Se teleporter », D34). */
    public static final int ACTION_TELEPORT = 12;
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
    /** Champ : masse en kg (double, finie et positive, bornee par le modele ; T39). */
    public static final int FIELD_MASS = 6;
    /** Champ : periode de rotation propre en heures (double, signee, {@code 0} = synchrone ; T44). */
    public static final int FIELD_ROTATION_PERIOD = 7;
    /** Champ : obliquite de la rotation propre en degres (double, bornee a {@code [0, 180]} ; T44). */
    public static final int FIELD_OBLIQUITY = 8;
    /** Champ : longitude du noeud du pole en degres (double, ramenee a {@code [-180, 180[} ; T44). */
    public static final int FIELD_NODE = 9;
    /** Champ : phase du meridien origine a {@code t = 0} en degres (double, ramenee a {@code [0, 360[} ; T44). */
    public static final int FIELD_ROTATION_PHASE = 10;
    /** Champ : texture (utf, forme texte de {@code SpaceTextureRef} ; T44). */
    public static final int FIELD_TEXTURE = 11;
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
     * Demi-petit axe affiche minimal (unites du cube) d'une orbite pour avoir une poignee : en dessous, l'echelle de
     * la poignee serait bornee par {@link #MIN_ORBIT_SCALE} et sa relecture fausse ; il faut rapprocher la vue.
     */
    public static final double MIN_HANDLE_AXIS = 1e-5;
    /** Apoapside affichee maximale (unites du cube) d'une orbite pour avoir une poignee : ellipse entiere dans la Map. */
    public static final double MAX_HANDLE_REACH = 4.0;
    /** Distance maximale (unites du cube, par axe) du parent affiche au centre de la vue pour avoir une poignee. */
    public static final double PARENT_LIMIT = 2.0;
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
    /** Longueur maximale (caracteres) d'une texture lue dans une action ({@code custom:} + 40 : 47). */
    private static final int MAX_TEXTURE_READ = 64;
    /** Niveau de permission d'un operateur (comme les commandes du mod et l'edition). */
    public static final int OP_LEVEL = 2;
    /** Ecart horizontal entre la fenetre de la Map et celle du Configurator (placement par defaut), en blocs. */
    public static final float WINDOW_GAP = 0.25f;
    private static final Component NO_MAP = Component.literal("Space Map requise sur ce marker");
    private static final Component NO_ENVIRONMENT =
            Component.literal("Aucun Space Environment dans cette dimension");
    private static final Component UNKNOWN_SYSTEM = Component.literal("Systeme inconnu");
    private static final Component UNKNOWN_BODY = Component.literal("Corps inconnu");
    private static final Component PARENT_REQUIRED = Component.literal("Un parent est requis");
    private static final Component NON_FINITE = Component.literal("Valeur invalide");
    private static final Component FIELD_UNUSED = Component.literal("Ce champ ne s'applique pas a ce corps");
    private static final Component NO_ORBIT = Component.literal("Ce corps n'a pas d'orbite");
    private static final Component BAD_TEXTURE = Component.literal("Texture invalide");
    private static final Component UNKNOWN_TEXTURE = Component.literal("Texture inconnue");
    private static final Component ALREADY_DEFAULT = Component.literal("Deja aux valeurs par defaut");
    private static final Component MISSING_TEXTURE = Component.literal("Texture importee absente du serveur");
    /** Etat de l'import d'une texture ({@link Backend#importProgress}) : aucun import en cours. */
    public static final int IMPORT_IDLE = -1;
    /** Etat de l'import : choix du fichier ou preparation de l'image. */
    public static final int IMPORT_PREPARING = -2;

    /** Galaxie par defaut (valeurs du RESET, D34), creee une fois a la premiere demande (fil serveur), jamais modifiee. */
    private static SpaceGalaxy defaultGalaxy;

    private static Backend backend;

    // ---------------------------------------------------------------- etat synchronise

    /** Temps fige de la poignee d'orbite ({@code t_edit}), jours ; repris par le serveur au changement de contexte. */
    private double editTime;
    private boolean environment;
    /** Indice du choix de l'axe avant ({@link #FORWARD_YAW}), {@code -1} = autre valeur ou pas d'Environment. */
    private int forward = -1;
    /** Vitesse de distorsion de l'Environment (copie), al/s. */
    private double warpSpeed = SpaceMotionSettings.DEFAULT_WARP_SPEED;
    /** Echelle de temps des astres de l'Environment (copie), secondes simulees par tick. */
    private double timeScale = SpaceMotionSettings.DEFAULT_TIME_SCALE;

    /** Vrai apres la lecture d'une sauvegarde d'avant T42 : fenetre remise a son placement par defaut (serveur). */
    private boolean legacyWindow;

    // ---------------------------------------------------------------- serveur

    /** Reglages de l'Environment deja lus (instance remplacee a chaque changement) ; {@link #settingsRead} si lus. */
    private SpaceMotionSettings lastSettings;
    private boolean settingsRead;
    private final Set<UUID> ops = new HashSet<>();
    /** Contexte du dernier {@code t_edit} (systeme montre, corps, centre, demi-etendue, revision de la copie). */
    private SpaceSystem frozenSystem;
    private int frozenBody;
    private int frozenCenter;
    private double frozenHalf;
    private int frozenRevision;
    private boolean frozenKnown;

    // ---------------------------------------------------------------- deux cotes (une instance par cote)

    /** Space Map du meme marker, relue a chaque tick ({@link SpaceMapModule#of}), {@code null} sans Map. */
    private SpaceMapModule map;
    private SpaceConfiguratorWindow window;
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f point = new Vector3f();
    private final Vector3d cubePoint = new Vector3d();
    /** Map et transform de son cube vus au tick precedent (client : les poignees suivent le cube). */
    private SpaceMapModule cubeMap;
    private final HoloTransform lastCube = new HoloTransform();

    /** Poignee de l'etoile selectionnee, {@code null} si elle n'existe pas. */
    private SpaceMapHandle handle;
    /** Derniere position ecrite dans la poignee (repere du marker) ; valable si {@link #handleWritten}. */
    private final Vector3f lastHandle = new Vector3f();
    private boolean handleWritten;
    /** Contexte de la derniere ecriture : systeme, cadrage galactique et transform du cube. */
    private int handleSystem;
    private double handleFrameX;
    private double handleFrameY;
    private double handleFrameZ;
    private double handleFrameHalf;
    private final HoloTransform handleCube = new HoloTransform();

    /** Poignee d'orbite du corps selectionne, {@code null} si elle n'existe pas. */
    private SpaceOrbitHandle orbitHandle;
    /** Derniere valeur ecrite dans la poignee d'orbite ; valable si {@link #orbitWritten}. */
    private final HoloTransform lastOrbit = new HoloTransform();
    private boolean orbitWritten;
    /** Contexte de la derniere ecriture : ids, transform du cube, unites du cube (blocs) par UA. */
    private int orbitSystem;
    private int orbitBody;
    private final HoloTransform orbitCube = new HoloTransform();
    private double orbitUnit = 1.0;
    /** Placement voulu de la poignee d'orbite, et transform de travail de la lecture. */
    private final HoloTransform wantedOrbit = new HoloTransform();
    private final HoloTransform orbitScratch = new HoloTransform();
    private final Quaternionf orbitRotation = new Quaternionf();
    /** Positions affichees des corps au temps fige (unites du cube), tableau reutilise. */
    private final Vector3d[] bodyPoints = SpaceOrbits.newPositionArray();
    /** Indice du parent du corps de la poignee d'orbite (resolu par {@link #orbitTarget}), {@code -1} sinon. */
    private int orbitParent = -1;

    // ---------------------------------------------------------------- client : rafraichissement de la fenetre

    private SpaceMapModule shownMap;
    private int shownRevision = -1;
    private boolean shownSystemView;
    private int shownSelectedSystem = -1;
    private int shownSelectedBody = -1;
    private int shownCurrent = -1;
    private SpaceSystem shownData;
    private boolean shownEnvironment;
    private boolean shownOrbitHandle;

    /** Pont vers le client, installe au setup client. */
    public interface Backend
    {
        /** Vrai si le joueur local est operateur (niveau {@value SpaceConfiguratorModule#OP_LEVEL}). */
        boolean localOp();

        /**
         * T45 : lance l'import d'une texture pour le corps {@code bodyId} du systeme {@code systemId} (choix du fichier,
         * preparation, envoi, puis {@link SpaceConfiguratorModule#applyImportedTexture}) ; faux si un import est deja
         * en cours ou impossible.
         */
        default boolean importTexture(SpaceConfiguratorModule module, int systemId, int bodyId) { return false; }

        /**
         * T45 : etat de l'import du joueur local : {@link #IMPORT_IDLE}, {@link #IMPORT_PREPARING} ou avancement de
         * l'envoi (0 a 100).
         */
        default int importProgress() { return IMPORT_IDLE; }
    }

    /** Branche le client (setup client). */
    public static void install(Backend backend)
    {
        SpaceConfiguratorModule.backend = Objects.requireNonNull(backend);
    }

    /** Vrai si le joueur local est operateur ; faux sans backend (serveur dedie). */
    static boolean localOp() { return backend != null && backend.localOp(); }

    /** Etat de l'import d'une texture du joueur local ({@link Backend#importProgress}) ; inactif sans backend. */
    static int importProgress() { return backend != null ? backend.importProgress() : IMPORT_IDLE; }

    /**
     * Placement par defaut de la fenetre (T42, aussi son RESET) : meme plan que la fenetre de la Space Map placee
     * devant la base du cube par defaut ({@link SpaceMapCube#DEFAULT_HEIGHT}, {@link SpaceMapCube#DEFAULT_SCALE}), a
     * sa droite vue de face ({@code +X}) a {@value #WINDOW_GAP} bloc d'elle, bord bas au niveau du bas du cube.
     * Constant (le cube reel peut avoir bouge : les operateurs placent ensuite la fenetre au {@code world_editor}).
     */
    public static HoloTransform defaultWindowTransform()
    {
        float cubeHalf = SpaceMapCube.DEFAULT_SCALE * SpaceMapCube.HALF_SIZE;
        float x = SpaceMapWindow.WIDTH * SpaceMapWindow.PIXEL_SCALE * 0.5f + WINDOW_GAP
                + SpaceConfiguratorWindow.WIDTH * SpaceConfiguratorWindow.PIXEL_SCALE * 0.5f;
        float y = SpaceMapCube.DEFAULT_HEIGHT - cubeHalf + SpaceConfiguratorWindow.HALF_HEIGHT;
        float z = cubeHalf + SpaceMapModule.WINDOW_GAP;
        return HoloTransform.translation(x, y, z);
    }

    // ---------------------------------------------------------------- cycle de vie

    /**
     * Cree la fenetre (placement par defaut : {@link #defaultWindowTransform}) ; cote client, les poignees si la Map
     * deja chargee les demande (le serveur les cree a son premier tick). Le framework restaure ensuite les placements
     * sauvegardes / recus.
     */
    @Override
    public void onLoad()
    {
        window = addObject(new SpaceConfiguratorWindow("window", this, defaultWindowTransform()));
        if (host().clientSide()) {
            map = SpaceMapModule.of(this);
            updateHandles(true);
        }
        refresh();
    }

    @Override
    public void onUnload()
    {
        if (host().clientSide() && map != null) map.hideOrbit(0);
        map = null;
        cubeMap = null;
        lastSettings = null;
        settingsRead = false;
        frozenSystem = null;
        frozenKnown = false;
        handle = null;
        handleWritten = false;
        orbitHandle = null;
        orbitWritten = false;
        shownMap = null;
        shownData = null;
    }

    @Override
    public void save(CompoundTag tag)
    {
        tag.putBoolean("mapped", true);
    }

    /** Lecture tolerante : les cles d'avant T42 sont ignorees (L22) ; sans {@code mapped}, fenetre a replacer. */
    @Override
    public void load(CompoundTag tag)
    {
        legacyWindow = !tag.contains("mapped");
    }

    // ---------------------------------------------------------------- serveur

    /**
     * Reglages de l'Environment, Map du marker (copie rafraichie), fenetre d'une ancienne sauvegarde, sondage des
     * poignees, temps fige, poignees creees / retirees / replacees, acces des operateurs.
     */
    @Override
    public void serverTick(ServerLevel level)
    {
        SpaceEnvironmentModule env = SpaceEnvironmentModule.of(level);
        updateSettings(env);
        map = SpaceMapModule.of(this);
        if (map != null) map.refreshFrom(env);
        resetLegacyWindow();
        pollHandle(env);
        pollOrbit(env);
        freezeTime(env);
        updateHandles(false);
        updateAccess(level.getServer());
    }

    /**
     * Relit l'axe avant, la vitesse de distorsion et l'echelle de temps si l'Environment ou l'instance de ses reglages
     * a change ; envoi seulement si une valeur copiee change.
     */
    private void updateSettings(SpaceEnvironmentModule env)
    {
        SpaceMotionSettings settings = env != null ? env.settings() : null;
        boolean present = env != null;
        if (settingsRead && settings == lastSettings && present == environment) return;
        settingsRead = true;
        lastSettings = settings;
        int choice = settings != null ? forwardChoice(settings) : -1;
        double warp = settings != null ? settings.warpSpeed() : SpaceMotionSettings.DEFAULT_WARP_SPEED;
        double scale = settings != null ? settings.timeScale() : SpaceMotionSettings.DEFAULT_TIME_SCALE;
        if (present != environment || choice != forward || warp != warpSpeed || scale != timeScale) {
            environment = present;
            forward = choice;
            warpSpeed = warp;
            timeScale = scale;
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

    /** Sauvegarde d'avant T42 : fenetre remise une fois a son placement par defaut, marque ecrite ensuite. */
    private void resetLegacyWindow()
    {
        if (!legacyWindow) return;
        legacyWindow = false;
        window.transform().set(defaultWindowTransform());
        markDirty();
    }

    /**
     * Temps fige de la poignee d'orbite : repris ({@code days()} de l'Environment, envoye) quand le contexte change
     * (systeme montre ou sa copie, corps selectionne, centre, demi-etendue de la vue) et qu'un corps a orbite est
     * selectionne dans la vue systeme ; jamais par tick sinon.
     */
    private void freezeTime(SpaceEnvironmentModule env)
    {
        SpaceSystem system = map != null ? map.shownSystem() : null;
        int body = system != null ? map.selectedBody() : 0;
        int center = system != null ? map.centerIndex() : -1;
        double half = system != null ? map.viewHalf() : 0.0;
        int revision = map != null ? map.copiedRevision() : 0;
        if (frozenKnown && system == frozenSystem && body == frozenBody && center == frozenCenter
                && half == frozenHalf && revision == frozenRevision) {
            return;
        }
        frozenKnown = true;
        frozenSystem = system;
        frozenBody = body;
        frozenCenter = center;
        frozenHalf = half;
        frozenRevision = revision;
        SpaceBody selected = system != null && body != 0 ? system.body(body) : null;
        if (env == null || selected == null || selected.orbit() == null) return;
        double now = env.days();
        if (now != editTime) {
            editTime = now;
            markDirty();
        }
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
     * Operateurs seulement (verifie en premier, refus silencieux), source = la fenetre, Space Map du marker et
     * Environment requis ; puis contenu valide contre l'Environment de la dimension du joueur.
     */
    @Override
    public void handleAction(ServerPlayer player, HoloObject source, FriendlyByteBuf buf)
    {
        if (!player.hasPermissions(OP_LEVEL)) return;
        if (source == null || source != window) return;
        int action = buf.readByte();
        SpaceEnvironmentModule env = SpaceEnvironmentModule.of(player.serverLevel());
        updateSettings(env);
        map = SpaceMapModule.of(this);
        if (map == null) {
            player.displayClientMessage(NO_MAP, true);
            return;
        }
        if (env == null) {
            player.displayClientMessage(NO_ENVIRONMENT, true);
            return;
        }
        map.refreshFrom(env);
        switch (action) {
            case ACTION_ADD -> {
                SpaceEditResult result = env.addSystem("", map.frameX(), map.frameY(), map.frameZ());
                if (report(player, result)) {
                    map.refreshFrom(env);
                    map.focusSystem(result.id());
                }
            }
            case ACTION_REMOVE -> report(player, env.removeSystem(buf.readVarInt()));
            case ACTION_RENAME -> {
                int id = buf.readVarInt();
                report(player, env.renameSystem(id, buf.readUtf(MAX_NAME_READ)));
            }
            case ACTION_SET_CURRENT -> report(player, env.setCurrentSystem(buf.readVarInt()));
            case ACTION_FORWARD -> {
                int index = buf.readByte();
                if (index >= 0 && index < FORWARD_YAW.length) {
                    report(player, env.setForward(FORWARD_YAW[index], FORWARD_PITCH[index]));
                }
            }
            case ACTION_ADD_BODY -> {
                int systemId = buf.readVarInt();
                int ordinal = buf.readByte();
                int parent = buf.readVarInt();
                SpaceBodyKind[] kinds = SpaceBodyKind.values();
                if (ordinal >= 0 && ordinal < kinds.length) addBody(player, env, systemId, kinds[ordinal], parent);
            }
            case ACTION_REMOVE_BODY -> {
                int systemId = buf.readVarInt();
                int id = buf.readVarInt();
                if (system(player, env, systemId) != null) report(player, env.removeBody(systemId, id));
            }
            case ACTION_UPDATE_BODY -> updateBody(player, env, buf);
            case ACTION_WARP_SPEED -> report(player, env.setWarpSpeed(buf.readDouble()));
            case ACTION_TIME_SCALE -> report(player, env.setTimeScale(buf.readDouble()));
            case ACTION_FRAME_ORBIT -> {
                int systemId = buf.readVarInt();
                int id = buf.readVarInt();
                if (system(player, env, systemId) != null && !map.frameOrbit(systemId, id)) {
                    player.displayClientMessage(NO_ORBIT, true);
                }
            }
            case ACTION_RESET_BODY -> {
                int systemId = buf.readVarInt();
                int id = buf.readVarInt();
                resetBody(player, env, systemId, id);
            }
            case ACTION_TELEPORT -> {
                int systemId = buf.readVarInt();
                int id = buf.readVarInt();
                if (system(player, env, systemId) != null) report(player, env.teleportTo(systemId, id));
            }
            default -> {
            }
        }
        // Effet visible des le prochain envoi : copie de la Map, reglages, temps fige et poignees a jour.
        map.refreshFrom(env);
        updateSettings(env);
        freezeTime(env);
        updateHandles(false);
    }

    /** Systeme {@code id} de l'Environment ; {@code null} (avec un message) s'il est inconnu. */
    private static SpaceSystem system(ServerPlayer player, SpaceEnvironmentModule env, int id)
    {
        SpaceSystem system = id > 0 ? env.system(id) : null;
        if (system == null) player.displayClientMessage(UNKNOWN_SYSTEM, true);
        return system;
    }

    /** « +Etoile », « +Planete », « +Lune », « +Mineur » : corps aux valeurs par defaut du type, puis selectionne. */
    private void addBody(ServerPlayer player, SpaceEnvironmentModule env, int systemId, SpaceBodyKind kind,
                         int parent)
    {
        if (system(player, env, systemId) == null) return;
        if (parent <= 0) {
            player.displayClientMessage(PARENT_REQUIRED, true);
            return;
        }
        SpaceEditResult result = env.addBody(systemId, kind, parent);
        if (!report(player, result)) return;
        map.refreshFrom(env);
        map.selectInView(systemId, result.id());
    }

    /**
     * Modification d'un champ d'un corps ({@link #ACTION_UPDATE_BODY}) : valeur lue, verifiee (finie, champ
     * applicable au corps ; masse strictement positive, T39 ; texture bien formee et, si integree, connue, T44), corps
     * modifie par le modele a partir de sa valeur courante cote serveur, seul ce champ change (bornes appliquees : masse
     * dans {@code [1e10, 1e33]} kg, rotation propre par {@code SpaceRotation}) puis {@code updateBody} (revision
     * incrementee ; rien si le corps ne change pas).
     */
    private void updateBody(ServerPlayer player, SpaceEnvironmentModule env, FriendlyByteBuf buf)
    {
        int systemId = buf.readVarInt();
        int id = buf.readVarInt();
        int field = buf.readByte();
        String text = null;
        int color = 0;
        double number = Double.NaN;
        switch (field) {
            case FIELD_NAME -> text = buf.readUtf(MAX_NAME_READ);
            case FIELD_TEXTURE -> text = buf.readUtf(MAX_TEXTURE_READ);
            case FIELD_COLOR -> color = buf.readInt();
            case FIELD_RADIUS, FIELD_LUMINOSITY, FIELD_PERIOD, FIELD_PHASE, FIELD_MASS, FIELD_ROTATION_PERIOD,
                 FIELD_OBLIQUITY, FIELD_NODE, FIELD_ROTATION_PHASE -> number = buf.readDouble();
            default -> {
                return;
            }
        }
        SpaceSystem system = system(player, env, systemId);
        if (system == null) return;
        SpaceBody body = system.body(id);
        if (body == null) {
            player.displayClientMessage(UNKNOWN_BODY, true);
            return;
        }
        boolean invalid = text == null && field != FIELD_COLOR && !Double.isFinite(number);
        if (invalid || field == FIELD_MASS && !(number > 0.0)) {
            player.displayClientMessage(NON_FINITE, true);
            return;
        }
        SpaceTextureRef texture = null;
        if (field == FIELD_TEXTURE) {
            texture = SpaceTextureRef.parse(text);
            if (texture == null) {
                player.displayClientMessage(BAD_TEXTURE, true);
                return;
            }
            if (texture.source() == SpaceTextureRef.Source.BUILTIN && !SpaceBodyTextures.exists(texture.id())) {
                player.displayClientMessage(UNKNOWN_TEXTURE, true);
                return;
            }
            // T45 : une texture importee doit avoir ete stockee pour la dimension (canal des textures Space).
            if (texture.source() == SpaceTextureRef.Source.CUSTOM
                    && !SpaceTextureStore.exists(player.serverLevel(), texture.id())) {
                player.displayClientMessage(MISSING_TEXTURE, true);
                return;
            }
        }
        SpaceRotation rotation = body.rotation();
        SpaceBody updated;
        try {
            updated = switch (field) {
                case FIELD_NAME -> body.withName(text);
                case FIELD_TEXTURE -> body.withTexture(texture);
                case FIELD_ROTATION_PERIOD -> body.withRotation(rotation.withPeriod(number));
                case FIELD_OBLIQUITY -> body.withRotation(rotation.withPole(number, rotation.node()));
                case FIELD_NODE -> body.withRotation(rotation.withPole(rotation.obliquity(), number));
                case FIELD_ROTATION_PHASE -> body.withRotation(rotation.withPhase(number));
                case FIELD_RADIUS -> body.withRadius(number);
                case FIELD_COLOR -> body.withColor(color);
                case FIELD_LUMINOSITY -> body.kind() == SpaceBodyKind.STAR ? body.withLuminosity((float) number)
                        : null;
                case FIELD_PERIOD -> body.orbit() != null ? body.withOrbit(body.orbit().withPeriod(number)) : null;
                case FIELD_MASS -> body.withMass(number);
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
        report(player, env.updateBody(systemId, updated));
    }

    /**
     * « RESET » (D34) : le corps reprend <b>toutes</b> ses valeurs par defaut (nom, rayon, couleur, luminosite, masse,
     * orbite, rotation propre, texture) par {@code updateBody} (revision incrementee, valeurs validees par le modele).
     * Defaut = corps de memes ids de systeme et de corps dans la galaxie par defaut, s'il a le meme type et le meme
     * parent (sinon c'est un autre astre : galaxie d'un ancien lot, ou ids reutilises par une autre galaxie) ; a
     * defaut, valeurs d'un astre neuf de son type comme a sa creation ({@code addBody} : nom « Type id », rayon et
     * couleur du type, luminosite 1, masse estimee, rotation du type, texture auto), l'<b>orbite actuelle gardee</b>
     * (celle de la creation dependait des orbites d'alors). Rien si le corps est deja a ses valeurs par defaut.
     */
    private void resetBody(ServerPlayer player, SpaceEnvironmentModule env, int systemId, int id)
    {
        SpaceSystem system = system(player, env, systemId);
        if (system == null) return;
        SpaceBody body = system.body(id);
        if (body == null) {
            player.displayClientMessage(UNKNOWN_BODY, true);
            return;
        }
        if (defaultGalaxy == null) defaultGalaxy = SpaceGalaxy.createDefault();
        SpaceSystem defaults = defaultGalaxy.system(systemId);
        SpaceBody reference = defaults != null ? defaults.body(id) : null;
        SpaceBody updated;
        try {
            if (reference != null && reference.kind() == body.kind() && reference.parentId() == body.parentId()) {
                updated = reference;
            } else {
                SpaceBodyKind kind = body.kind();
                updated = new SpaceBody(id, kind, kind.defaultName() + " " + id, body.parentId(), kind.defaultRadius(),
                        kind.defaultColor(), 1.0f, body.orbit());
            }
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(NON_FINITE, true);
            return;
        }
        if (updated.equals(body)) {
            player.displayClientMessage(ALREADY_DEFAULT, true);
            return;
        }
        report(player, env.updateBody(systemId, updated));
    }

    /** Affiche le motif d'un refus au joueur ; vrai si l'operation a reussi. */
    private static boolean report(ServerPlayer player, SpaceEditResult result)
    {
        if (result.success()) return true;
        if (result.message() != null) player.displayClientMessage(result.message(), true);
        return false;
    }

    // ---------------------------------------------------------------- client

    /**
     * Client : Map du marker relue, poignees creees / retirees selon son etat (placees a leur creation et quand son
     * cube bouge), orbite du corps edite masquee dans la Map chez un operateur, fenetre rafraichie si ce qu'elle
     * montre a change. Sans allocation (hors creation d'une poignee et rafraichissement de la fenetre).
     */
    @Override
    public void clientTick()
    {
        map = SpaceMapModule.of(this);
        updateHandles(cubeMoved());
        if (map != null) map.hideOrbit(orbitHandle != null && localOp() ? map.selectedBody() : 0);
        refreshOnChange();
    }

    /** Client : vrai si la Map ou le transform de son cube ont change depuis le tick precedent. */
    private boolean cubeMoved()
    {
        SpaceMapCube cube = map != null ? map.cube() : null;
        if (cube == null) {
            cubeMap = null;
            return false;
        }
        HoloTransform t = cube.transform();
        if (map == cubeMap && sameTransform(t, lastCube)) return false;
        cubeMap = map;
        lastCube.set(t);
        return true;
    }

    /** Client : fenetre rafraichie quand la Map, sa selection, sa copie, sa vue ou la poignee d'orbite changent. */
    private void refreshOnChange()
    {
        SpaceMapModule m = map;
        int revision = m != null ? m.copiedRevision() : -1;
        boolean systemView = m != null && m.systemView();
        int selectedSystem = m != null ? m.selectedSystem() : -1;
        int selectedBody = m != null ? m.selectedBody() : -1;
        int current = m != null ? m.currentSystemId() : -1;
        SpaceSystem data = m != null ? m.displayedSystemData() : null;
        boolean env = m != null && m.environmentPresent();
        boolean orbit = orbitHandle != null;
        if (m == shownMap && revision == shownRevision && systemView == shownSystemView
                && selectedSystem == shownSelectedSystem && selectedBody == shownSelectedBody
                && current == shownCurrent && data == shownData && env == shownEnvironment
                && orbit == shownOrbitHandle) {
            return;
        }
        shownMap = m;
        shownRevision = revision;
        shownSystemView = systemView;
        shownSelectedSystem = selectedSystem;
        shownSelectedBody = selectedBody;
        shownCurrent = current;
        shownData = data;
        shownEnvironment = env;
        shownOrbitHandle = orbit;
        refresh();
    }

    // ---------------------------------------------------------------- poignees (deux cotes)

    /**
     * Cree ou retire les poignees selon l'etat de la Map (seulement si absentes / presentes), puis les place : serveur
     * toujours (ecriture seulement si le placement voulu change), client a leur creation ou si {@code place}.
     */
    private void updateHandles(boolean place)
    {
        boolean server = !host().clientSide();
        SystemHeader header = handleTarget();
        if (header != null && handle == null) {
            handle = object(SpaceMapHandle.KEY) instanceof SpaceMapHandle existing ? existing
                    : addObject(new SpaceMapHandle());
            handleWritten = false;
        } else if (header == null && handle != null) {
            removeObject(SpaceMapHandle.KEY);
            handle = null;
            handleWritten = false;
        }
        if (handle != null && (server || place || !handleWritten)) placeHandle(header, server);

        SpaceBody body = orbitTarget();
        if (body != null && orbitHandle == null) {
            orbitHandle = object(SpaceOrbitHandle.KEY) instanceof SpaceOrbitHandle existing ? existing
                    : addObject(new SpaceOrbitHandle());
            orbitWritten = false;
        } else if (body == null && orbitHandle != null) {
            removeObject(SpaceOrbitHandle.KEY);
            orbitHandle = null;
            orbitWritten = false;
        }
        if (orbitHandle != null && (server || place || !orbitWritten)) placeOrbit(body, server);
    }

    /** Systeme dont l'etoile porte la poignee : selection de la Map sur sa carte galactique ; {@code null} sinon. */
    private SystemHeader handleTarget()
    {
        if (map == null || map.cube() == null || map.systemView() || !(map.frameHalf() > 0.0)) return null;
        return map.selectedHeader();
    }

    /**
     * Ecrit dans la poignee la position de l'etoile de {@code header} (bornee au cube de la Map) si elle differe de la
     * derniere ecrite (toujours cote client : appele seulement quand il faut placer), et retient le contexte.
     */
    private void placeHandle(SystemHeader header, boolean server)
    {
        map.galaxyPoint(header, cubePoint);
        float h = SpaceMapCube.HALF_SIZE;
        map.cubeToMarker(clamp(cubePoint.x, h), clamp(cubePoint.y, h), clamp(cubePoint.z, h), point);
        if (server && handleWritten && near(point, lastHandle)) return;
        handle.transform().setTranslation(point.x, point.y, point.z);
        lastHandle.set(point);
        handleWritten = true;
        handleSystem = header.id();
        handleFrameX = map.frameX();
        handleFrameY = map.frameY();
        handleFrameZ = map.frameZ();
        handleFrameHalf = map.frameHalf();
        handleCube.set(map.cube().transform());
    }

    /**
     * Serveur : la poignee a ete deplacee par un joueur (ecart a la derniere position ecrite) : position convertie en
     * unites du cube avec le transform et le cadrage de cette ecriture, {@code q = (R^-1 . (P - t)) / sigma}, bornee a
     * {@code [-1, 1]^3}, puis {@code g = c + q . H} et {@code moveSystem} du systeme de cette ecriture. La poignee est
     * ensuite reecrite a la position du systeme (bornee).
     */
    private void pollHandle(SpaceEnvironmentModule env)
    {
        if (handle == null || !handleWritten) return;
        HoloTransform p = handle.transform();
        point.set(p.x(), p.y(), p.z());
        if (near(point, lastHandle)) return;
        handleWritten = false;
        if (env == null) return;
        HoloTransform t = handleCube;
        point.sub(t.x(), t.y(), t.z());
        HoloEditMath.rotation(rotation, t).conjugate().transform(point);
        float h = SpaceMapCube.HALF_SIZE;
        double qx = clamp(point.x / (h * Math.max(1e-6f, t.scaleX())), 1.0);
        double qy = clamp(point.y / (h * Math.max(1e-6f, t.scaleY())), 1.0);
        double qz = clamp(point.z / (h * Math.max(1e-6f, t.scaleZ())), 1.0);
        double half = handleFrameHalf;
        env.moveSystem(handleSystem, handleFrameX + qx * half, handleFrameY + qy * half, handleFrameZ + qz * half);
        if (map != null) map.refreshFrom(env);
    }

    /**
     * Corps edite par la poignee d'orbite : corps selectionne de la vue systeme de la Map, a orbite, dont l'orbite est
     * affichee a l'echelle de la vue (demi-petit axe {@code >= MIN_HANDLE_AXIS}, apoapside {@code <= MAX_HANDLE_REACH}
     * unites du cube) et dont le parent, au temps fige, est a moins de {@link #PARENT_LIMIT} unites du centre ;
     * {@code null} sinon. Remplit {@link #bodyPoints} (temps fige) et {@link #orbitParent}. Sans allocation.
     */
    private SpaceBody orbitTarget()
    {
        orbitParent = -1;
        SpaceSystem system = map != null && map.cube() != null ? map.shownSystem() : null;
        SpaceBody body = system != null && map.selectedBody() != 0 ? system.body(map.selectedBody()) : null;
        if (body == null || body.orbit() == null || body.isRoot()) return null;
        int parent = system.parentIndex(system.indexOf(body.id()));
        if (parent < 0) return null;
        double k = SpaceMapCube.HALF_SIZE / map.viewHalf();
        SpaceOrbit orbit = body.orbit();
        if (orbit.semiMinorAxis() * k < MIN_HANDLE_AXIS || orbit.apoapsis() * k > MAX_HANDLE_REACH) return null;
        map.bodyPoints(editTime, bodyPoints);
        Vector3d q = bodyPoints[parent];
        double limit = PARENT_LIMIT * SpaceMapCube.HALF_SIZE;
        if (!(Math.abs(q.x) <= limit && Math.abs(q.y) <= limit && Math.abs(q.z) <= limit)) return null;
        orbitParent = parent;
        return body;
    }

    /**
     * Ecrit dans la poignee d'orbite son placement voulu ({@code docs/space/04-mathematiques.md} par. 7) :
     * translation = parent affiche au temps fige dans la vue de la Map, rotation = {@code R_cube . R_orbite}, echelle
     * {@code (a_aff . sigma, a_aff . sigma, b_aff . sigma)} ({@code a_aff = a / H}). Serveur : seulement s'il differe
     * du dernier ecrit. Retient le contexte (ids, cube, unite) pour la relecture.
     */
    private void placeOrbit(SpaceBody body, boolean server)
    {
        HoloTransform t = map.cube().transform();
        Vector3d q = bodyPoints[orbitParent];
        map.cubeToMarker(q.x, q.y, q.z, point);
        wantedOrbit.setTranslation(point.x, point.y, point.z);
        SpaceOrbit orbit = body.orbit();
        orbitRotation.rotationYXZ(-(float) orbit.yaw() * Mth.DEG_TO_RAD, (float) orbit.pitch() * Mth.DEG_TO_RAD,
                (float) orbit.roll() * Mth.DEG_TO_RAD);
        setRotationYXZ(wantedOrbit, HoloEditMath.rotation(rotation, t).mul(orbitRotation));
        double unit = SpaceMapCube.HALF_SIZE / map.viewHalf() * meanScale(t);
        float scaleA = orbitScale(orbit.semiMajorAxis() * unit);
        wantedOrbit.setScale(scaleA, scaleA, orbitScale(orbit.semiMinorAxis() * unit));
        if (server && orbitWritten && sameTransform(wantedOrbit, lastOrbit)) return;
        orbitHandle.transform().set(wantedOrbit);
        lastOrbit.set(wantedOrbit);
        orbitWritten = true;
        orbitSystem = map.shownSystem().id();
        orbitBody = body.id();
        orbitCube.set(t);
        orbitUnit = unit;
    }

    private static float orbitScale(double value)
    {
        return (float) Math.max(MIN_ORBIT_SCALE, Math.min(HoloTransform.MAX_SCALE, value));
    }

    /**
     * Serveur : la poignee d'orbite a ete editee par un joueur (rotation ou echelle {@code X} / {@code Z} differente de
     * la derniere ecrite) : orbite du corps de cette ecriture recalculee (doc 04 par. 7, seulement les composantes
     * changees, avec le cube et l'unite de cette ecriture) puis {@code updateBody}. Tout autre ecart (echelle
     * {@code Y}, translation) force seulement une reecriture.
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
        SpaceSystem system = env != null ? env.system(orbitSystem) : null;
        SpaceBody body = system != null ? system.body(orbitBody) : null;
        if (body == null || body.orbit() == null) return;
        SpaceOrbit orbit = body.orbit();
        if (rotated) {
            // R_o = R_cube^-1 . R_poignee
            HoloEditMath.rotation(orbitRotation, h);
            HoloEditMath.rotation(rotation, orbitCube).conjugate().mul(orbitRotation);
            setRotationYXZ(orbitScratch, rotation);
            orbit = orbit.withOrientation(orbitScratch.yaw(), orbitScratch.pitch(), orbitScratch.roll());
        }
        if (shaped) {
            double a = h.scaleX() / orbitUnit;
            double b = h.scaleZ() / orbitUnit;
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
            env.updateBody(orbitSystem, body.withOrbit(orbit));
        } catch (IllegalArgumentException ignored) {
            return;
        }
        if (map != null) map.refreshFrom(env);
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

    private static float clamp(double value, double limit)
    {
        return (float) Math.max(-limit, Math.min(limit, value));
    }

    private static boolean near(Vector3f a, Vector3f b)
    {
        return Math.abs(a.x - b.x) <= HANDLE_EPSILON && Math.abs(a.y - b.y) <= HANDLE_EPSILON
                && Math.abs(a.z - b.z) <= HANDLE_EPSILON;
    }

    private static float meanScale(HoloTransform t) { return (t.scaleX() + t.scaleY() + t.scaleZ()) / 3f; }

    /** Egalite exacte des neuf composantes. */
    private static boolean sameTransform(HoloTransform a, HoloTransform b)
    {
        return a.x() == b.x() && a.y() == b.y() && a.z() == b.z() && a.yaw() == b.yaw() && a.pitch() == b.pitch()
                && a.roll() == b.roll() && a.scaleX() == b.scaleX() && a.scaleY() == b.scaleY()
                && a.scaleZ() == b.scaleZ();
    }

    // ---------------------------------------------------------------- demandes du client

    /** Client : « Ajouter » (au centre de la carte galactique de la Map, selectionne ensuite). */
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

    /** Client : ajoute un corps de type {@code kind} autour de {@code parentId} dans le systeme {@code systemId}. */
    void requestAddBody(int systemId, SpaceBodyKind kind, int parentId)
    {
        sendTyped(ACTION_ADD_BODY, buf -> {
            buf.writeVarInt(systemId);
            buf.writeByte(kind.ordinal());
            buf.writeVarInt(parentId);
        });
    }

    /** Client : retire le corps {@code id} (et ses descendants) du systeme {@code systemId}. */
    void requestRemoveBody(int systemId, int id)
    {
        sendTyped(ACTION_REMOVE_BODY, buf -> {
            buf.writeVarInt(systemId);
            buf.writeVarInt(id);
        });
    }

    /** Client : renomme le corps {@code id}. */
    void requestBodyName(int systemId, int id, String name)
    {
        String clean = SpaceLimits.sanitizeName(name);
        sendUpdate(systemId, id, FIELD_NAME, buf -> buf.writeUtf(clean, MAX_NAME_READ));
    }

    /** Client : couleur du corps {@code id}. */
    void requestBodyColor(int systemId, int id, int color)
    {
        sendUpdate(systemId, id, FIELD_COLOR, buf -> buf.writeInt(color));
    }

    /**
     * Client : champ numerique ({@link #FIELD_RADIUS}, {@link #FIELD_LUMINOSITY}, {@link #FIELD_PERIOD},
     * {@link #FIELD_PHASE}, {@link #FIELD_MASS} ; rotation propre, T44 : {@link #FIELD_ROTATION_PERIOD},
     * {@link #FIELD_OBLIQUITY}, {@link #FIELD_NODE}, {@link #FIELD_ROTATION_PHASE}).
     */
    void requestBodyNumber(int systemId, int id, int field, double value)
    {
        sendUpdate(systemId, id, field, buf -> buf.writeDouble(value));
    }

    /** Client : texture du corps {@code id} ({@code auto}, {@code builtin:<id>} ou {@code custom:<sha1>} ; T44). */
    void requestBodyTexture(int systemId, int id, String texture)
    {
        sendUpdate(systemId, id, FIELD_TEXTURE, buf -> buf.writeUtf(texture, MAX_TEXTURE_READ));
    }

    /** Client : « Importer » (T45) : import d'une texture pour le corps {@code id}, par le backend. */
    void requestImport(int systemId, int id)
    {
        if (backend != null && host().clientSide()) backend.importTexture(this, systemId, id);
    }

    /**
     * Client (T45) : applique au corps {@code id} la texture importee {@code sha1} deja stockee par le serveur
     * (action 7, champ texture {@code custom:<sha1>}).
     */
    public void applyImportedTexture(int systemId, int id, String sha1)
    {
        requestBodyTexture(systemId, id, SpaceTextureRef.custom(sha1).serialized());
    }

    /** Client : « RESET » du corps {@code id} (D34). */
    void requestResetBody(int systemId, int id)
    {
        sendTyped(ACTION_RESET_BODY, buf -> {
            buf.writeVarInt(systemId);
            buf.writeVarInt(id);
        });
    }

    /** Client : « Se teleporter » pres du corps {@code id} (D34). */
    void requestTeleport(int systemId, int id)
    {
        sendTyped(ACTION_TELEPORT, buf -> {
            buf.writeVarInt(systemId);
            buf.writeVarInt(id);
        });
    }

    /** Client : vitesse de distorsion du saut, al/s (bornee par l'Environment). */
    void requestWarpSpeed(double value) { sendTyped(ACTION_WARP_SPEED, buf -> buf.writeDouble(value)); }

    /** Client : echelle de temps des astres, secondes simulees par tick (bornee par l'Environment). */
    void requestTimeScale(double value) { sendTyped(ACTION_TIME_SCALE, buf -> buf.writeDouble(value)); }

    /** Client : « Orbite » : la Map cadre sa vue sur l'orbite du corps {@code id}. */
    void requestFrameOrbit(int systemId, int id)
    {
        sendTyped(ACTION_FRAME_ORBIT, buf -> {
            buf.writeVarInt(systemId);
            buf.writeVarInt(id);
        });
    }

    private void sendUpdate(int systemId, int id, int field, Consumer<FriendlyByteBuf> value)
    {
        sendTyped(ACTION_UPDATE_BODY, buf -> {
            buf.writeVarInt(systemId);
            buf.writeVarInt(id);
            buf.writeByte(field);
            value.accept(buf);
        });
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
        buf.writeDouble(editTime);
        buf.writeBoolean(environment);
        if (!environment) return;
        buf.writeByte(forward);
        buf.writeDouble(warpSpeed);
        buf.writeDouble(timeScale);
    }

    /** Lecture tolerante : valeurs invalides = defaut. */
    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        double time = buf.readDouble();
        boolean present = buf.readBoolean();
        int newForward = -1;
        double newWarp = SpaceMotionSettings.DEFAULT_WARP_SPEED;
        double newScale = SpaceMotionSettings.DEFAULT_TIME_SCALE;
        if (present) {
            newForward = buf.readByte();
            if (newForward < -1 || newForward >= FORWARD_YAW.length) newForward = -1;
            double warp = buf.readDouble();
            if (Double.isFinite(warp)) newWarp = SpaceLimits.sanitizeWarpSpeed(warp);
            double scale = buf.readDouble();
            if (Double.isFinite(scale)) newScale = Math.max(0.0, Math.min(scale, SpaceMotionSettings.MAX_TIME_SCALE));
        }
        editTime = Double.isFinite(time) ? time : 0.0;
        environment = present;
        forward = newForward;
        warpSpeed = newWarp;
        timeScale = newScale;
    }

    /** Client : fenetre mise a jour (reglages recus). */
    @Override
    public void onSync() { refresh(); }

    private void refresh()
    {
        if (window != null) window.showState();
    }

    // ---------------------------------------------------------------- lecture (fenetre, renderers)

    /** Space Map du meme marker lue au dernier tick (deux cotes), {@code null} sans Map (C78). */
    public SpaceMapModule map() { return map; }

    /** Fenetre (deux cotes). */
    public SpaceConfiguratorWindow window() { return window; }

    /** Poignee de l'etoile selectionnee (deux cotes), {@code null} si elle n'existe pas. */
    public SpaceMapHandle handle() { return handle; }

    /** Poignee d'orbite du corps selectionne (deux cotes), {@code null} si elle n'existe pas. */
    public SpaceOrbitHandle orbitHandle() { return orbitHandle; }

    /** Temps fige de la poignee d'orbite {@code t_edit}, jours. */
    public double editTime() { return editTime; }

    /** Choix courant de l'axe avant du monde (indice de {@link #FORWARD_YAW}), {@code -1} = autre ou inconnu. */
    public int forwardChoice() { return forward; }

    /** Vitesse de distorsion du saut de l'Environment (copie), al/s ; defaut sans Environment. */
    public double warpSpeed() { return warpSpeed; }

    /** Echelle de temps des astres de l'Environment (copie), secondes simulees par tick ; defaut sans Environment. */
    public double timeScale() { return timeScale; }

    /** Vrai si la dimension du marker a un Space Environment (copie des reglages). */
    public boolean environmentPresent() { return environment; }

    /**
     * Vrai si le systeme selectionne dans la Map est hors de son cadrage galactique (la poignee est alors bornee au
     * bord du cube). Sans allocation.
     */
    public boolean selectedOutsideFrame()
    {
        SystemHeader header = map != null ? map.selectedHeader() : null;
        double half = map != null ? map.frameHalf() : 0.0;
        if (header == null || !(half > 0.0)) return false;
        double limit = (1.0 + HANDLE_EPSILON) * half;
        return Math.abs(header.x() - map.frameX()) > limit || Math.abs(header.y() - map.frameY()) > limit
                || Math.abs(header.z() - map.frameZ()) > limit;
    }
}
