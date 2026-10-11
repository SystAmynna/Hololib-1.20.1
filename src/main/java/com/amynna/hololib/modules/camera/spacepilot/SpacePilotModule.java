package com.amynna.hololib.modules.camera.spacepilot;

import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.player.PlayerHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.camera.config.Configurable;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.SpaceFormat;
import com.amynna.hololib.modules.dimension.space.math.SpaceCruise;
import com.amynna.hololib.modules.dimension.space.math.SpaceJump;
import com.amynna.hololib.modules.dimension.space.math.SpaceMotion;
import com.amynna.hololib.modules.dimension.space.math.SpaceMotionSettings;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.math.SpacePose;
import com.amynna.hololib.modules.dimension.space.math.SpaceUnits;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceGalaxy.BackgroundStar;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.monde.spacecontroller.SpacePilotPad;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3d;

import java.util.Objects;

/**
 * Module {@code hololib:space_pilot} (memoire de joueur) : aides au <b>pilotage</b> dans une dimension munie d'un
 * Space Environment, pour le joueur local seulement. Reduit en T33 (D25, C75) : la connaissance du ciel (anneaux,
 * orbites, etoiles exterieures, nom au regard) et la Quick Action sont passees a Space Knowledge
 * ({@code hololib:space_knowledge}). Il garde :
 * <ul>
 *     <li>le <b>joystick</b> (T19, D11 : sans ce module, le Space Controller refuse le pilotage,
 *     {@link #of(ServerPlayer)}) : deux objets {@code Local} {@code PRIVATE}, {@link SpaceJoystickForward} (cercle
 *     avant = zone morte, place par {@link #clientTick} sur l'axe avant de l'Environment) et {@link SpaceJoystickAim}
 *     (cercle de visee au centre de l'ecran), dessines seulement pendant que le joueur local pilote
 *     ({@link #joystickShown()}) ;</li>
 *     <li>le <b>saut</b> (T22) : cible, roue de charge et aide a la visee, dessinees pour le pilote local par
 *     {@code SpaceJumpRenderer} (client) ;</li>
 *     <li>le <b>lien vers le referentiel</b> et l'anneau du referentiel ({@link #refLink()}), dessines pour le pilote
 *     local par {@code SpaceReferenceLinkRenderer} (client), <b>seulement pendant le pilotage</b> (C75) ;</li>
 *     <li>le <b>HUD de navigation</b> (T20) : objet {@code Hud} {@code PRIVATE} {@link SpacePilotHud} (cle
 *     {@code hud}), deplacable par l'Own HUD Editor ; {@link #clientTick} lit le miroir de l'Environment : ligne de
 *     vitesse chaque tick (T34), referentiel et distance au plus 4 fois par seconde ({@value #HUD_REFRESH_TICKS}
 *     ticks) ; affiche si {@link #hudShown()}, c'est-a-dire <b>seulement pendant le pilotage</b> (T33). Pendant le
 *     voyage d'un saut (T22), la ligne de distance montre la cible et le temps restant ;</li>
 *     <li>la <b>vitesse de croisiere</b> (T34, D25, C76 ; reglage {@link #cruise()}) : chaque tick client, cible =
 *     corps du systeme courant le plus proche du centre de l'ecran (regard de la camera, lu par le {@link Backend},
 *     hysteresis : {@link SpaceCruise#pickTarget}) et vitesse cible {@code v*} ({@link SpaceCruise#targetSpeed} :
 *     distance a sa surface, rayon, {@code V_max} ; consigne sure au frein normal avec un temps de reaction humain)
 *     ; affichees sur la ligne de vitesse et la ligne « Cible » du HUD. Aucune cible pendant un saut.</li>
 * </ul>
 * Etat sauvegarde et synchronise au proprietaire seulement : {@link #sharedWithObservers()} faux et objets
 * {@code PRIVATE} (C45), un joueur qui inspecte ne voit ni la fenetre de Config ni les rendus. Les widgets appliquent
 * le reglage localement (prediction) puis l'envoient ({@code sendAction}) ; le serveur le valide (proprietaire,
 * bornes) et renvoie l'etat. Les cles d'etat du lot 2 qui ne servent plus ({@code enabled}, {@code orbits},
 * {@code bodies}, {@code stars}, {@code names}, {@code nameAngle}) sont ignorees au chargement, sans erreur (L22) ;
 * {@code hud} et {@code refLink} gardent leur valeur.
 * <p>
 * Compatibilite (utilisation de l'API, sans la modifier) : {@link Configurable} (fenetre
 * {@link SpacePilotConfigWindow}). Plus de Quick Action (C75). Le client passe par un {@link Backend} (regard de la
 * camera) installe au setup client ({@code client.modules.camera.spacepilot.SpacePilotBackend}).
 * <p>
 * Sauvegarde : les 3 reglages et la version de mise en page du HUD ({@code hudLayout}, T34 : un HUD sauve avant est
 * recale une fois par {@link #serverTick}).
 * <p>
 * Action reseau (client vers serveur) : {@code FLAG} = 3 octets (type, indice du reglage, valeur). Etat synchronise :
 * 1 octet (3 reglages en bits).
 */
