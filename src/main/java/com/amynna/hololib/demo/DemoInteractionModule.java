package com.amynna.hololib.demo;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAccess;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetDraggable;
import com.amynna.hololib.holo.widget.control.HoloWidgetHoverZone;
import com.amynna.hololib.holo.widget.control.HoloWidgetMouseBox;
import com.amynna.hololib.holo.widget.control.HoloWidgetMultiButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetScrollZone;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.holo.widget.control.HoloWidgetValueBar;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Demonstration de l'interaction au regard (memoire de joueur), construite avec les widgets generiques de
 * {@code holo.widget.control} : une fenetre en camera normale ({@code Local} relatif), orientee vers le regard au
 * premier tick, publique.
 * <ul>
 *     <li>{@link HoloWidgetButton} (clic gauche) et {@link HoloWidgetMultiButton} (clic droit et molette, une action
 *     chacun) ;</li>
 *     <li>{@link HoloWidgetScrollZone} qui compte les crans de molette, {@link HoloWidgetHoverZone} qui reagit au
 *     regard ;</li>
 *     <li>une petite animation ({@link DemoAnimation}) et une {@link HoloWidgetMouseBox} qui capture la souris des que
 *     le regard y entre (Saut recentre, Echap / Entree rend la souris) ;</li>
 *     <li>une {@link HoloWidgetValueBar} (molette ou clic) et une {@link HoloWidgetDraggable} a glisser au regard,
 *     toutes deux synchronisees ;</li>
 *     <li>un {@link HoloWidgetSwitch} "ouvert a tous" (acces de la fenetre), que seul le proprietaire peut
 *     basculer.</li>
 * </ul>
 * L'etat (compteurs, valeur, position de la pastille, acces) vit cote serveur : chaque widget envoie une action au nom
 * de la fenetre ({@code sendAction(window, ...)}), que le serveur verifie puis renvoie a tous. Le fond de la fenetre
 * intercepte les clics : viser la fenetre ne casse jamais le bloc derriere.
 */
public final class DemoInteractionModule extends HoloModule
{
    private static final int WIDTH = 240;
    private static final int HEIGHT = 208;
    private static final float DISTANCE = 0.8f;
    private static final HoloColor ACCENT = HoloColor.primary(0xFF);

    private static final int ACTION_LEFT = 0;
    private static final int ACTION_RIGHT = 1;
    private static final int ACTION_MIDDLE = 2;
    private static final int ACTION_SCROLL = 3;
    private static final int ACTION_ACCESS = 4;
    private static final int ACTION_VALUE = 5;
    private static final int ACTION_DRAG = 6;
    /** Crans de molette maximum acceptes par action. */
    private static final int MAX_SCROLL = 10;

    private int left;
    private int right;
    private int middle;
    private int scroll;
    /** Valeur de la barre, en pourcents. */
    private int value = 50;
    private float dragX = 4f;
    private float dragY = 7f;
    /** Vrai : tous les joueurs qui voient la fenetre peuvent l'utiliser. */
    private boolean shared;
    /** Vrai une fois la fenetre orientee vers le regard du joueur (sauvegarde). */
    private boolean oriented;

    private HoloWindow window;
    private HoloWidgetButton leftButton;
    private HoloWidgetMultiButton otherButton;
    private HoloWidgetScrollZone scrollZone;
    private HoloWidgetValueBar valueBar;
    private HoloWidgetDraggable draggable;
    private HoloWidgetSwitch accessSwitch;

    @Override
    public void onLoad()
    {
        window = addObject(new HoloWindow("interaction", new HoloAnchor.Local(false),
                HoloTransform.translation(0f, 0f, DISTANCE), WIDTH, HEIGHT, 1f / 160f));
        window.setVisibility(HoloVisibility.PUBLIC);
        window.setAccess(shared ? HoloAccess.EVERYONE : HoloAccess.OWNER);
        window.setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK, HoloControl.MIDDLE_CLICK);
        HoloPanel root = window.root();
        root.setPattern(0x80).setBorder(ACCENT, 1f);

        root.add(new HoloLabel(4, 4, WIDTH - 8, 14, Component.literal("Interaction"))
                .setAlign(HoloLabel.Align.CENTER).setColor(ACCENT));

        leftButton = root.add(new HoloWidgetButton(6, 22, 72, 20, Component.empty(), button -> send(ACTION_LEFT)));
        otherButton = root.add(new HoloWidgetMultiButton(84, 22, 150, 20, Component.empty()));
        otherButton.on(HoloControl.RIGHT_CLICK, button -> send(ACTION_RIGHT))
                .on(HoloControl.MIDDLE_CLICK, button -> send(ACTION_MIDDLE));

        // Le centre de la fenetre (120, 104) tombe entre deux zones : la boite a souris n'est pas visee au centre.
        scrollZone = root.add(new HoloWidgetScrollZone(6, 48, 111, 44, Component.literal("Zone molette"),
                Component.literal("Molette !"), (zone, steps) -> sendInt(ACTION_SCROLL, steps)));
        root.add(new HoloWidgetHoverZone(123, 48, 111, 44, Component.literal("Regarde-moi"),
                Component.literal("Je te vois !")));
        root.add(new DemoAnimation(6, 98, 111, 36));
        root.add(new HoloWidgetMouseBox(123, 98, 111, 36)).setCaptureOnHover(true)
                .setRecenterControl(HoloControl.mapping("key.jump"))
                .setTexts(Component.literal("Zone de capture"), Component.literal("Souris capturee"),
                        Component.literal("Echap / Entree"));

