package com.amynna.hololib.demo;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAccess;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.control.HoloTextBuffer;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.holo.widget.control.HoloWidgetTextBox;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Demonstration de la capture du clavier (memoire de joueur), construite avec les widgets generiques de
 * {@code holo.widget.control} : deux fenetres en camera normale ({@code Local} relatif), publiques, cote a cote,
 * orientees vers le regard au premier tick.
 * <ul>
 *     <li>{@code pad} : un clic capture les controles de deplacement (avancer, reculer, gauche, droite), qui pilotent
 *     le point du joueur au lieu du joueur ({@link DemoPad}, une {@code HoloWidgetKeyBox} multijoueur) ; Echap ou
 *     Entree rend la main. Un point par joueur : un autre joueur qui a acces a la fenetre et la clique fait apparaitre
 *     le sien, vu de tous.</li>
 *     <li>{@code editor} : un clic capture tout le clavier ; zone de texte multiligne ({@link HoloWidgetTextBox}).
 *     Echap, ou un regard qui s'eloigne de la zone, valide : le texte est envoye au serveur, sauvegarde et
 *     synchronise.</li>
 * </ul>
 * Chaque fenetre a un interrupteur ({@link HoloWidgetSwitch}) qui l'ouvre a tous (acces {@code EVERYONE}) ou la
 * reserve au proprietaire ; seul le proprietaire peut le basculer. Pour voir les fenetres, un autre joueur doit
 * inspecter le proprietaire ({@code /hololib_inspect}). Le fond des fenetres intercepte les clics : les viser ne casse
 * jamais le bloc derriere.
 */
public final class DemoKeyboardModule extends HoloModule
{
    private static final float DISTANCE = 1.6f;
    /** Ecart angulaire des fenetres de part et d'autre du regard, en degres. */
    private static final float PAD_YAW = -24f;
    private static final float EDITOR_YAW = 22f;
    private static final int BACKGROUND = 0x8010304A;
    private static final int PAD_ACCENT = 0xFF80FF80;
    private static final int EDITOR_ACCENT = 0xFFFFE060;

    /** Bornes de l'editeur (reappliquees cote serveur). */
    private static final int TEXT_LENGTH = 512;
    private static final int TEXT_LINES = 32;

    private static final int ACTION_MOVE = 0;
    private static final int ACTION_ACCESS = 1;
    private static final int ACTION_TEXT = 2;
    /** Points synchronises au plus. */
    private static final int MAX_POINTS = 32;

    private String text = "";
    private boolean padShared;
    private boolean editorShared;
    /** Point de chaque joueur dans le pave (non sauvegarde). */
    private final Map<UUID, float[]> points = new LinkedHashMap<>();
    /** Vrai une fois les fenetres orientees vers le regard du joueur (sauvegarde). */
    private boolean oriented;
    private int age;

    private HoloWindow padWindow;
    private HoloWindow editorWindow;
    private DemoPad pad;
    private HoloWidgetTextBox editor;
    private HoloWidgetSwitch padAccess;
    private HoloWidgetSwitch editorAccess;

    @Override
    public void onLoad()
    {
        padWindow = addObject(new HoloWindow("pad", new HoloAnchor.Local(false),
                HoloTransform.translation(PAD_YAW, 0f, DISTANCE), 160, 146, 1f / 160f));
        setUp(padWindow, "Pave", PAD_ACCENT);
        pad = padWindow.root().add(new DemoPad(6, 20, 148, 100, this::move));
        padAccess = padWindow.root().add(accessSwitch(padWindow, 126, 148));

        editorWindow = addObject(new HoloWindow("editor", new HoloAnchor.Local(false),
                HoloTransform.translation(EDITOR_YAW, 0f, DISTANCE), 240, 128, 1f / 200f));
        setUp(editorWindow, "Editeur", EDITOR_ACCENT);
        editor = editorWindow.root().add(new HoloWidgetTextBox(6, 20, 228, 86, TEXT_LENGTH, true));
        editor.setOnSubmit((box, value) -> commit(value));
        editorAccess = editorWindow.root().add(accessSwitch(editorWindow, 110, 228));

        padWindow.setAccess(padShared ? HoloAccess.EVERYONE : HoloAccess.OWNER);
        editorWindow.setAccess(editorShared ? HoloAccess.EVERYONE : HoloAccess.OWNER);
        refresh();
    }