public final class SpacePilotModule extends HoloModule implements Configurable
{
    // Indices des reglages booleens (bits de la synchro, actions FLAG, cles NBT).
    static final int HUD = 0;
    static final int REF_LINK = 1;
    static final int CRUISE = 2;
    private static final int FLAG_COUNT = 3;
    private static final int ALL_FLAGS = (1 << FLAG_COUNT) - 1;
    private static final String[] FLAG_KEYS = {"hud", "refLink", "cruise"};

    private static final byte ACTION_FLAG = 0;

    private static final Component NAME = Component.literal("Space Pilot");

    /**
     * Intervalle de rafraichissement des lignes de referentiel et de distance du HUD, en ticks (4 fois par seconde au
     * plus) ; la ligne de vitesse (et la vitesse de croisiere) est rafraichie chaque tick.
     */
    static final int HUD_REFRESH_TICKS = 5;

    /**
     * Fraction de tick de la pose et des positions lues par la vitesse de croisiere : fin de l'intervalle affiche, au
     * tick de {@link SpaceEnvironmentModule#speed()} (dernier tick simule).
     */
    private static final float CRUISE_PARTIAL_TICK = 1f;
    /**
     * Cosinus de l'ecart maximal entre le regard et le point d'interception pour garder la cible courante (T39, C118) :
     * {@code TARGET_ANGLE + TARGET_HYSTERESIS}, comme l'hysteresis de {@link SpaceCruise#pickTarget}.
     */
    private static final double KEEP_INTERCEPT_COS =
            Math.cos(Math.toRadians(SpaceCruise.TARGET_ANGLE + SpaceCruise.TARGET_HYSTERESIS));

    /** Pont vers le client (regard de la camera), installe au setup client ; {@code null} sur un serveur dedie. */
    private static Backend backend;

    /** Cle NBT de la version de mise en page du HUD (absente avant T34 : placement a recaler, {@link #serverTick}). */
    private static final String HUD_LAYOUT_KEY = "hudLayout";
    /** Version courante de la mise en page du HUD (T34 : fenetre de 240 x 50). */
    private static final int HUD_LAYOUT = 2;

    // Etat (deux cotes) : bits des reglages (tous vrais par defaut).
    private int flags = ALL_FLAGS;
    /** Serveur : placement du HUD sauve avant T34, a recaler au premier tick ({@link #serverTick}). */
    private boolean hudLayoutPending;

    // Objets (deux cotes).
    private SpacePilotConfigWindow config;
    private SpaceJoystickForward forward;
    private SpaceJoystickAim aim;
    private SpacePilotHud navigation;

    // Client : HUD de navigation (espace de travail cree au premier rafraichissement, jamais cote serveur).
    /** Space Environment present dans la dimension du joueur local (relu a chaque tick client). */
    private boolean environmentPresent;
    private int hudCountdown;
    private SpacePose hudPose;
    private Vector3d[] hudPositions;
    // Client : vitesse de croisiere (T34), espace de travail cree au premier calcul.
    /** Id de la cible courante ({@code 0} sans cible : reglage coupe, saut, rien pres du centre) : hysteresis. */
    private int cruiseTargetId;
    /** Vitesse cible du dernier calcul, km/tick (valable si {@link #cruiseTargetId} n'est pas nul). */
    private double cruiseSpeed;
    private Vector3d cruiseLook;
    /** Positions des corps un tick apres {@link #hudPositions} (eloignement de la cible, T39), creees au besoin. */
    private Vector3d[] cruiseNext;
    private Vector3d cruiseLookSystem;
    /** Direction du point d'interception (axes du systeme, unitaire), T39, C118. */
    private Vector3d cruiseAim;
    /** Vrai si le point d'interception du dernier calcul s'ecarte visiblement de la cible. */
    private boolean cruiseInterceptShown;

