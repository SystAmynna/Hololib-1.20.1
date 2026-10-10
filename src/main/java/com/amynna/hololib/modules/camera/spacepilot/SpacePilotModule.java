package com.amynna.hololib.modules.camera.spacepilot;

import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.player.PlayerHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.camera.config.Configurable;
import com.amynna.hololib.modules.camera.quickaction.QuickAction;
import com.amynna.hololib.modules.camera.quickaction.QuickActionProvider;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.SpaceFormat;
import com.amynna.hololib.modules.dimension.space.math.SpaceJump;
import com.amynna.hololib.modules.dimension.space.math.SpaceMotion;
import com.amynna.hololib.modules.dimension.space.math.SpaceMotionSettings;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.math.SpacePose;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceGalaxy.BackgroundStar;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.monde.spacecontroller.SpacePilotPad;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.joml.Vector3d;

import java.util.List;

/**
 * Module {@code hololib:space_pilot} (memoire de joueur) : affichage personnel du pilote dans une dimension munie d'un
 * Space Environment. Il porte les <b>reglages d'affichage</b>, lus par les renderers du joueur local (surcouche du
 * ciel, lien vers le referentiel, HUD de navigation : T18 a T20), et le <b>joystick</b> de pilotage (T19) :
 * <ul>
 *     <li>{@link #enabled()} : interrupteur general, bascule par l'action de la roue Quick Action ; faux, il coupe la
 *     surcouche, le HUD et le lien, mais pas le joystick ni la roue de charge, necessaires au pilotage (C44) ;</li>
 *     <li>{@link #orbits()}, {@link #bodies()} (reperage des corps : anneau + nom), {@link #stars()} (etoiles
 *     exterieures), {@link #names()} (nom au regard, corps et etoiles), {@link #nameAngle()} (angle regard / objet
 *     sous lequel le nom s'affiche, {@value #MIN_NAME_ANGLE} a {@value #MAX_NAME_ANGLE} degres), {@link #hud()}
 *     (donnees de navigation), {@link #refLink()} (lien vers le referentiel).</li>
 * </ul>
 * <b>Joystick</b> (T19, D11 : sans ce module, le Space Controller refuse le pilotage, {@link #of(ServerPlayer)}) : deux
 * objets {@code Local} {@code PRIVATE}, {@link SpaceJoystickForward} (cercle avant = zone morte, place par
 * {@link #clientTick} sur l'axe avant de l'Environment) et {@link SpaceJoystickAim} (cercle de visee au centre de
 * l'ecran), dessines seulement pendant que le joueur local pilote ({@link #joystickShown()}) ; pas coupes par
 * {@link #enabled()} (C44).
 * <p>
 * <b>HUD de navigation</b> (T20) : objet {@code Hud} {@code PRIVATE} {@link SpacePilotHud} (cle {@code hud}),
 * deplacable par l'Own HUD Editor ; {@link #clientTick} lit le miroir de l'Environment et le rafraichit au plus
 * 4 fois par seconde ({@value #HUD_REFRESH_TICKS} ticks) ; affiche si {@link #hudShown()}. Pendant le voyage d'un
 * saut (T22), la ligne de distance montre la cible et le temps restant.
 * <p>
 * <b>Saut</b> (T22) : la roue de charge et le reperage de la cible sont dessines pour le pilote local par
 * {@code SpaceJumpRenderer} (client), qui lit le pad du Space Controller et le miroir de l'Environment ; pas coupes
 * par {@link #enabled()} (C44), sauf l'aide a la visee (etoile qui serait choisie, sans la touche du saut).
 * <p>
 * Etat sauvegarde et synchronise au proprietaire seulement : {@link #sharedWithObservers()} faux et objets
 * {@code PRIVATE} (C45), un joueur qui inspecte ne voit ni la fenetre de Config ni l'action de la roue. Les widgets
 * appliquent le reglage localement (prediction) puis l'envoient ({@code sendAction}) ; le serveur le valide
 * (proprietaire, bornes) et renvoie l'etat.
 * <p>
 * Compatibilites (utilisation de l'API, sans la modifier) : {@link Configurable} (fenetre
 * {@link SpacePilotConfigWindow}) et {@link QuickActionProvider} (action {@code toggle}, dont le nom et l'icone
 * suivent {@link #enabled()} : la roue les relit a chaque frame).
 * <p>
 * Actions reseau (client vers serveur) : {@code FLAG} = 3 octets (type, indice du reglage, valeur) ; {@code ANGLE} = 2
 * octets (type, angle en degres). Etat synchronise : 2 octets (7 reglages en bits, angle).
 */
