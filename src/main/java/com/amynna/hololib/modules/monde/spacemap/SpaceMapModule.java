package com.amynna.hololib.modules.monde.spacemap;

import com.amynna.hololib.holo.edit.HoloEditMath;
import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.memory.position.PositionHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.module.HoloModuleHost;
import com.amynna.hololib.holo.object.HoloAccess;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceEditResult;
import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem.SystemHeader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Module {@code hololib:space_map} (memoire de position, marker ; T37, D20) : carte de la galaxie du Space Environment
 * de la dimension, <b>visible et utilisable par tous</b>. Un cube ({@link SpaceMapCube}) montre la carte galactique ou
 * un systeme, une fenetre de navigation separee ({@link SpaceMapWindow}) liste les astres en arborescence de la
 * gravite. Il ne stocke <b>pas</b> la galaxie : il en garde une copie, recopiee depuis l'Environment quand sa revision
 * change, et synchronisee a ses observateurs (meme politique que le Configurator des lots 1 et 2).
 * <p>
 * <b>Vue partagee</b> (C77) : l'etat de navigation est celui du marker, le meme pour tous les joueurs, sauvegarde et
 * synchronise : mode (galaxie / systeme), systeme affiche (vue systeme ; deplie dans l'arborescence en vue galaxie, ses
 * corps sont alors copies), selection (systeme ; corps du systeme affiche), centre et demi-etendue de la carte
 * galactique (al), corps au centre et demi-etendue de la vue systeme (UA, zoom continu borne), origine des coordonnees
 * (C50). Le recentrage sur une ligne de l'arborescence = selection + centre : un systeme recentre la carte galactique
 * sur lui (zoom garde) ; un corps passe en vue systeme centree sur lui, a la demi-etendue de
 * {@link SpaceSystemScale#framingHalf} (le zoom sur un corps de T25 en est un cas). Le zoom multiplie ou divise la
 * demi-etendue par {@value #ZOOM_STEP} par pas, vers le centre.
 * <p>
 * <b>Temps</b> : la vue systeme montre les corps au temps <b>vivant</b> de l'Environment (client :
 * {@code displayDays(partialTick)} du miroir ; serveur : {@code days()}), donc le vaisseau a sa vraie place ; pas de
 * temps fige ni d'animation locale (outils d'edition du Configurator).
 * <p>
 * <b>Etat synchronise</b> ({@link #writeSync}) :
 * <pre>
 * byte mode (ordinal), varint systeme affiche (0 = aucun), varint systeme selectionne, varint corps selectionne,
 * varint corps au centre (0 = origine du systeme), double demi-etendue systeme (UA),
 * double cx, double cy, double cz, double H (carte galactique, al), varint origine (0 = defaut),
 * boolean environment
 * si environment : int revision copiee, varint systeme courant,
 *                  en-tetes de tous les systemes (SystemHeader.writeList),
 *                  boolean systeme affiche [+ SpaceSystem.write]
 * </pre>
 * Envoye a chaque changement de revision de l'Environment (aussitot) et apres un changement de vue, au plus une fois
 * toutes les {@value #VIEW_SYNC_TICKS} ticks (molette tournee : un envoi par fenetre de 4 ticks au plus) ; jamais par
 * tick sinon.
 * <p>
 * <b>Actions</b> ({@code sendAction(source, ...)}, source = le cube ou la fenetre, premier octet = type), acceptees de
 * tout joueur a portee (acces {@code EVERYONE}, distance verifiee par le framework) :
 * <pre>
 * 0 GALAXY_VIEW                  -> carte galactique d'ensemble, aucune selection (ligne « Galaxie »)
 * 1 SELECT_SYSTEM + varint id    -> carte galactique, systeme selectionne et centre (0 = aucune selection)
 * 2 DISPLAY + varint id          -> systeme deplie dans l'arborescence (ses corps copies) ; depuis la vue systeme d'un
 *                                   autre systeme : retour a la carte galactique centree sur lui ; 0 = replie
 * 3 SELECT_BODY + varint systeme + varint corps
 *                                -> vue systeme, corps selectionne et au centre, demi-etendue de cadrage
 *                                   (0 = systeme entier)
 * 4 MODE + byte ordinal          -> GALAXY (recentree sur le systeme quitte) ou SYSTEM (systeme selectionne, sinon
 *                                   affiche ; vue entiere)
 * 5 ZOOM + byte crans            -> demi-etendue / ZOOM_STEP^crans (positif = rapproche), bornee
 * 6 REFRAME                      -> vue d'ensemble : galaxie entiere, ou systeme entier centre sur son origine
 * 7 ORIGIN + varint id           -> origine des coordonnees (0 = defaut)
 * 8 TARGET + boolean             -> true : la selection courante de cette Map devient la cible du vaisseau (T43) ;
 *                                   false : la cible est retiree si c'est la selection courante
 * </pre>
 * Un refus (systeme ou corps inconnu, pas d'Environment) est affiche au joueur dans la barre d'action.
 * <p>
 * <b>Cible du vaisseau</b> (T43, D33) : tenue par l'Environment ({@link SpaceEnvironmentModule#setTarget}). La
 * selection ciblable ({@link #selectionTargetSystem()}, {@link #selectionTargetBody()}) est celle de la ligne
 * selectionnee de l'arborescence : en vue systeme, le corps selectionne ; sur la carte galactique, le systeme
 * selectionne (systeme entier) ; jamais « Galaxie ». La Map retient la cible qu'elle a posee (serveur, sauvegardee :
 * cles {@code targetSystem}, {@code targetBody}) ; a chaque tick serveur, si sa selection ciblable n'est plus cette
 * cible (ligne cliquee, « Galaxie », mode, {@link #focusSystem} / {@link #selectInView} / {@link #frameOrbit}, corps ou
 * systeme disparu), elle retire la cible du vaisseau <b>si elle est encore celle qu'elle a posee</b> ; une cible posee
 * depuis une autre Map n'est pas touchee (et la Map oublie la sienne des que la cible du vaisseau change).
 * <p>
 * <b>Cube</b> : sonde chaque tick serveur (pas de callback d'edition) ; echelle ramenee a la moyenne si elle n'est plus
 * uniforme (C11). <b>Fenetre</b> : placee a sa creation devant la base du cube par defaut (aussi son transform de
 * RESET), puis independante (transform sauvegarde et synchronise par le framework).
 * <p>
 * <b>Space Configurator du meme marker</b> (T42, D20, C78) : il trouve la Map par {@link #of(HoloModule)} (table
 * des Maps chargees par memoire, une par cote, sans allocation) et lit : selection ({@link #selectedSystem()},
 * {@link #selectedBody()}, {@link #selectedHeader()}, {@link #selectedBodyData()}), systeme affiche
 * ({@link #shownSystem()}), copie de la galaxie ({@link #headers()}, {@link #copiedRevision()}), repere du cube et de
 * la vue ({@link #cube()}, {@link #cubeToMarker}, {@link #galaxyPoint}, {@link #bodyPoints}, {@link #centerIndex()},
 * {@link #viewHalf()}, {@link #frameX()}..{@link #frameHalf()}), temps de la vue ({@link #viewDays}). Cote serveur, il
 * rafraichit la copie avant de la lire ({@link #refreshFrom}) et, apres une de ses editions, oriente la vue :
 * systeme ajoute selectionne et centre ({@link #focusSystem}), corps ajoute selectionne ({@link #selectInView}),
 * « Orbite » ({@link #frameOrbit} : vue centree sur le parent du corps, son orbite entiere dans le cube). Cote client,
 * il masque l'orbite que sa poignee dessine ({@link #hideOrbit}, lu par le renderer : {@link #hiddenOrbitBody()}).
 */
public final class SpaceMapModule extends HoloModule implements SpaceMapView
{
    /** Mode de la carte. */
    public enum Mode
    {
        /** Le cube montre la carte galactique. */
        GALAXY,
        /** Le cube montre le systeme affiche. */
        SYSTEM
    }

    /** Type d'action : carte galactique d'ensemble, sans selection. */
    public static final int ACTION_GALAXY_VIEW = 0;
    /** Type d'action : selection et centrage d'un systeme sur la carte galactique (varint id). */
    public static final int ACTION_SELECT_SYSTEM = 1;
    /** Type d'action : systeme deplie dans l'arborescence (varint id, 0 = replie). */
    public static final int ACTION_DISPLAY = 2;
    /** Type d'action : selection et centrage d'un corps (varint systeme, varint corps ; 0 = systeme entier). */
    public static final int ACTION_SELECT_BODY = 3;
    /** Type d'action : mode (octet : ordinal de {@link Mode}). */
    public static final int ACTION_MODE = 4;
    /** Type d'action : zoom (octet signe : crans, positif = rapproche). */
    public static final int ACTION_ZOOM = 5;
    /** Type d'action : vue d'ensemble. */
    public static final int ACTION_REFRAME = 6;
    /** Type d'action : origine des coordonnees (varint id d'un systeme, 0 = defaut). */
    public static final int ACTION_ORIGIN = 7;
    /** Type d'action : cible du vaisseau (booleen : vrai = poser la selection courante, faux = la retirer), T43. */
    public static final int ACTION_TARGET = 8;

    /** Facteur d'un pas de zoom. */
    public static final double ZOOM_STEP = 1.5;
    /** Crans au plus par action de zoom. */
    public static final int MAX_ZOOM_STEPS = 8;
    /** Bornes de la demi-etendue de la carte galactique, al. */
    public static final double MIN_FRAME_HALF = 0.05;
    public static final double MAX_FRAME_HALF = 2.0 * SpaceLimits.MAX_GALACTIC_DISTANCE;
    /** Demi-etendue minimale d'une vue d'ensemble galactique, al. */
    public static final double MIN_REFRAME_HALF = 1.0;
    /** Marge de la vue d'ensemble galactique : {@code H = nice(REFRAME_MARGIN x demi-etendue max)}. */
    public static final double REFRAME_MARGIN = 1.1;
    /** Ticks au moins entre deux envois dus a un changement de vue. */
    public static final int VIEW_SYNC_TICKS = 4;
    /** Ecart entre la fenetre et la face avant du cube (placement initial), en blocs. */
    public static final float WINDOW_GAP = 0.25f;
    /** Ecart relatif tolere entre les echelles du cube avant de les uniformiser. */
    private static final float UNIFORM_EPSILON = 1e-4f;
    private static final Component UNKNOWN_SYSTEM = Component.literal("Systeme inconnu");
    private static final Component UNKNOWN_BODY = Component.literal("Corps inconnu");
    private static final Component NO_SELECTION = Component.literal("Selectionner d'abord un systeme");
    private static final Component NOTHING_TO_TARGET = Component.literal("Selectionner d'abord un astre");
    private static final Component NO_ENVIRONMENT =
            Component.literal("Aucun Space Environment dans cette dimension");
    /**
     * Maps chargees, par portee (memoire du marker), une table par cote (T42) : le Configurator du meme marker y
     * trouve la sienne sans parcourir le conteneur (aucune allocation par tick). Remplie par {@code onLoad}, videe par
     * {@code onUnload} ; cote serveur, une entree est confirmee par le conteneur du marker ({@link #of}).
     */
    private static final Map<HoloScope, SpaceMapModule> SERVER_MAPS = new ConcurrentHashMap<>();
    private static final Map<HoloScope, SpaceMapModule> CLIENT_MAPS = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------- etat sauvegarde et synchronise

    private Mode mode = Mode.GALAXY;
    /** Systeme affiche (vue systeme) ou deplie (arborescence), {@code 0} = aucun. */
    private int system;
    /** Systeme selectionne, {@code 0} = aucun. */
    private int selectedSystem;
    /** Corps selectionne du systeme affiche, {@code 0} = aucun. */
    private int selectedBody;
    /** Corps au centre de la vue systeme, {@code 0} = origine du systeme. */
    private int centerBody;
    /** Demi-etendue de la vue systeme, UA ; {@code <= 0} : a calculer (systeme entier). */
    private double systemHalf;
    /** Centre de la carte galactique, al. */
    private double frameX;
    private double frameY;
    private double frameZ;
    /** Demi-etendue de la carte galactique, al ; {@code <= 0} : vue d'ensemble a la premiere copie. */
    private double frameHalf;
    /** Origine des coordonnees ({@code 0} = defaut, {@link SpaceCoordinates#origin}). */
    private int origin;

    // ---------------------------------------------------------------- copie synchronisee (recalculee)

    private boolean environment;
    private int copiedRevision;
    private int currentSystemId;
    private List<SystemHeader> headers = List.of();
    /** Copie complete du systeme affiche, {@code null} si aucun. */
    private SpaceSystem shown;

    // ---------------------------------------------------------------- serveur

    /** Environment de la derniere copie (une nouvelle instance force une recopie). */
    private SpaceEnvironmentModule lastEnvironment;
    private boolean copied;
    /** Vue changee depuis le dernier envoi (envoi limite a un par {@link #VIEW_SYNC_TICKS} ticks). */
    private boolean viewDirty;
    private int ticksSinceSync = VIEW_SYNC_TICKS;
    /**
     * Cible du vaisseau posee par cette Map (T43), {@code 0} = aucune : retiree de l'Environment quand la selection
     * ciblable change, si elle est encore la cible du vaisseau. Sauvegardee, non synchronisee.
     */
    private int postedSystem;
    private int postedBody;

    // ---------------------------------------------------------------- deux cotes (une instance par cote)

    private SpaceMapCube cube;
    private SpaceMapWindow window;
    /** Derniere valeur vue du transform du cube (sondage). */
    private final HoloTransform lastCube = new HoloTransform();
    private boolean cubeKnown;
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f axis = new Vector3f();
    /** Copie et corps dont l'indice du centre a ete calcule ({@link #centerIndex()}). */
    private SpaceSystem indexedSystem;
    private int indexedBody;
    private int centerIndex = -1;

    // ---------------------------------------------------------------- client

    /** Corps dont l'orbite est dessinee par la poignee d'orbite du Configurator du meme marker (T42), {@code 0} sinon. */
    private int hiddenOrbit;

    // ---------------------------------------------------------------- cycle de vie

    /**
     * Cree le cube (acces {@code EVERYONE} : molette) et la fenetre (devant la base du cube par defaut : placement
     * initial et transform de RESET), deux cotes, memes cles ; le framework restaure ensuite les placements
     * sauvegardes / recus.
     */
    @Override
    public void onLoad()
    {
        cube = addObject(new SpaceMapCube("cube"));
        cube.setAccess(HoloAccess.EVERYONE);
        HoloTransform initial = new HoloTransform();
        windowPlacement(cube.transform(), initial);
        window = addObject(new SpaceMapWindow("window", this, initial));
        maps(host()).put(host().scope(), this);
        refresh();
    }

    @Override
    public void onUnload()
    {
        maps(host()).remove(host().scope(), this);
        lastEnvironment = null;
        copied = false;
        hiddenOrbit = 0;
    }

    private static Map<HoloScope, SpaceMapModule> maps(HoloModuleHost host)
    {
        return host.clientSide() ? CLIENT_MAPS : SERVER_MAPS;
    }

    /**
     * Space Map de la meme memoire de position que {@code module} (meme marker, meme cote), {@code null} s'il n'y en a
     * pas (T42 : utilise par le Space Configurator, deux cotes). Serveur : l'entree de la table n'est rendue que si le
     * conteneur du marker contient bien ce module ({@code HoloContainer.module(id)} : une Map d'un monde quitte sans
     * dechargement est ignoree). Sans allocation.
     */
    public static SpaceMapModule of(HoloModule module)
    {
        HoloModuleHost host = module.host();
        if (host == null || !(host.scope() instanceof HoloScope.Position position)) return null;
        SpaceMapModule map = maps(host).get(position);
        if (map == null || host.clientSide()) return map;
        PositionHoloContainer container = HoloContainers.position(position.markerId());
        return container != null && container.module(map.instanceId()) == map ? map : null;
    }

    @Override
    public void save(CompoundTag tag)
    {
        tag.putString("mode", mode.name());
        tag.putInt("system", system);
        tag.putInt("selected", selectedSystem);
        tag.putInt("body", selectedBody);
        tag.putInt("center", centerBody);
        tag.putDouble("half", systemHalf);
        tag.putInt("origin", origin);
        CompoundTag frame = new CompoundTag();
        frame.putDouble("x", frameX);
        frame.putDouble("y", frameY);
        frame.putDouble("z", frameZ);
        frame.putDouble("h", frameHalf);
        tag.put("frame", frame);
        if (postedSystem != 0) {
            tag.putInt("targetSystem", postedSystem);
            tag.putInt("targetBody", postedBody);
        }
    }

    /** Lecture tolerante : valeur invalide = defaut (vue d'ensemble a la premiere copie). */
    @Override
    public void load(CompoundTag tag)
    {
        mode = Mode.SYSTEM.name().equals(tag.getString("mode")) ? Mode.SYSTEM : Mode.GALAXY;
        system = Math.max(0, tag.getInt("system"));
        selectedSystem = Math.max(0, tag.getInt("selected"));
        selectedBody = Math.max(0, tag.getInt("body"));
        centerBody = Math.max(0, tag.getInt("center"));
        systemHalf = tag.getDouble("half");
        if (!(systemHalf > 0.0) || !Double.isFinite(systemHalf)) systemHalf = 0.0;
        else systemHalf = SpaceSystemScale.clampViewHalf(systemHalf);
        origin = Math.max(0, tag.getInt("origin"));
        if (system == 0) mode = Mode.GALAXY;
        CompoundTag frame = tag.getCompound("frame");
        frameX = frame.getDouble("x");
        frameY = frame.getDouble("y");
        frameZ = frame.getDouble("z");
        frameHalf = frame.contains("h", Tag.TAG_DOUBLE) ? frame.getDouble("h") : 0.0;
        if (!validFrame(frameX, frameY, frameZ, frameHalf)) {
            frameX = frameY = frameZ = 0.0;
            frameHalf = 0.0;
        }
        // Cible posee par cette Map (T43) ; absente (sauvegarde plus ancienne) = aucune.
        postedSystem = Math.max(0, tag.getInt("targetSystem"));
        postedBody = postedSystem == 0 ? 0 : Math.max(0, tag.getInt("targetBody"));
    }

    private static boolean validFrame(double x, double y, double z, double half)
    {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Double.isFinite(half)
                && half >= MIN_FRAME_HALF && half <= MAX_FRAME_HALF;
    }

    // ---------------------------------------------------------------- serveur

    /**
     * Copie de l'Environment si sa revision a change, cible posee retiree si la selection a change, sondage du cube,
     * envoi limite d'une vue changee.
     */
    @Override
    public void serverTick(ServerLevel level)
    {
        SpaceEnvironmentModule env = SpaceEnvironmentModule.of(level);
        updateCopy(env);
        checkPostedTarget(env);
        pollCube();
        if (ticksSinceSync < VIEW_SYNC_TICKS) ticksSinceSync++;
        if (viewDirty && ticksSinceSync >= VIEW_SYNC_TICKS) sync();
    }

    /** Envoi de tout l'etat a la fin du tick. */
    private void sync()
    {
        markDirty();
        viewDirty = false;
        ticksSinceSync = 0;
    }

    /** Recopie depuis {@code env} si c'est un autre Environment ou si sa revision a change (envoi aussitot). */
    private void updateCopy(SpaceEnvironmentModule env)
    {
        boolean changed = !copied || env != lastEnvironment
                || env != null && env.galaxyRevision() != copiedRevision;
        lastEnvironment = env;
        if (!changed) return;
        copied = true;
        copy(env);
        sync();
    }

    /**
     * Copie des en-tetes de tous les systemes et du systeme affiche ; selection, centre, systeme affiche et origine
     * invalides (retires) remis a {@code 0}, vue systeme sans systeme = carte galactique. Sans Environment, la vue est
     * gardee (il peut revenir, ou ne pas etre encore charge).
     */
    private void copy(SpaceEnvironmentModule env)
    {
        if (env == null) {
            environment = false;
            copiedRevision = 0;
            currentSystemId = 0;
            headers = List.of();
            shown = null;
            return;
        }
        environment = true;
        copiedRevision = env.galaxyRevision();
        currentSystemId = env.currentSystemId();
        List<SpaceSystem> systems = env.galaxy();
        List<SystemHeader> list = new ArrayList<>(systems.size());
        for (SpaceSystem item : systems) list.add(item.header());
        headers = List.copyOf(list);
        if (selectedSystem != 0 && env.system(selectedSystem) == null) selectedSystem = 0;
        if (origin != 0 && env.system(origin) == null) origin = 0;
        shown = system != 0 ? env.system(system) : null;
        if (shown == null) {
            system = 0;
            mode = Mode.GALAXY;
        }
        if (shown == null || shown.body(selectedBody) == null) selectedBody = 0;
        if (shown == null || shown.body(centerBody) == null) centerBody = 0;
        if (shown != null && !(systemHalf > 0.0)) systemHalf = SpaceSystemScale.halfExtent(shown);
        if (frameHalf <= 0.0) reframeGalaxy();
    }

    /**
     * Sondage du cube : a chaque changement de son transform (edition d'un operateur, RESET), echelle uniformisee
     * (moyenne des trois axes, C11) ; le transform modifie est envoye par le framework au tick suivant.
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

    /** Source = le cube ou la fenetre ; contenu valide contre l'Environment de la dimension du joueur. */
    @Override
    public void handleAction(ServerPlayer player, HoloObject source, FriendlyByteBuf buf)
    {
        if (source == null || source != cube && source != window) return;
        int action = buf.readByte();
        SpaceEnvironmentModule env = SpaceEnvironmentModule.of(player.serverLevel());
        updateCopy(env);
        switch (action) {
            case ACTION_GALAXY_VIEW -> galaxyView();
            case ACTION_SELECT_SYSTEM -> selectSystem(player, env, buf.readVarInt());
            case ACTION_DISPLAY -> display(player, env, buf.readVarInt());
            case ACTION_SELECT_BODY -> {
                int systemId = buf.readVarInt();
                selectBody(player, env, systemId, buf.readVarInt());
            }
            case ACTION_MODE -> {
                int ordinal = buf.readByte();
                if (ordinal >= 0 && ordinal < Mode.values().length) setMode(player, env, Mode.values()[ordinal]);
            }
            case ACTION_ZOOM -> zoom(buf.readByte());
            case ACTION_REFRAME -> reframe();
            case ACTION_ORIGIN -> setOrigin(player, env, buf.readVarInt());
            case ACTION_TARGET -> target(player, env, buf.readBoolean());
            default -> {
            }
        }
    }

    /** « Galaxie » : carte galactique d'ensemble, sans selection. */
    private void galaxyView()
    {
        mode = Mode.GALAXY;
        selectedSystem = 0;
        selectedBody = 0;
        centerBody = 0;
        reframeGalaxy();
        viewDirty = true;
    }

    /** Selection d'un systeme : carte galactique centree sur lui (zoom garde) ; {@code 0} = plus de selection. */
    private void selectSystem(ServerPlayer player, SpaceEnvironmentModule env, int id)
    {
        if (id < 0) return;
        SpaceSystem target = null;
        if (id != 0) {
            target = env != null ? env.system(id) : null;
            if (target == null) {
                player.displayClientMessage(env == null ? NO_ENVIRONMENT : UNKNOWN_SYSTEM, true);
                return;
            }
        }
        applySelectSystem(id, target);
    }

    private void applySelectSystem(int id, SpaceSystem target)
    {
        if (mode == Mode.SYSTEM) {
            mode = Mode.GALAXY;
            selectedBody = 0;
            centerBody = 0;
        }
        selectedSystem = id;
        if (target != null) centerOn(target);
        viewDirty = true;
    }

    // ---------------------------------------------------------------- vue orientee par le Configurator (serveur, T42)

    /**
     * Serveur : recopie depuis {@code env} si c'est un autre Environment ou si sa revision a change (envoi aussitot) ;
     * le Configurator l'appelle avant de lire la copie et apres chacune de ses editions (copie a jour dans le tick).
     */
    public void refreshFrom(SpaceEnvironmentModule env) { updateCopy(env); }

    /**
     * Serveur : selectionne le systeme {@code id} et centre la carte galactique sur lui, comme un clic dans
     * l'arborescence (systeme ajoute par le Configurator) ; sans effet s'il est inconnu de l'Environment copie.
     */
    public void focusSystem(int id)
    {
        SpaceSystem target = lastEnvironment != null && id > 0 ? lastEnvironment.system(id) : null;
        if (target != null) applySelectSystem(id, target);
    }

    /**
     * Serveur : selectionne le corps {@code bodyId} sans changer le cadrage, si la vue systeme montre le systeme
     * {@code systemId} et que la copie contient ce corps (corps ajoute par le Configurator).
     */
    public void selectInView(int systemId, int bodyId)
    {
        if (!systemView() || shown.id() != systemId || bodyId == selectedBody || shown.body(bodyId) == null) return;
        selectedBody = bodyId;
        viewDirty = true;
    }

    /**
     * Serveur : vue systeme de {@code systemId} centree sur le parent du corps {@code bodyId}, corps selectionne,
     * demi-etendue {@code nice(1,1 x apoapside)} : son orbite entiere tient dans le cube et ne derive pas avec le temps
     * vivant (bouton « Orbite » du Configurator). Faux (rien ne change) si le corps est inconnu ou sans orbite.
     */
    public boolean frameOrbit(int systemId, int bodyId)
    {
        SpaceSystem target = lastEnvironment != null && systemId > 0 ? lastEnvironment.system(systemId) : null;
        int index = target != null && bodyId > 0 ? target.indexOf(bodyId) : -1;
        int parent = index >= 0 ? target.parentIndex(index) : -1;
        SpaceBody body = index >= 0 ? target.bodies().get(index) : null;
        if (parent < 0 || body.orbit() == null) return false;
        mode = Mode.SYSTEM;
        system = systemId;
        shown = target;
        selectedSystem = systemId;
        selectedBody = bodyId;
        centerBody = target.bodies().get(parent).id();
        systemHalf = SpaceSystemScale.clampViewHalf(
                SpaceSystemScale.nice(SpaceSystemScale.MARGIN * body.orbit().apoapsis()));
        viewDirty = true;
        return true;
    }

    /** Client : orbite du corps {@code bodyId} dessinee par la poignee d'orbite du Configurator ({@code 0} = aucune). */
    public void hideOrbit(int bodyId) { hiddenOrbit = bodyId; }

    /** Orbite omise par le renderer : celle que dessine la poignee d'orbite du Configurator (client, operateurs). */
    @Override
    public int hiddenOrbitBody() { return hiddenOrbit; }

    /**
     * Systeme deplie : ses corps sont copies. Depuis la vue systeme d'un autre systeme, retour a la carte galactique
     * selectionnee et centree sur lui. {@code 0} replie (carte galactique seulement).
     */
    private void display(ServerPlayer player, SpaceEnvironmentModule env, int id)
    {
        if (id < 0 || id == system) return;
        if (id == 0) {
            if (mode == Mode.SYSTEM) return;
            system = 0;
            shown = null;
            selectedBody = 0;
            centerBody = 0;
            viewDirty = true;
            return;
        }
        SpaceSystem target = env != null ? env.system(id) : null;
        if (target == null) {
            player.displayClientMessage(env == null ? NO_ENVIRONMENT : UNKNOWN_SYSTEM, true);
            return;
        }
        if (mode == Mode.SYSTEM) {
            mode = Mode.GALAXY;
            selectedSystem = id;
            centerOn(target);
        }
        system = id;
        shown = target;
        selectedBody = 0;
        centerBody = 0;
        systemHalf = SpaceSystemScale.halfExtent(target);
        viewDirty = true;
    }

    /**
     * Recentrage sur un corps : vue systeme du systeme {@code systemId}, corps selectionne et au centre, demi-etendue
     * de cadrage ({@link SpaceSystemScale#framingHalf}) ; {@code bodyId = 0} : systeme entier, sans selection.
     */
    private void selectBody(ServerPlayer player, SpaceEnvironmentModule env, int systemId, int bodyId)
    {
        if (systemId <= 0 || bodyId < 0) return;
        SpaceSystem target = env != null ? env.system(systemId) : null;
        if (target == null) {
            player.displayClientMessage(env == null ? NO_ENVIRONMENT : UNKNOWN_SYSTEM, true);
            return;
        }
        int index = bodyId == 0 ? -1 : target.indexOf(bodyId);
        if (bodyId != 0 && index < 0) {
            player.displayClientMessage(UNKNOWN_BODY, true);
            return;
        }
        mode = Mode.SYSTEM;
        system = systemId;
        shown = target;
        selectedSystem = systemId;
        selectedBody = bodyId;
        centerBody = bodyId;
        systemHalf = SpaceSystemScale.clampViewHalf(SpaceSystemScale.framingHalf(target, index));
        viewDirty = true;
    }

    /**
     * Mode : la carte galactique se recentre sur le systeme quitte ; la vue systeme montre le systeme selectionne
     * (sinon le systeme deplie) en entier.
     */
    private void setMode(ServerPlayer player, SpaceEnvironmentModule env, Mode requested)
    {
        if (requested == mode) return;
        if (requested == Mode.GALAXY) {
            mode = Mode.GALAXY;
            selectedSystem = system;
            selectedBody = 0;
            centerBody = 0;
            if (shown != null) centerOn(shown);
            viewDirty = true;
            return;
        }
        if (env == null) {
            player.displayClientMessage(NO_ENVIRONMENT, true);
            return;
        }
        int id = selectedSystem != 0 ? selectedSystem : system;
        if (id == 0) {
            player.displayClientMessage(NO_SELECTION, true);
            return;
        }
        selectBody(player, env, id, 0);
    }

    /** Zoom de {@code steps} crans vers le centre (positif = rapproche), borne. */
    private void zoom(int steps)
    {
        int clamped = Math.max(-MAX_ZOOM_STEPS, Math.min(MAX_ZOOM_STEPS, steps));
        if (clamped == 0) return;
        double factor = Math.pow(ZOOM_STEP, -clamped);
        if (mode == Mode.SYSTEM) {
            double half = SpaceSystemScale.clampViewHalf(viewHalf() * factor);
            if (half == systemHalf) return;
            systemHalf = half;
        } else {
            double base = frameHalf > 0.0 ? frameHalf : MIN_REFRAME_HALF;
            double half = Math.max(MIN_FRAME_HALF, Math.min(MAX_FRAME_HALF, base * factor));
            if (half == frameHalf) return;
            frameHalf = half;
        }
        viewDirty = true;
    }

    /** Vue d'ensemble : galaxie entiere, ou systeme entier centre sur son origine (selection gardee). */
    private void reframe()
    {
        if (mode == Mode.SYSTEM && shown != null) {
            centerBody = 0;
            systemHalf = SpaceSystemScale.halfExtent(shown);
        } else {
            reframeGalaxy();
        }
        viewDirty = true;
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
        viewDirty = true;
    }

    // ---------------------------------------------------------------- cible du vaisseau (serveur, T43)

    /**
     * Action 8 : {@code set} = la selection ciblable devient la cible du vaisseau (retenue comme posee par cette
     * Map) ; sinon la cible est retiree si c'est la selection ciblable. Rien a cibler ou pas d'Environment : message.
     */
    private void target(ServerPlayer player, SpaceEnvironmentModule env, boolean set)
    {
        if (env == null) {
            player.displayClientMessage(NO_ENVIRONMENT, true);
            return;
        }
        int targetSystem = selectionTargetSystem();
        int targetBody = selectionTargetBody();
        if (targetSystem == 0) {
            player.displayClientMessage(NOTHING_TO_TARGET, true);
            return;
        }
        if (!set) {
            if (env.isTarget(targetSystem, targetBody)) env.clearTarget();
            if (postedSystem == targetSystem && postedBody == targetBody) forgetPosted();
            return;
        }
        SpaceEditResult result = env.setTarget(targetSystem, targetBody);
        if (!result.success()) {
            player.displayClientMessage(result.message(), true);
            return;
        }
        if (postedSystem != targetSystem || postedBody != targetBody) {
            postedSystem = targetSystem;
            postedBody = targetBody;
            // Seul moyen de faire sauvegarder la memoire du marker (le framework sauve sur markDirty).
            viewDirty = true;
        }
    }

    /**
     * Chaque tick serveur : la cible posee par cette Map est oubliee si elle n'est plus la cible du vaisseau (posee
     * ailleurs, retiree, astre disparu), et retiree du vaisseau si la selection ciblable a change. Sans Environment,
     * rien (il peut ne pas etre encore charge).
     */
    private void checkPostedTarget(SpaceEnvironmentModule env)
    {
        if (postedSystem == 0 || env == null) return;
        if (!env.isTarget(postedSystem, postedBody)) {
            forgetPosted();
            return;
        }
        if (selectionTargetSystem() == postedSystem && selectionTargetBody() == postedBody) return;
        env.clearTarget();
        forgetPosted();
    }

    private void forgetPosted()
    {
        postedSystem = 0;
        postedBody = 0;
        viewDirty = true;
    }

    /**
     * Systeme de la selection ciblable (ligne selectionnee de l'arborescence) : en vue systeme, celui du corps
     * selectionne ({@code 0} sans corps selectionne) ; sur la carte galactique, le systeme selectionne ; {@code 0} =
     * rien a cibler. Deux cotes.
     */
    public int selectionTargetSystem()
    {
        if (systemView()) return selectedBody != 0 ? shown.id() : 0;
        return selectedSystem;
    }

    /** Corps de la selection ciblable : le corps selectionne en vue systeme, {@code 0} (systeme entier) sinon. */
    public int selectionTargetBody() { return systemView() ? selectedBody : 0; }

    /** Centre de la carte galactique sur un systeme (demi-etendue gardee). */
    private void centerOn(SpaceSystem target)
    {
        frameX = target.x();
        frameY = target.y();
        frameZ = target.z();
        if (frameHalf <= 0.0) frameHalf = MIN_REFRAME_HALF;
    }

    /**
     * Vue d'ensemble galactique ({@code docs/space/04-mathematiques.md} par. 7) : centre = centre de la boite
     * englobante des systemes copies, demi-etendue = {@code nice(1,1 x demi-etendue max)}, au moins
     * {@value #MIN_REFRAME_HALF} al.
     */
    private void reframeGalaxy()
    {
        if (headers.isEmpty()) {
            frameX = frameY = frameZ = 0.0;
            frameHalf = MIN_REFRAME_HALF;
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
        frameHalf = Math.min(MAX_FRAME_HALF,
                Math.max(MIN_REFRAME_HALF, SpaceSystemScale.nice(REFRAME_MARGIN * half)));
    }

    // ---------------------------------------------------------------- placement initial de la fenetre (deux cotes)

    /**
     * Placement de la fenetre devant la base du cube {@code t}, ecrit dans {@code dest} (translation et rotation ;
     * echelle inchangee) : tournee comme le cube en lacet seulement (sa face {@code +Z} regarde dans la direction avant
     * du cube {@code f = (-sin yaw, 0, cos yaw)}), a {@value #WINDOW_GAP} bloc de l'enveloppe du cube dans cette
     * direction, bord bas au niveau du point le plus bas du cube (meme regle que la fenetre du Configurator, T24).
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
        dest.setTranslation(t.x() + fx * distance, t.y() - vertical + SpaceMapWindow.HALF_HEIGHT,
                t.z() + fz * distance);
        dest.setRotation(t.yaw(), 0f, 0f);
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

    /** Client : « Galaxie » (carte galactique d'ensemble, sans selection). */
    void requestGalaxyView() { sendTyped(window, ACTION_GALAXY_VIEW, null); }

    /** Client : selectionne et centre le systeme {@code id} sur la carte galactique ({@code 0} = aucun). */
    void requestSelectSystem(int id) { sendTyped(window, ACTION_SELECT_SYSTEM, buf -> buf.writeVarInt(id)); }

    /** Client : deplie le systeme {@code id} ({@code 0} = replie). */
    void requestDisplay(int id) { sendTyped(window, ACTION_DISPLAY, buf -> buf.writeVarInt(id)); }

    /** Client : recentre la vue systeme de {@code systemId} sur le corps {@code bodyId} ({@code 0} = systeme entier). */
    void requestSelectBody(int systemId, int bodyId)
    {
        sendTyped(window, ACTION_SELECT_BODY, buf -> {
            buf.writeVarInt(systemId);
            buf.writeVarInt(bodyId);
        });
    }

    /** Client : change de mode. */
    void requestMode(Mode requested) { sendTyped(window, ACTION_MODE, buf -> buf.writeByte(requested.ordinal())); }

    /** Client : zoom de {@code steps} crans depuis la fenetre (positif = rapproche). */
    void requestZoom(int steps) { sendZoom(window, steps); }

    /** Client : vue d'ensemble. */
    void requestReframe() { sendTyped(window, ACTION_REFRAME, null); }

    /** Client : origine des coordonnees = systeme {@code id} ({@code 0} = defaut). */
    void requestOrigin(int id) { sendTyped(window, ACTION_ORIGIN, buf -> buf.writeVarInt(id)); }

    /** Client : cible du vaisseau = selection courante ({@code true}) ou retiree ({@code false}), T43. */
    void requestTarget(boolean set) { sendTyped(window, ACTION_TARGET, buf -> buf.writeBoolean(set)); }

    /** Molette sur le cube : la Space Map zoome. */
    @Override
    public boolean zoomable() { return true; }

    /** Client : zoom de {@code steps} crans demande depuis le cube (molette). */
    @Override
    public void requestZoom(SpaceMapCube source, int steps) { sendZoom(source, steps); }

    private void sendZoom(HoloObject source, int steps)
    {
        int clamped = Math.max(-MAX_ZOOM_STEPS, Math.min(MAX_ZOOM_STEPS, steps));
        if (clamped != 0) sendTyped(source, ACTION_ZOOM, buf -> buf.writeByte(clamped));
    }

    private void sendTyped(HoloObject source, int action, Consumer<FriendlyByteBuf> content)
    {
        if (source == null) return;
        sendAction(source, buf -> {
            buf.writeByte(action);
            if (content != null) content.accept(buf);
        });
    }

    // ---------------------------------------------------------------- synchronisation

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeByte(mode.ordinal());
        buf.writeVarInt(system);
        buf.writeVarInt(selectedSystem);
        buf.writeVarInt(selectedBody);
        buf.writeVarInt(centerBody);
        buf.writeDouble(systemHalf);
        buf.writeDouble(frameX);
        buf.writeDouble(frameY);
        buf.writeDouble(frameZ);
        buf.writeDouble(frameHalf);
        buf.writeVarInt(origin);
        buf.writeBoolean(environment);
        if (!environment) return;
        buf.writeInt(copiedRevision);
        buf.writeVarInt(currentSystemId);
        SystemHeader.writeList(buf, headers);
        buf.writeBoolean(shown != null);
        if (shown != null) shown.write(buf);
    }

    /** Lecture stricte (en-tetes et systeme : leve sur une valeur invalide) ; valeurs de vue invalides = defaut. */
    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        int ordinal = buf.readByte();
        Mode newMode = ordinal >= 0 && ordinal < Mode.values().length ? Mode.values()[ordinal] : Mode.GALAXY;
        int newSystem = Math.max(0, buf.readVarInt());
        int newSelected = Math.max(0, buf.readVarInt());
        int newBody = Math.max(0, buf.readVarInt());
        int newCenter = Math.max(0, buf.readVarInt());
        double newHalf = buf.readDouble();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        double half = buf.readDouble();
        int newOrigin = Math.max(0, buf.readVarInt());
        boolean newEnvironment = buf.readBoolean();
        int revision = 0;
        int current = 0;
        List<SystemHeader> newHeaders = List.of();
        SpaceSystem newShown = null;
        if (newEnvironment) {
            revision = buf.readInt();
            current = buf.readVarInt();
            newHeaders = SystemHeader.readList(buf);
            if (buf.readBoolean()) newShown = SpaceSystem.read(buf);
        }
        mode = newShown != null ? newMode : Mode.GALAXY;
        system = newSystem;
        selectedSystem = newSelected;
        selectedBody = newBody;
        centerBody = newCenter;
        systemHalf = newHalf > 0.0 ? SpaceSystemScale.clampViewHalf(newHalf) : 0.0;
        if (validFrame(x, y, z, half)) {
            frameX = x;
            frameY = y;
            frameZ = z;
            frameHalf = half;
        } else {
            frameX = frameY = frameZ = 0.0;
            frameHalf = 0.0;
        }
        origin = newOrigin;
        environment = newEnvironment;
        copiedRevision = revision;
        currentSystemId = current;
        headers = newHeaders;
        shown = newShown;
    }

    /** Client : fenetre mise a jour depuis l'etat recu. */
    @Override
    public void onSync() { refresh(); }

    private void refresh()
    {
        if (window != null) window.showState();
    }

    // ---------------------------------------------------------------- lecture (renderer, fenetre, Configurator T42)

    /** Cube (deux cotes). */
    public SpaceMapCube cube() { return cube; }

    /** Fenetre de navigation (deux cotes). */
    public SpaceMapWindow window() { return window; }

    public Mode mode() { return mode; }

    @Override
    public boolean systemView() { return mode == Mode.SYSTEM && shown != null; }

    @Override
    public boolean environmentPresent() { return environment; }

    /** Revision de la galaxie de l'Environment deja copiee. */
    public int copiedRevision() { return copiedRevision; }

    @Override
    public int currentSystemId() { return currentSystemId; }

    @Override
    public List<SystemHeader> headers() { return headers; }

    /** Id de l'origine des coordonnees choisie, {@code 0} = defaut ; voir {@link #originHeader()}. */
    public int origin() { return origin; }

    @Override
    public SystemHeader originHeader() { return SpaceCoordinates.origin(headers, origin); }

    @Override
    public double frameX() { return frameX; }

    @Override
    public double frameY() { return frameY; }

    @Override
    public double frameZ() { return frameZ; }

    @Override
    public double frameHalf() { return frameHalf; }

    @Override
    public int selectedSystem() { return selectedSystem; }

    /** Id du systeme affiche (vue systeme) ou deplie (arborescence), {@code 0} = aucun. */
    public int displayedSystem() { return system; }

    /**
     * Systeme affiche ou deplie (copie complete), {@code null} si aucun ; meme en vue galactique (arborescence). Voir
     * {@link #shownSystem()} pour la vue systeme seulement.
     */
    public SpaceSystem displayedSystemData() { return shown; }

    @Override
    public SpaceSystem shownSystem() { return systemView() ? shown : null; }

    @Override
    public int selectedBody() { return selectedBody; }

    /** Id du corps au centre de la vue systeme, {@code 0} = origine du systeme. */
    public int centerBody() { return centerBody; }

    /** En-tete du systeme selectionne, {@code null} si aucun ou absent de la copie. Sans allocation. */
    public SystemHeader selectedHeader() { return header(selectedSystem); }

    /** En-tete du systeme {@code id} dans la copie, {@code null} si absent. Sans allocation. */
    public SystemHeader header(int id)
    {
        if (id == 0) return null;
        for (int i = 0; i < headers.size(); i++) {
            SystemHeader header = headers.get(i);
            if (header.id() == id) return header;
        }
        return null;
    }

    /** Corps selectionne du systeme affiche (copie), {@code null} si aucun. Sans allocation. */
    public SpaceBody selectedBodyData()
    {
        return shown == null || selectedBody == 0 ? null : shown.body(selectedBody);
    }

    @Override
    public int centerIndex()
    {
        if (shown != indexedSystem || centerBody != indexedBody) {
            indexedSystem = shown;
            indexedBody = centerBody;
            centerIndex = shown == null || centerBody == 0 ? -1 : shown.indexOf(centerBody);
        }
        return centerIndex;
    }

    /** Demi-etendue de la vue systeme, UA (systeme entier tant qu'elle n'est pas posee ; {@code 1} sans systeme). */
    @Override
    public double viewHalf()
    {
        if (systemHalf > 0.0) return systemHalf;
        return shown != null ? SpaceSystemScale.halfExtent(shown) : 1.0;
    }

    /**
     * Temps de la vue systeme, en jours : temps vivant de l'Environment (client : temps d'affichage du miroir a
     * {@code partialTick} ; serveur : {@code days()} de la derniere copie) ; {@code 0} sans Environment.
     */
    public double viewDays(float partialTick)
    {
        if (host() != null && host().clientSide()) {
            SpaceEnvironmentModule env = SpaceEnvironmentModule.client();
            return env != null ? env.displayDays(partialTick) : 0.0;
        }
        return lastEnvironment != null ? lastEnvironment.days() : 0.0;
    }

    @Override
    public double displayDays(float partialTick) { return viewDays(partialTick); }

    /**
     * Repere du cube -> repere du marker (blocs) : {@code P = t + R . (sigma (.) q)}, {@code q} en unites du cube
     * ({@code docs/space/04-mathematiques.md} par. 7). Sans allocation (instance de travail du module : fil du cote).
     */
    public Vector3f cubeToMarker(double qx, double qy, double qz, Vector3f dest)
    {
        HoloTransform t = cube.transform();
        dest.set((float) qx * t.scaleX(), (float) qy * t.scaleY(), (float) qz * t.scaleZ());
        HoloEditMath.rotation(rotation, t).transform(dest);
        return dest.add(t.x(), t.y(), t.z());
    }

    /** Repere du marker (blocs) -> repere du cube : {@code q = (R^-1 . (P - t)) / sigma}. Sans allocation. */
    public Vector3d markerToCube(float px, float py, float pz, Vector3f scratch, Vector3d dest)
    {
        HoloTransform t = cube.transform();
        scratch.set(px - t.x(), py - t.y(), pz - t.z());
        HoloEditMath.rotation(rotation, t).conjugate().transform(scratch);
        return dest.set(scratch.x / Math.max(1e-6f, t.scaleX()), scratch.y / Math.max(1e-6f, t.scaleY()),
                scratch.z / Math.max(1e-6f, t.scaleZ()));
    }

    /** Position d'un systeme sur la carte galactique, en unites du cube : {@code (g - c) / H}. Sans allocation. */
    public Vector3d galaxyPoint(SystemHeader header, Vector3d dest)
    {
        double half = frameHalf > 0.0 ? frameHalf : MIN_REFRAME_HALF;
        double h = SpaceMapCube.HALF_SIZE;
        return dest.set((header.x() - frameX) / half * h, (header.y() - frameY) / half * h,
                (header.z() - frameZ) / half * h);
    }

    /**
     * Positions des corps du systeme affiche dans la vue systeme courante (unites du cube) au temps {@code days} ;
     * {@code dest[i]} pour {@code shownSystem().bodies().get(i)}. Faux sans vue systeme. Sans allocation.
     *
     * @param dest au moins {@link SpaceLimits#MAX_BODIES} vecteurs ({@link SpaceOrbits#newPositionArray()})
     */
    public boolean bodyPoints(double days, Vector3d[] dest)
    {
        SpaceSystem view = shownSystem();
        if (view == null) return false;
        SpaceSystemScale.positions(view, days, viewHalf(), centerIndex(), dest);
        return true;
    }
}
