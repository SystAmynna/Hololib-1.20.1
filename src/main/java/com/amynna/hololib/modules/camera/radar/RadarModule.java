package com.amynna.hololib.modules.camera.radar;

import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.player.PlayerHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.modules.HoloLibModules;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.camera.config.Configurable;
import com.amynna.hololib.modules.camera.quickaction.QuickAction;
import com.amynna.hololib.modules.camera.quickaction.QuickActionProvider;
import com.amynna.hololib.modules.camera.targeting.TargetingThroughWalls;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Objects;

/**
 * Module {@code hololib:radar} (memoire de joueur) : radar a onde du joueur local. Activer lance une onde spherique
 * depuis la position du joueur (point fige) ; les entites qu'elle atteint sont reperees jusqu'a Desactiver (un second
 * Activer relance l'onde et vide la liste). Onde et reperages sont purement clients, vus du seul proprietaire
 * ({@code client.modules.camera.radar.RadarClient}, dessin {@code client.render.modules.camera.radar}).
 * <p>
 * Ce module porte les <b>reglages</b> (sauvegardes, synchronises, valides par le serveur) :
 * <ul>
 *     <li>{@link #thickness()} : epaisseur de mur traversable, 0 a {@value #MAX_THICKNESS} blocs ou
 *     {@link #INFINITE_THICKNESS} (aucun test) ;</li>
 *     <li>{@link #radius()} : rayon de detection, {@value #MIN_RADIUS} a {@value #MAX_RADIUS} blocs par pas de
 *     {@value #RADIUS_STEP} ;</li>
 *     <li>{@link #players()} / {@link #entities()} : joueurs, autres entites vivantes (hors porte-armures).</li>
 * </ul>
 * Declenchement : fenetre {@link RadarWindow} (camera normale, {@code PUBLIC}) ; si la memoire contient le module
 * {@code hololib:quick_action}, la fenetre est retiree ({@link #quickActionPresent()}, calcule par le serveur) et
 * remplacee par les actions {@code activate} / {@code deactivate} de la roue ({@link QuickActionProvider}). Reglages :
 * fenetre de Config {@link RadarConfigWindow} ({@link Configurable}) ; sans module Config, les valeurs par defaut
 * s'appliquent. Ciblage ({@link TargetingThroughWalls}) : les entites reperees sont visables a travers les murs.
 * <p>
 * Le client passe par un {@link Backend} installe au setup client ; il n'est appele que par le client du proprietaire
 * ({@code host().canAct()}). Actions reseau (client vers serveur) : {@code SETTING} = 3 octets (type, indice, valeur).
 * Etat synchronise : 4 octets. Code commun : aucune classe client.
 */
public final class RadarModule extends HoloModule implements Configurable, QuickActionProvider, TargetingThroughWalls
{
    /** Epaisseur de mur traversable maximale (blocs) ; au-dela, {@link #INFINITE_THICKNESS}. */
    public static final int MAX_THICKNESS = 16;
    /** Valeur « infinie » de l'epaisseur : aucun test de mur. */
    public static final int INFINITE_THICKNESS = MAX_THICKNESS + 1;
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 128;
    public static final int RADIUS_STEP = 8;
    public static final int DEFAULT_RADIUS = 48;

    // Indices des reglages (actions SETTING).
    static final int THICKNESS = 0;
    static final int RADIUS = 1;
    static final int PLAYERS = 2;
    static final int ENTITIES = 3;

    private static final byte ACTION_SETTING = 0;

    private static final Component NAME = Component.literal("Radar");

    /** Pont vers le client, installe au setup client ; {@code null} sur un serveur dedie. */
    private static Backend backend;

    // Etat (deux cotes).
    private int thickness = INFINITE_THICKNESS;
    private int radius = DEFAULT_RADIUS;
    private boolean players = true;
    private boolean entities = true;
    /** Serveur -> clients : la memoire contient Quick Action (non sauvegarde). */
    private boolean quickActionPresent;
    /** Vrai une fois la fenetre orientee vers le regard du joueur (sauvegarde). */
    private boolean oriented;