    // ---------------------------------------------------------------- pont client

    /** Pont vers le client, installe au setup client. */
    public interface Backend
    {
        /**
         * Regard de la camera du joueur local (vecteur unitaire, axes du monde Minecraft, derniere frame dessinee) dans
         * {@code dest} ; faux si la camera n'est pas prete (dest inchange).
         */
        boolean cameraLook(Vector3d dest);
    }

    /** Branche le client (setup client). */
    public static void install(Backend backend) { SpacePilotModule.backend = Objects.requireNonNull(backend); }

    // ---------------------------------------------------------------- reglages (lus par les renderers)

    /** HUD de navigation (vitesse, referentiel, distance). */
    public boolean hud() { return flag(HUD); }

    /**
     * Lien pointille entre le cercle avant et le corps de reference, et anneau du referentiel : dessines seulement
     * pendant le pilotage (C75).
     */
    public boolean refLink() { return flag(REF_LINK); }

    /** Calculateur de vitesse de croisiere du HUD (T34) : partie droite de la ligne de vitesse et ligne « Cible ». */
    public boolean cruise() { return flag(CRUISE); }

    /**
     * Client : vrai si le HUD de navigation ({@link SpacePilotHud}) doit etre affiche : miroir du joueur local
     * ({@code ownerView}), Space Environment dans sa dimension (relu a chaque tick client, pas par frame), reglage
     * {@link #hud()} actif et joueur local aux commandes ({@link SpacePilotPad#localPilot()}, comme le joystick : T33,
     * C75).
     */
    public boolean hudShown()
    {
        return host().clientSide() && host().ownerView() && environmentPresent && hud()
                && SpacePilotPad.localPilot() != null;
    }

    /** HUD de navigation (deux cotes). */
    public SpacePilotHud navigationHud() { return navigation; }

    /**
     * Client : vrai si ce module est le miroir du joueur local ({@code ownerView}) et que ce joueur pilote : capture
     * du pad en cours et etat recu qui le designe comme pilote de ce poste ({@link SpacePilotPad#localPilot()}, meme
     * regle que C29). Le cercle de visee et le cercle avant ne sont dessines que dans ce cas.
     */
    public boolean joystickShown()
    {
        return host().clientSide() && host().ownerView() && SpacePilotPad.localPilot() != null;
    }

    /** Cercle avant du joystick (deux cotes). */
    public SpaceJoystickForward joystickForward() { return forward; }

    /** Cercle de visee du joystick (deux cotes). */
    public SpaceJoystickAim joystickAim() { return aim; }

    /**
     * Serveur : module Space Pilot de la memoire active de {@code player}, ou {@code null} (absent, memoire pas encore
     * chargee). Le Space Controller refuse le pilotage sans lui (D11).
     */
    public static SpacePilotModule of(ServerPlayer player)
    {
        PlayerHoloContainer container = HoloContainers.player(player);
        if (container == null) return null;
        for (HoloModule module : container.modules()) {
            if (module instanceof SpacePilotModule pilot) return pilot;
        }
        return null;
    }

    boolean flag(int index) { return (flags & (1 << index)) != 0; }

    private void setFlag(int index, boolean on)
    {
        if (on) flags |= 1 << index;
        else flags &= ~(1 << index);
    }

    // ---------------------------------------------------------------- cycle de vie

    /** Pas d'envoi aux observateurs : tout ce que dessine Space Pilot est pour le joueur local seulement (C45). */
    @Override
    public boolean sharedWithObservers() { return false; }

    @Override
    public void onLoad()
    {
        config = addObject(new SpacePilotConfigWindow("config", NAME, this));
        forward = addObject(new SpaceJoystickForward("joystick_forward"));
        aim = addObject(new SpaceJoystickAim("joystick_aim"));
        navigation = addObject(new SpacePilotHud("hud"));
        config.refresh();
    }

    /**
     * Client : place le cercle avant sur l'axe avant du monde (reglage de l'Environment de la dimension du joueur
     * local ; meme calcul que le Space Controller du lot 1) et rafraichit le HUD de navigation. Le serveur ne deplace
     * ni l'un ni l'autre : rien n'est envoye.
     */
    @Override
    public void clientTick()
    {
        if (forward == null) return;
        SpaceEnvironmentModule env = SpaceEnvironmentModule.client();
        environmentPresent = env != null;
        if (env == null || !env.synced()) return;
        SpaceMotionSettings settings = env.settings();
        forward.place((float) settings.forwardYaw(), (float) settings.forwardPitch());
        refreshHud(env);
    }