        valueBar = root.add(new HoloWidgetValueBar(6, 140, 228, 14, Component.literal("Valeur"),
                (bar, v) -> sendInt(ACTION_VALUE, Math.round(v * 100f))));

        HoloPanel track = root.add(new HoloPanel(6, 158, 228, 28));
        track.setBackground(0x30000000).setBorder(HoloColor.secondary(0x60).lighter(0.66f), 1f);
        draggable = track.add(new HoloWidgetDraggable(dragX, dragY, 60, 14, Component.literal("Glisse-moi")));
        draggable.setOnDrop(widget -> sendAction(window, buf -> {
            buf.writeByte(ACTION_DRAG);
            buf.writeFloat(widget.x());
            buf.writeFloat(widget.y());
        }));

        accessSwitch = root.add(new HoloWidgetSwitch(6, 190, 228, 14, Component.literal("Ouvert a tous"),
                (toggle, requested) -> sendAction(window, buf -> {
                    buf.writeByte(ACTION_ACCESS);
                    buf.writeBoolean(requested);
                })));
        accessSwitch.setPredictive(false);
        refresh();
    }

    /** Cote client : action au nom de la fenetre. */
    private void send(int action) { sendAction(window, buf -> buf.writeByte(action)); }

    private void sendInt(int action, int argument)
    {
        sendAction(window, buf -> {
            buf.writeByte(action);
            buf.writeVarInt(argument);
        });
    }

    private void refresh()
    {
        leftButton.setText(Component.literal("Gauche : " + left));
        otherButton.setText(Component.literal("Droit : " + right + " | Molette : " + middle));
        scrollZone.setDetail(Component.literal("Crans : " + scroll));
        valueBar.setValue(value / 100f);
        draggable.setPosition(dragX, dragY);
        accessSwitch.setOn(shared);
    }

    @Override
    public void serverTick(ServerLevel level)
    {
        if (oriented) return;
        Player player = level.getPlayerByUUID(owner());
        if (player == null) return;
        window.transform().setTranslation(player.getYRot(), 0f, DISTANCE);
        oriented = true;
    }

    @Override
    public void handleAction(ServerPlayer player, HoloObject source, FriendlyByteBuf buf)
    {
        if (source != window) return;
        switch (buf.readByte()) {
            case ACTION_LEFT -> left++;
            case ACTION_RIGHT -> right++;
            case ACTION_MIDDLE -> middle++;
            case ACTION_SCROLL -> scroll += Mth.clamp(buf.readVarInt(), -MAX_SCROLL, MAX_SCROLL);
            case ACTION_VALUE -> value = Mth.clamp(buf.readVarInt(), 0, 100);
            case ACTION_DRAG -> {
                float x = buf.readFloat();
                float y = buf.readFloat();
                if (!Float.isFinite(x) || !Float.isFinite(y)) return;
                // Le widget serveur borne la position a son panneau.
                draggable.setPosition(x, y);
                dragX = draggable.x();
                dragY = draggable.y();
            }
            case ACTION_ACCESS -> {
                if (!player.getUUID().equals(owner())) return;
                shared = buf.readBoolean();
                window.setAccess(shared ? HoloAccess.EVERYONE : HoloAccess.OWNER);
            }
            default -> {
                return;
            }
        }
        markDirty();
    }

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeVarInt(left);
        buf.writeVarInt(right);
        buf.writeVarInt(middle);
        buf.writeVarInt(scroll);
        buf.writeVarInt(value);
        buf.writeFloat(dragX);
        buf.writeFloat(dragY);
        buf.writeBoolean(shared);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        left = buf.readVarInt();
        right = buf.readVarInt();
        middle = buf.readVarInt();
        scroll = buf.readVarInt();
        value = buf.readVarInt();
        dragX = buf.readFloat();
        dragY = buf.readFloat();
        shared = buf.readBoolean();
    }

    @Override
    public void onSync() { refresh(); }

    @Override
    public void save(CompoundTag tag)
    {
        tag.putInt("left", left);
        tag.putInt("right", right);
        tag.putInt("middle", middle);
        tag.putInt("scroll", scroll);
        tag.putInt("value", value);
        tag.putFloat("drag_x", dragX);
        tag.putFloat("drag_y", dragY);
        tag.putBoolean("shared", shared);
        tag.putBoolean("oriented", oriented);
    }

    @Override
    public void load(CompoundTag tag)
    {
        left = tag.getInt("left");
        right = tag.getInt("right");
        middle = tag.getInt("middle");
        scroll = tag.getInt("scroll");
        value = tag.contains("value") ? Mth.clamp(tag.getInt("value"), 0, 100) : 50;
        if (tag.contains("drag_x")) {
            dragX = tag.getFloat("drag_x");
            dragY = tag.getFloat("drag_y");
        }
        shared = tag.getBoolean("shared");
        oriented = tag.getBoolean("oriented");
    }
}