    // Objets (deux cotes).
    private RadarWindow window;
    private RadarConfigWindow config;
    private List<QuickAction> actions = List.of();

    // ---------------------------------------------------------------- pont client

    /** Pont vers le client (etat du radar local), installe au setup client. */
    public interface Backend
    {
        /** Lance (ou relance) le radar du joueur local avec les reglages de {@code module}. */
        void activate(RadarModule module);

        /** Arrete le radar du joueur local et oublie les reperages. */
        void deactivate(RadarModule module);

        /** Texte d'etat de la fenetre ; meme instance tant que l'etat affiche ne change pas. */
        String status();

        /** Entites reperees par le radar local (vue en lecture). */
        List<? extends Entity> detected();
    }

    /** Branche le client (setup client). */
    public static void install(Backend backend) { RadarModule.backend = Objects.requireNonNull(backend); }

    // ---------------------------------------------------------------- reglages (lus par le client)

    /** Epaisseur de mur traversable en blocs (0 a 16), ou {@link #INFINITE_THICKNESS}. */
    public int thickness() { return thickness; }

    /** Vrai : aucun test d'epaisseur de mur. */
    public boolean infiniteThickness() { return thickness >= INFINITE_THICKNESS; }

    /** Rayon de detection, en blocs. */
    public int radius() { return radius; }

    /** Detecte les autres joueurs. */
    public boolean players() { return players; }

    /** Detecte les autres entites vivantes (hors joueurs et porte-armures). */
    public boolean entities() { return entities; }

    /** Vrai si la memoire du proprietaire contient Quick Action : la fenetre est alors retiree. */
    public boolean quickActionPresent() { return quickActionPresent; }

    // ---------------------------------------------------------------- cycle de vie

    @Override
    public void onLoad()
    {
        window = addObject(new RadarWindow("window", this));
        config = addObject(new RadarConfigWindow("config", NAME, this));
        actions = List.of(new RadarAction(true), new RadarAction(false));
        config.refresh();
    }

    @Override
    public void onUnload()
    {
        // Module retire ou memoire changee : le radar local s'arrete (RadarClient le verifie aussi chaque tick).
        if (host().canAct() && backend != null) backend.deactivate(this);
    }

    @Override
    public void onSync() { config.refresh(); }

    @Override
    public void clientTick()
    {
        if (host().canAct() && backend != null) window.setStatus(backend.status());
    }

    @Override
    public Component configName() { return NAME; }

    @Override
    public ConfigWindow configWindow() { return config; }

    @Override
    public List<QuickAction> quickActions() { return actions; }

    @Override
    public List<? extends Entity> throughWallTargets()
    {
        return host().canAct() && backend != null ? backend.detected() : List.of();
    }

    // ---------------------------------------------------------------- client (proprietaire)

    /** Cote client : bouton ou action Activer. */
    void requestActivate()
    {
        if (host().canAct() && backend != null) backend.activate(this);
    }

    /** Cote client : bouton ou action Desactiver. */
    void requestDeactivate()
    {
        if (host().canAct() && backend != null) backend.deactivate(this);
    }

    /** Cote client : reglage demande par la fenetre de Config ; applique aussitot, puis envoye. */
    void requestSetting(int index, int value)
    {
        if (!apply(index, value)) return;
        config.refresh();
        sendAction(buf -> buf.writeByte(ACTION_SETTING).writeByte(index).writeByte(value));
    }