public final class SpacePilotModule extends HoloModule implements Configurable, QuickActionProvider
{
    /** Bornes et defaut de l'angle du nom au regard, en degres (C46). */
    public static final int MIN_NAME_ANGLE = 1;
    public static final int MAX_NAME_ANGLE = 30;
    public static final int DEFAULT_NAME_ANGLE = 5;

    // Indices des reglages booleens (bits de la synchro, actions FLAG, cles NBT).
    static final int ENABLED = 0;
    static final int ORBITS = 1;
    static final int BODIES = 2;
    static final int STARS = 3;
    static final int NAMES = 4;
    static final int HUD = 5;
    static final int REF_LINK = 6;
    private static final int FLAG_COUNT = 7;
    private static final int ALL_FLAGS = (1 << FLAG_COUNT) - 1;
    private static final String[] FLAG_KEYS = {"enabled", "orbits", "bodies", "stars", "names", "hud", "refLink"};

    private static final byte ACTION_FLAG = 0;
    private static final byte ACTION_ANGLE = 1;

    private static final Component NAME = Component.literal("Space Pilot");

    /** Intervalle de rafraichissement du HUD de navigation, en ticks (4 fois par seconde au plus). */
    static final int HUD_REFRESH_TICKS = 5;

    // Etat (deux cotes) : bits des reglages (tous vrais par defaut) et angle du nom.
    private int flags = ALL_FLAGS;
    private int nameAngle = DEFAULT_NAME_ANGLE;

    // Objets (deux cotes).
    private SpacePilotConfigWindow config;
    private List<QuickAction> actions = List.of();
    private SpaceJoystickForward forward;
    private SpaceJoystickAim aim;
    private SpacePilotHud navigation;

    // Client : HUD de navigation (espace de travail cree au premier rafraichissement, jamais cote serveur).
    /** Space Environment present dans la dimension du joueur local (relu a chaque tick client). */
    private boolean environmentPresent;
    private int hudCountdown;
    private SpacePose hudPose;
    private Vector3d[] hudPositions;

    // ---------------------------------------------------------------- reglages (lus par les renderers)

    /** Interrupteur general (Quick Action) : faux, surcouche, HUD et lien sont coupes (pas le joystick, C44). */
    public boolean enabled() { return flag(ENABLED); }

    /** Orbites des corps du systeme courant. */
    public boolean orbits() { return flag(ORBITS); }

    /** Reperage des corps : anneau autour de chaque corps visible. */
    public boolean bodies() { return flag(BODIES); }

    /** Etoiles exterieures (autres systemes) : anneau double. */
    public boolean stars() { return flag(STARS); }

    /** Nom de l'objet (corps ou etoile) le plus proche du regard, sous {@link #nameAngle()}. */
    public boolean names() { return flag(NAMES); }

    /** Angle maximal regard / objet pour afficher son nom, en degres (1 a 30, defaut 5). */
    public int nameAngle() { return nameAngle; }

    /** HUD de navigation (vitesse, referentiel, distance). */
    public boolean hud() { return flag(HUD); }

    /** Lien pointille entre l'axe avant et le corps de reference. */
    public boolean refLink() { return flag(REF_LINK); }