    /**
     * Client : relit le miroir de l'Environment et met le HUD a jour (qui ne reconstruit que les lignes changees).
     * Valeurs arrondies a trois chiffres significatifs, comme la fenetre du Space Controller. Rafraichi meme masque :
     * une session d'edition le revele, et le calcul est leger (orbites du systeme courant).
     * <ul>
     *     <li>chaque tick : ligne de vitesse et, avec la vitesse de croisiere ({@link #cruise()}), cible et vitesse
     *     cible ({@link #updateCruise}) : en freinage rapide la vitesse change de 22 % par tick, une consigne vieille
     *     de 5 ticks ne servirait a rien ;</li>
     *     <li>au plus une fois tous les {@value #HUD_REFRESH_TICKS} ticks : corps de reference et altitude au-dessus
     *     de lui. L'altitude n'est pas synchronisee (calculee par le serveur seulement) : elle est recalculee ici au
     *     temps d'affichage du ciel ({@code pose(0)} et {@code bodyPositions(0)}, meme repere et meme instant), donc
     *     coherente avec ce que voit le joueur.</li>
     * </ul>
     * Pendant un saut ({@code jumpAt(0)} en phase {@code CHARGE} ou {@code TRAVEL}, T22, C63) : nom de l'etoile cible
     * (etoile de fond, {@code "?"} si inconnue), puis en voyage le temps restant en secondes entieres, a la place de
     * la distance ; l'altitude n'est pas recalculee ; pas de cible de croisiere.
     */
    private void refreshHud(SpaceEnvironmentModule env)
    {
        if (!host().ownerView()) return;
        boolean cruise = cruise();
        navigation.setCruise(cruise);
        SpaceJump jump = env.jumpAt(0f);
        boolean jumping = jump.phase() == SpaceJump.Phase.TRAVEL || jump.phase() == SpaceJump.Phase.CHARGE;
        double speed = SpaceFormat.round3(env.speed());
        SpaceBody target = cruise && !jumping ? updateCruise(env) : null;
        if (target == null) {
            cruiseTargetId = 0;
            cruiseInterceptShown = false;
        }
        navigation.showSpeed(speed, target != null ? SpaceFormat.round3(cruiseSpeed) : Double.NaN,
                target != null ? target.name() : null);

        if (--hudCountdown > 0) return;
        hudCountdown = HUD_REFRESH_TICKS;
        SpaceBody body = env.referenceBody();
        String reference = body != null ? body.name() : null;
        if (jumping) {
            BackgroundStar star = env.backgroundStar(jump.target());
            int seconds = jump.phase() == SpaceJump.Phase.CHARGE ? SpacePilotHud.CHARGING
                    : SpaceFormat.wholeSeconds(env.jumpRemainingSeconds(0f));
            navigation.show(reference, Double.POSITIVE_INFINITY, star != null ? star.name() : "?", seconds);
            return;
        }
        double distance = body != null ? SpaceFormat.round3(referenceAltitude(env, body)) : Double.POSITIVE_INFINITY;
        navigation.show(reference, distance, null, 0);
    }