    /** Applique un reglage borne ; faux si l'indice est inconnu ou la valeur inchangee. */
    private boolean apply(int index, int value)
    {
        switch (index) {
            case THICKNESS -> {
                int v = Mth.clamp(value, 0, INFINITE_THICKNESS);
                if (v == thickness) return false;
                thickness = v;
            }
            case RADIUS -> {
                int v = clampRadius(value);
                if (v == radius) return false;
                radius = v;
            }
            case PLAYERS -> {
                if (players == (value != 0)) return false;
                players = value != 0;
            }
            case ENTITIES -> {
                if (entities == (value != 0)) return false;
                entities = value != 0;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static int clampRadius(int value)
    {
        int stepped = Math.round((float) value / RADIUS_STEP) * RADIUS_STEP;
        return Mth.clamp(stepped, MIN_RADIUS, MAX_RADIUS);
    }

    // ---------------------------------------------------------------- serveur

    @Override
    public void serverTick(ServerLevel level)
    {
        PlayerHoloContainer container = HoloContainers.player(owner());
        boolean present = container != null && container.contains(HoloLibModules.QUICK_ACTION.getId());
        if (present != quickActionPresent) {
            quickActionPresent = present;
            markDirty();
        }
        if (oriented) return;
        Player player = level.getPlayerByUUID(owner());
        if (player == null) return;
        window.transform().setTranslation(player.getYRot(), 0f, RadarWindow.DISTANCE);
        oriented = true;
        markDirty();
    }

    @Override
    public void handleAction(ServerPlayer player, FriendlyByteBuf buf)
    {
        // Le framework n'accepte une action sans source que du proprietaire : verification redondante, peu couteuse.
        if (!player.getUUID().equals(owner())) return;
        if (buf.readByte() != ACTION_SETTING) return;
        int index = buf.readByte();
        int value = buf.readUnsignedByte();
        apply(index, value);
        // Toujours renvoye : corrige aussi une prediction bornee ou refusee.
        markDirty();
    }

    @Override
    public void save(CompoundTag tag)
    {
        tag.putInt("thickness", thickness);
        tag.putInt("radius", radius);
        tag.putBoolean("players", players);
        tag.putBoolean("entities", entities);
        tag.putBoolean("oriented", oriented);
    }

    @Override
    public void load(CompoundTag tag)
    {
        thickness = tag.contains("thickness", Tag.TAG_INT)
                ? Mth.clamp(tag.getInt("thickness"), 0, INFINITE_THICKNESS) : INFINITE_THICKNESS;
        radius = tag.contains("radius", Tag.TAG_INT) ? clampRadius(tag.getInt("radius")) : DEFAULT_RADIUS;
        players = !tag.contains("players", Tag.TAG_BYTE) || tag.getBoolean("players");
        entities = !tag.contains("entities", Tag.TAG_BYTE) || tag.getBoolean("entities");
        oriented = tag.getBoolean("oriented");
    }

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeByte(thickness);
        buf.writeByte(radius);
        buf.writeByte((players ? 1 : 0) | (entities ? 2 : 0) | (quickActionPresent ? 4 : 0));
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        thickness = Mth.clamp(buf.readUnsignedByte(), 0, INFINITE_THICKNESS);
        radius = clampRadius(buf.readUnsignedByte());
        int flags = buf.readByte();
        players = (flags & 1) != 0;
        entities = (flags & 2) != 0;
        quickActionPresent = (flags & 4) != 0;
    }

    // ---------------------------------------------------------------- Quick Action

    /**
     * Actions {@code activate} (« Radar : Activer », ondes cyan) et {@code deactivate} (« Radar : Desactiver », ondes
     * grises barrees de rouge).
     */
    private final class RadarAction implements QuickAction
    {
        private static final Component NAME_ON = Component.literal("Radar : Activer");
        private static final Component NAME_OFF = Component.literal("Radar : Desactiver");
        private static final int SEGMENTS = 20;

        private final boolean activate;

        RadarAction(boolean activate) { this.activate = activate; }

        @Override
        public String id() { return activate ? "activate" : "deactivate"; }

        @Override
        public Component name() { return activate ? NAME_ON : NAME_OFF; }

        @Override
        public void drawIcon(HoloCanvas canvas, float size)
        {
            RadarIcons.waves(canvas, size, activate ? 0xFF40C0FF : 0xFF708090, SEGMENTS);
            if (!activate) {
                canvas.beginLayer();
                canvas.line(0.15f * size, 0.85f * size, 0.85f * size, 0.15f * size, Math.max(1.5f, 0.1f * size),
                        0xFFFF5050);
                canvas.endLayer();
            }
        }

        @Override
        public void run()
        {
            if (activate) requestActivate();
            else requestDeactivate();
        }
    }
}
