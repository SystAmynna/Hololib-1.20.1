package com.amynna.hololib.modules.camera.spaceknowledge;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.camera.config.Configurable;
import com.amynna.hololib.modules.camera.quickaction.QuickAction;
import com.amynna.hololib.modules.camera.quickaction.QuickActionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Module {@code hololib:space_knowledge} (memoire de joueur, T33, D25) : <b>connaissance du ciel</b> dans une dimension
 * munie d'un Space Environment, pour le joueur local seulement. Il porte les reglages lus par la surcouche du ciel
 * ({@code SpaceKnowledgeSkyRenderer}, client) :
 * <ul>
 *     <li>{@link #enabled()} : interrupteur general, bascule par l'action {@code toggle} de la roue Quick Action ;
 *     faux, toute la surcouche est coupee ;</li>
 *     <li>{@link #orbits()}, {@link #bodies()} (reperage des corps : anneau), {@link #stars()} (etoiles exterieures),
 *     {@link #names()} (nom au regard, corps et etoiles), {@link #nameAngle()} (angle regard / objet sous lequel le nom
 *     s'affiche, {@value #MIN_NAME_ANGLE} a {@value #MAX_NAME_ANGLE} degres), {@link #distance()} (distance sous le
 *     nom : a la surface d'un corps, en annees-lumiere pour une etoile exterieure).</li>
 * </ul>
 * Repris de Space Pilot (C75) : la surcouche n'est plus dessinee par lui. Ce module n'est <b>pas</b> requis pour
 * piloter (D11 ne concerne que Space Pilot) et ne dessine ni le joystick, ni le lien vers le referentiel, ni le saut.
 * <p>
 * Etat sauvegarde et synchronise au proprietaire seulement : {@link #sharedWithObservers()} faux et objets
 * {@code PRIVATE} (C45), un joueur qui inspecte ne voit ni la fenetre de Config ni l'action de la roue. Les widgets
 * appliquent le reglage localement (prediction) puis l'envoient ({@code sendAction}) ; le serveur le valide
 * (proprietaire, bornes) et renvoie l'etat.
 * <p>
 * Compatibilites (utilisation de l'API, sans la modifier) : {@link Configurable} (fenetre
 * {@link SpaceKnowledgeConfigWindow}, « Space Knowledge ») et {@link QuickActionProvider} (action {@code toggle},
 * « Space Knowledge : OFF / ON », nom et icone relus par la roue a chaque frame).
 * <p>
 * Actions reseau (client vers serveur) : {@code FLAG} = 3 octets (type, indice du reglage, valeur) ; {@code ANGLE} = 2
 * octets (type, angle en degres). Etat synchronise : 2 octets (6 reglages en bits, angle). Code commun : aucune classe
 * client.
 */
public final class SpaceKnowledgeModule extends HoloModule implements Configurable, QuickActionProvider
{
    private static final HoloColor ICON_PLANET = HoloColor.primary(0xFF);
    private static final HoloColor ICON_RING = HoloColor.primary(0xFF).lighter(0.51f);
    private static final HoloColor ICON_OFF = HoloColor.special(0xFF).darker(0.56f);
    private static final HoloColor ICON_RING_OFF = HoloColor.special(0xFF).darker(0.43f);
    private static final HoloColor ICON_STRIKE = HoloColor.critical(0xFF);

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
    static final int DISTANCE = 5;
    private static final int FLAG_COUNT = 6;
    private static final int ALL_FLAGS = (1 << FLAG_COUNT) - 1;
    private static final String[] FLAG_KEYS = {"enabled", "orbits", "bodies", "stars", "names", "distance"};

    private static final byte ACTION_FLAG = 0;
    private static final byte ACTION_ANGLE = 1;

    private static final Component NAME = Component.literal("Space Knowledge");

    // Etat (deux cotes) : bits des reglages (tous vrais par defaut) et angle du nom.
    private int flags = ALL_FLAGS;
    private int nameAngle = DEFAULT_NAME_ANGLE;

    // Objets (deux cotes).
    private SpaceKnowledgeConfigWindow config;
    private List<QuickAction> actions = List.of();

    // ---------------------------------------------------------------- reglages (lus par le renderer)

    /** Interrupteur general (Quick Action) : faux, toute la surcouche est coupee. */
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

    /** Distance sous le nom au regard : a la surface d'un corps, en annees-lumiere pour une etoile exterieure. */
    public boolean distance() { return flag(DISTANCE); }

    boolean flag(int index) { return (flags & (1 << index)) != 0; }

    private void setFlag(int index, boolean on)
    {
        if (on) flags |= 1 << index;
        else flags &= ~(1 << index);
    }

    // ---------------------------------------------------------------- cycle de vie

    /** Pas d'envoi aux observateurs : tout ce que dessine Space Knowledge est pour le joueur local seulement (C45). */
    @Override
    public boolean sharedWithObservers() { return false; }

    @Override
    public void onLoad()
    {
        config = addObject(new SpaceKnowledgeConfigWindow("config", NAME, this));
        actions = List.of(new ToggleAction());
        config.refresh();
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
     * (relus par la roue a chaque frame) : « Space Knowledge : OFF » et planete barree quand la surcouche est active,
     * « Space Knowledge : ON » et planete allumee sinon.
     */
    private final class ToggleAction implements QuickAction
    {
        private static final Component NAME_OFF = Component.literal("Space Knowledge : OFF");
        private static final Component NAME_ON = Component.literal("Space Knowledge : ON");
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
            disk(canvas, c, c, 0.27f * size, canvas.color(on ? ICON_OFF : ICON_PLANET));
            canvas.endLayer();
            canvas.beginLayer();
            canvas.ellipse(c, c, 0.47f * size, 0.17f * size, Math.max(1f, 0.06f * size), SEGMENTS,
                    canvas.color(on ? ICON_RING_OFF : ICON_RING));
            canvas.endLayer();
            if (on) {
                canvas.beginLayer();
                canvas.line(0.15f * size, 0.85f * size, 0.85f * size, 0.15f * size, Math.max(1.5f, 0.1f * size),
                        canvas.color(ICON_STRIKE));
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