    /**
     * Client : vrai si le HUD de navigation ({@link SpacePilotHud}) doit etre affiche : miroir du joueur local
     * ({@code ownerView}), Space Environment dans sa dimension (relu a chaque tick client, pas par frame), interrupteur
     * general et reglage {@link #hud()} actifs.
     */
    public boolean hudShown()
    {
        return host().clientSide() && host().ownerView() && environmentPresent && enabled() && hud();
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
        actions = List.of(new ToggleAction());
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
     * Client : relit vitesse, corps de reference et altitude au-dessus de lui dans le miroir de l'Environment, au plus
     * une fois tous les {@value #HUD_REFRESH_TICKS} ticks, et les passe au HUD (qui ne reconstruit que les lignes
     * changees). Valeurs arrondies a trois chiffres significatifs, comme la fenetre du Space Controller. Rafraichi meme
     * masque : une session d'edition le revele, et le calcul est leger (orbites du systeme courant, 4 fois par
     * seconde).
     * <p>
     * L'altitude n'est pas synchronisee (calculee par le serveur seulement) : elle est recalculee ici au temps
     * d'affichage du ciel ({@code pose(0)} et {@code bodyPositions(0)}, meme repere et meme instant), donc coherente
     * avec ce que voit le joueur.
     * <p>
     * Pendant un saut ({@code jumpAt(0)} en phase {@code CHARGE} ou {@code TRAVEL}, T22, C63) : nom de l'etoile cible
     * (etoile de fond, {@code "?"} si inconnue), puis en voyage le temps restant en secondes entieres, a la place de
     * la distance ; l'altitude n'est pas recalculee.
     */
    private void refreshHud(SpaceEnvironmentModule env)
    {
        if (--hudCountdown > 0) return;
        hudCountdown = HUD_REFRESH_TICKS;
        if (!host().ownerView()) return;
        double speed = SpaceFormat.round3(env.speed());
        SpaceBody body = env.referenceBody();
        String reference = body != null ? body.name() : null;
        SpaceJump jump = env.jumpAt(0f);
        if (jump.phase() == SpaceJump.Phase.TRAVEL || jump.phase() == SpaceJump.Phase.CHARGE) {
            BackgroundStar star = env.backgroundStar(jump.target());
            int seconds = jump.phase() == SpaceJump.Phase.CHARGE ? SpacePilotHud.CHARGING
                    : SpaceFormat.wholeSeconds(env.jumpRemainingSeconds(0f));
            navigation.show(speed, reference, Double.POSITIVE_INFINITY, star != null ? star.name() : "?", seconds);
            return;
        }
        double distance = body != null ? SpaceFormat.round3(referenceAltitude(env, body)) : Double.POSITIVE_INFINITY;
        navigation.show(speed, reference, distance, null, 0);
    }

    /** Client : altitude au-dessus de {@code body} (corps du systeme courant) au temps d'affichage, km. */
    private double referenceAltitude(SpaceEnvironmentModule env, SpaceBody body)
    {
        SpaceSystem system = env.currentSystem();
        if (system == null) return Double.POSITIVE_INFINITY;
        int index = system.indexOf(body.id());
        if (index < 0) return Double.POSITIVE_INFINITY;
        if (hudPose == null) {
            hudPose = new SpacePose();
            hudPositions = SpaceOrbits.newPositionArray();
        }
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

    @Override
    public List<QuickAction> quickActions() { return actions; }

    // ---------------------------------------------------------------- client (proprietaire)

    /** Cote client : reglage booleen demande par la fenetre ou la roue ; applique aussitot, puis envoye. */
    void requestFlag(int index, boolean on)
    {
        if (index < 0 || index >= FLAG_COUNT) return;
        setFlag(index, on);
        config.refresh();
        sendAction(buf -> buf.writeByte(ACTION_FLAG).writeByte(index).writeBoolean(on));
    }

    /** Cote client : angle du nom demande par la fenetre, en degres ; applique aussitot, puis envoye. */
    void requestNameAngle(int degrees)
    {
        int angle = Mth.clamp(degrees, MIN_NAME_ANGLE, MAX_NAME_ANGLE);
        if (angle == nameAngle) return;
        nameAngle = angle;
        config.refresh();
        sendAction(buf -> buf.writeByte(ACTION_ANGLE).writeByte(angle));
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
            case ACTION_ANGLE -> nameAngle = Mth.clamp(buf.readByte(), MIN_NAME_ANGLE, MAX_NAME_ANGLE);
            default -> {
                return;
            }
        }
        // Toujours renvoye : corrige aussi une prediction bornee ou refusee.
        markDirty();
    }