    /**
     * Client : cible et vitesse cible de la vitesse de croisiere (T34, {@code docs/space/04-mathematiques.md} par. 11),
     * au temps de la vitesse lue ({@code pose(1)}, {@code bodyPositions(1)} : {@code speed()} est celle du dernier tick
     * simule, environ un tick apres {@code pose(0)} ; une distance plus vieille d'un tick surestimerait {@code v*}).
     * Regard de la camera ramene dans les axes du systeme ({@code Q} de la pose). Cible : la cible courante est gardee
     * tant que le regard reste a moins de {@code TARGET_ANGLE + TARGET_HYSTERESIS} de son point d'interception affiche
     * (le pilote vise ce point, parfois loin de la cible) ; sinon {@link SpaceCruise#pickTarget} (hysteresis sur
     * {@link #cruiseTargetId}). Puis (T39, C118) {@code v*} et point d'interception par
     * {@link SpaceCruise#interceptSpeed} : mouvement de la cible relatif au mouvement d'entrainement du monde sur un
     * tick (positions un tick plus tard a l'echelle de temps courante ; referentiel, ou melange des deux pendant le
     * fondu d'une bascule ; nul si la cible est le referentiel hors fondu). Sans allocation apres le premier appel.
     *
     * @return la cible (et {@link #cruiseTargetId}, {@link #cruiseSpeed}, {@link #cruiseAim} a jour), ou {@code null}
     */
    private SpaceBody updateCruise(SpaceEnvironmentModule env)
    {
        SpaceSystem system = env.currentSystem();
        if (system == null || backend == null) return null;
        if (cruiseLook == null) {
            cruiseLook = new Vector3d();
            cruiseLookSystem = new Vector3d();
            cruiseAim = new Vector3d();
        }
        if (!backend.cameraLook(cruiseLook)) return null;
        ensureHudWork();
        int count = env.bodyPositions(CRUISE_PARTIAL_TICK, hudPositions);
        env.pose(CRUISE_PARTIAL_TICK, hudPose);
        hudPose.orientation().transform(cruiseLook, cruiseLookSystem);
        int index = -1;
        if (cruiseTargetId != 0 && cruiseInterceptShown && cruiseLookSystem.dot(cruiseAim) >= KEEP_INTERCEPT_COS) {
            index = system.indexOf(cruiseTargetId);
            if (index >= count) index = -1;
        }
        if (index < 0) {
            index = SpaceCruise.pickTarget(system, hudPositions, count, hudPose.position(), cruiseLookSystem,
                    cruiseTargetId);
        }
        cruiseInterceptShown = false;
        if (index < 0) return null;
        SpaceBody body = system.bodies().get(index);
        cruiseTargetId = body.id();
        // Mouvement de la cible relatif au mouvement d'entrainement du monde (referentiel, fondu compris : juste apres
        // la bascule sur la cible, le monde suit encore l'ancien referentiel et la consigne ne saute pas).
        if (cruiseNext == null) cruiseNext = SpaceOrbits.newPositionArray();
        double next = env.displayDays(CRUISE_PARTIAL_TICK) + SpaceUnits.elapsedDays(1.0, env.settings().timeScale());
        SpaceOrbits.positions(system, next, cruiseNext);
        double mx = cruiseNext[index].x - hudPositions[index].x;
        double my = cruiseNext[index].y - hudPositions[index].y;
        double mz = cruiseNext[index].z - hudPositions[index].z;
        int reference = system.indexOf(env.referenceBodyId());
        int from = system.indexOf(env.referenceBlendFromId());
        double sigma = from >= 0 && from < count ? env.referenceBlend() : 1.0;
        if (reference >= 0 && reference < count) {
            mx -= sigma * (cruiseNext[reference].x - hudPositions[reference].x);
            my -= sigma * (cruiseNext[reference].y - hudPositions[reference].y);
            mz -= sigma * (cruiseNext[reference].z - hudPositions[reference].z);
        }
        if (sigma < 1.0) {
            mx -= (1.0 - sigma) * (cruiseNext[from].x - hudPositions[from].x);
            my -= (1.0 - sigma) * (cruiseNext[from].y - hudPositions[from].y);
            mz -= (1.0 - sigma) * (cruiseNext[from].z - hudPositions[from].z);
        }
        cruiseSpeed = SpaceCruise.interceptSpeed(hudPose.position(), hudPositions[index], mx, my, mz, body.radius(),
                env.settings().maxSpeed(), cruiseAim);
        cruiseInterceptShown = SpaceCruise.interceptShown(hudPose.position(), hudPositions[index], body.radius(),
                cruiseAim);
        return body;
    }

    /**
     * Client : point d'interception de la vitesse de croisiere (T39, C118), direction unitaire <b>dans les axes du
     * systeme</b> (a tourner par la pose du ciel), copiee dans {@code dest} ; faux sans cible, croisiere coupee,
     * pendant un saut, ou si le point est sur le disque de la cible ({@link SpaceCruise#interceptShown}). Mis a jour
     * chaque tick client du HUD ; lecture sans allocation (renderer du pilote local).
     */
    public boolean cruiseIntercept(Vector3d dest)
    {
        if (!cruiseInterceptShown || cruiseTargetId == 0 || !cruise() || cruiseAim == null) return false;
        dest.set(cruiseAim);
        return true;
    }