    private static void setUp(HoloWindow window, String title, int accent)
    {
        window.setVisibility(HoloVisibility.PUBLIC);
        window.setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK, HoloControl.MIDDLE_CLICK);
        window.root().setBackground(BACKGROUND).setBorder(accent, 1f);
        window.root().add(new HoloLabel(4, 4, window.width() - 8, 14, Component.literal(title))
                .setAlign(HoloLabel.Align.CENTER).setColor(accent));
    }

    /** Interrupteur d'acces : n'affiche que l'etat confirme par le serveur (seul le proprietaire peut le changer). */
    private HoloWidgetSwitch accessSwitch(HoloWindow window, float y, float width)
    {
        HoloWidgetSwitch toggle = new HoloWidgetSwitch(6, y, width, 14, Component.literal("Ouvert a tous"),
                (widget, requested) -> sendAction(window, buf -> {
                    buf.writeByte(ACTION_ACCESS);
                    buf.writeBoolean(requested);
                }));
        toggle.setPredictive(false);
        return toggle;
    }

    private void refresh()
    {
        padAccess.setOn(padShared);
        editorAccess.setOn(editorShared);
        editor.setText(text);
        pad.syncPoints(points);
    }

    // ---------------------------------------------------------------- actions client

    /** Position du point du joueur local (pave). */
    private void move(float x, float y)
    {
        sendAction(padWindow, buf -> {
            buf.writeByte(ACTION_MOVE);
            buf.writeFloat(x);
            buf.writeFloat(y);
        });
    }

    /** Fin d'une edition : texte de l'editeur. */
    private void commit(String value)
    {
        sendAction(editorWindow, buf -> {
            buf.writeByte(ACTION_TEXT);
            buf.writeUtf(value, TEXT_LENGTH);
        });
    }

    // ---------------------------------------------------------------- serveur

    @Override
    public void serverTick(ServerLevel level)
    {
        if (!oriented) {
            Player player = level.getPlayerByUUID(owner());
            if (player != null) {
                padWindow.transform().setTranslation(player.getYRot() + PAD_YAW, 0f, DISTANCE);
                editorWindow.transform().setTranslation(player.getYRot() + EDITOR_YAW, 0f, DISTANCE);
                oriented = true;
            }
        }
        // Points des joueurs deconnectes, une fois par seconde.
        if (++age % 20 == 0
                && points.keySet().removeIf(id -> level.getServer().getPlayerList().getPlayer(id) == null)) {
            markDirty();
        }
    }

    /** Source deja verifiee par le framework (acces de la fenetre, visibilite, inspection). */
    @Override
    public void handleAction(ServerPlayer player, HoloObject source, FriendlyByteBuf buf)
    {
        int action = buf.readByte();
        if (action == ACTION_ACCESS) {
            if (!player.getUUID().equals(owner())) return;
            boolean requested = buf.readBoolean();
            if (source == padWindow) {
                padShared = requested;
                padWindow.setAccess(padShared ? HoloAccess.EVERYONE : HoloAccess.OWNER);
            } else if (source == editorWindow) {
                editorShared = requested;
                editorWindow.setAccess(editorShared ? HoloAccess.EVERYONE : HoloAccess.OWNER);
            } else {
                return;
            }
        } else if (action == ACTION_MOVE && source == padWindow) {
            float x = buf.readFloat();
            float y = buf.readFloat();
            if (!Float.isFinite(x) || !Float.isFinite(y)) return;
            if (!points.containsKey(player.getUUID()) && points.size() >= MAX_POINTS) return;
            points.put(player.getUUID(), new float[] {pad.clampX(x), pad.clampY(y)});
        } else if (action == ACTION_TEXT && source == editorWindow) {
            String value = HoloTextBuffer.sanitize(buf.readUtf(TEXT_LENGTH), TEXT_LENGTH, TEXT_LINES);
            if (value.equals(text)) return;
            text = value;
        } else {
            return;
        }
        markDirty();
    }

    // ---------------------------------------------------------------- synchronisation et sauvegarde

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeUtf(text, TEXT_LENGTH);
        buf.writeBoolean(padShared);
        buf.writeBoolean(editorShared);
        buf.writeVarInt(points.size());
        for (Map.Entry<UUID, float[]> entry : points.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeFloat(entry.getValue()[0]);
            buf.writeFloat(entry.getValue()[1]);
        }
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        text = buf.readUtf(TEXT_LENGTH);
        padShared = buf.readBoolean();
        editorShared = buf.readBoolean();
        int count = Math.min(buf.readVarInt(), MAX_POINTS);
        points.clear();
        for (int i = 0; i < count; i++) {
            points.put(buf.readUUID(), new float[] {buf.readFloat(), buf.readFloat()});
        }
    }

    @Override
    public void onSync() { refresh(); }

    @Override
    public void save(CompoundTag tag)
    {
        tag.putString("text", text);
        tag.putBoolean("pad_shared", padShared);
        tag.putBoolean("editor_shared", editorShared);
        tag.putBoolean("oriented", oriented);
    }

    @Override
    public void load(CompoundTag tag)
    {
        text = HoloTextBuffer.sanitize(tag.getString("text"), TEXT_LENGTH, TEXT_LINES);
        padShared = tag.getBoolean("pad_shared");
        editorShared = tag.getBoolean("editor_shared");
        oriented = tag.getBoolean("oriented");
    }
}