    @Override
    public void save(CompoundTag tag)
    {
        for (int i = 0; i < FLAG_COUNT; i++) tag.putBoolean(FLAG_KEYS[i], flag(i));
        tag.putInt("nameAngle", nameAngle);
    }

    @Override
    public void load(CompoundTag tag)
    {
        flags = ALL_FLAGS;
        for (int i = 0; i < FLAG_COUNT; i++) {
            if (tag.contains(FLAG_KEYS[i], Tag.TAG_BYTE)) setFlag(i, tag.getBoolean(FLAG_KEYS[i]));
        }
        nameAngle = tag.contains("nameAngle", Tag.TAG_INT)
                ? Mth.clamp(tag.getInt("nameAngle"), MIN_NAME_ANGLE, MAX_NAME_ANGLE) : DEFAULT_NAME_ANGLE;
    }

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeByte(flags);
        buf.writeByte(nameAngle);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        flags = buf.readByte() & ALL_FLAGS;
        nameAngle = Mth.clamp(buf.readByte(), MIN_NAME_ANGLE, MAX_NAME_ANGLE);
    }

    // ---------------------------------------------------------------- Quick Action

    /**
     * Action {@code toggle} : bascule {@link #enabled()}. Nom et icone decrivent l'effet du clic et suivent l'etat
     * (relus par la roue a chaque frame) : « Space Pilot : OFF » et planete barree quand l'affichage est actif,
     * « Space Pilot : ON » et planete allumee sinon.
     */
    private final class ToggleAction implements QuickAction
    {
        private static final Component NAME_OFF = Component.literal("Space Pilot : OFF");
        private static final Component NAME_ON = Component.literal("Space Pilot : ON");
        private static final int SEGMENTS = 16;

        @Override
        public String id() { return "toggle"; }

        @Override
        public Component name() { return enabled() ? NAME_OFF : NAME_ON; }

        @Override
        public void drawIcon(HoloCanvas canvas, float size)
        {
            boolean on = enabled();
            float c = size / 2f;
            // Planete, puis son anneau incline, puis (actif) la barre rouge : une couche par forme.
            canvas.beginLayer();
            disk(canvas, c, c, 0.27f * size, on ? 0xFF607080 : 0xFF40C0FF);
            canvas.endLayer();
            canvas.beginLayer();
            canvas.ellipse(c, c, 0.47f * size, 0.17f * size, Math.max(1f, 0.06f * size), SEGMENTS,
                    on ? 0xFF8090A0 : 0xFFA0E0FF);
            canvas.endLayer();
            if (on) {
                canvas.beginLayer();
                canvas.line(0.15f * size, 0.85f * size, 0.85f * size, 0.15f * size, Math.max(1.5f, 0.1f * size),
                        0xFFFF5050);
                canvas.endLayer();
            }
        }

        @Override
        public void run() { requestFlag(ENABLED, !enabled()); }

        /** Disque plein en eventail. */
        private static void disk(HoloCanvas canvas, float cx, float cy, float r, int color)
        {
            for (int i = 0; i < SEGMENTS; i++) {
                float a0 = i * Mth.TWO_PI / SEGMENTS;
                float a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
                canvas.quad(cx, cy, cx + r * Mth.cos(a0), cy + r * Mth.sin(a0), cx + r * Mth.cos(a1),
                        cy + r * Mth.sin(a1), cx + r * Mth.cos(a1), cy + r * Mth.sin(a1), color);
            }
        }
    }
}