    /** Client : pose et positions de travail du HUD, creees au premier usage (jamais cote serveur). */
    private void ensureHudWork()
    {
        if (hudPose == null) {
            hudPose = new SpacePose();
            hudPositions = SpaceOrbits.newPositionArray();
        }
    }

    /** Client : altitude au-dessus de {@code body} (corps du systeme courant) au temps d'affichage, km. */
    private double referenceAltitude(SpaceEnvironmentModule env, SpaceBody body)
    {
        SpaceSystem system = env.currentSystem();
        if (system == null) return Double.POSITIVE_INFINITY;
        int index = system.indexOf(body.id());
        if (index < 0) return Double.POSITIVE_INFINITY;
        ensureHudWork();
        if (env.bodyPositions(0f, hudPositions) <= index) return Double.POSITIVE_INFINITY;
        env.pose(0f, hudPose);
        return SpaceMotion.altitude(hudPose.position(), body, hudPositions[index]);
    }

    @Override
    public void onSync() { config.refresh(); }

    @Override
    public Component configName() { return NAME; }

    @Override
    public ConfigWindow configWindow() { return config; }

    // ---------------------------------------------------------------- client (proprietaire)

    /** Cote client : reglage booleen demande par la fenetre ; applique aussitot, puis envoye. */
    void requestFlag(int index, boolean on)
    {
        if (index < 0 || index >= FLAG_COUNT) return;
        setFlag(index, on);
        config.refresh();
        sendAction(buf -> buf.writeByte(ACTION_FLAG).writeByte(index).writeBoolean(on));
    }

    // ---------------------------------------------------------------- serveur

    @Override
    public void handleAction(ServerPlayer player, FriendlyByteBuf buf)
    {
        // Le framework n'accepte une action sans source que du proprietaire : verification redondante, peu couteuse.
        if (!player.getUUID().equals(owner())) return;
        switch (buf.readByte()) {
            case ACTION_FLAG -> {
                int index = buf.readByte();
                boolean on = buf.readBoolean();
                if (index < 0 || index >= FLAG_COUNT) return;
                setFlag(index, on);
            }
            default -> {
                return;
            }
        }
        // Toujours renvoye : corrige aussi une prediction bornee ou refusee.
        markDirty();
    }

    /**
     * Serveur : migration du placement du HUD sauvegarde avant T34 ({@link #HUD_LAYOUT_KEY} absente) : le transform
     * d'un {@code Hud} place le <b>centre</b> de la fenetre, agrandie en T34 (150 x 39 -> 240 x 50) ; il est decale
     * d'une demi-difference de taille pour garder le coin haut gauche ou le joueur l'avait mis. Une seule fois, puis
     * sauvegarde ({@code markDirty}) ; le transform modifie est synchronise par le framework.
     */
    @Override
    public void serverTick(ServerLevel level)
    {
        if (!hudLayoutPending || navigation == null) return;
        hudLayoutPending = false;
        HoloTransform transform = navigation.transform();
        transform.setTranslation(transform.x() + 0.5f * (SpacePilotHud.WIDTH - SpacePilotHud.COMPACT_WIDTH),
                transform.y() + 0.5f * (SpacePilotHud.HEIGHT - SpacePilotHud.COMPACT_HEIGHT), transform.z());
        markDirty();
    }

    @Override
    public void save(CompoundTag tag)
    {
        for (int i = 0; i < FLAG_COUNT; i++) tag.putBoolean(FLAG_KEYS[i], flag(i));
        tag.putInt(HUD_LAYOUT_KEY, HUD_LAYOUT);
    }

    @Override
    public void load(CompoundTag tag)
    {
        flags = ALL_FLAGS;
        for (int i = 0; i < FLAG_COUNT; i++) {
            if (tag.contains(FLAG_KEYS[i], Tag.TAG_BYTE)) setFlag(i, tag.getBoolean(FLAG_KEYS[i]));
        }
        // Cles du lot 2 (enabled, orbits, bodies, stars, names, nameAngle) : ignorees (L22).
        // Etat sauve avant T34 (cle hud presente, pas de version de mise en page) : HUD a recaler (serverTick).
        hudLayoutPending = tag.contains(FLAG_KEYS[HUD], Tag.TAG_BYTE) && tag.getInt(HUD_LAYOUT_KEY) < HUD_LAYOUT;
    }

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeByte(flags);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        flags = buf.readByte() & ALL_FLAGS;
    }
}
