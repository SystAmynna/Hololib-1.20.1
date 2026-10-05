package com.amynna.hololib.demo;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAccess;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloButton;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Demonstration de l'interaction au regard (memoire de joueur) : une fenetre en camera normale ({@code Local}
 * relatif), orientee vers le regard au premier tick, publique.
 * <ul>
 *     <li>trois boutons, declenches par le clic gauche, droit et molette ;</li>
 *     <li>une zone qui compte les crans de molette quand on la regarde ;</li>
 *     <li>une zone qui reagit au regard ;</li>
 *     <li>une petite animation ;</li>
 *     <li>une zone qui, quand le regard y entre, capture la souris (et Saut) jusqu'a Echap ou Entree ;</li>
 *     <li>un bouton qui bascule le droit d'usage : proprietaire seul ou tous les joueurs qui voient la fenetre (les
 *     joueurs qui inspectent le proprietaire la voient autour de sa tete). Seul le proprietaire peut le basculer.</li>
 * </ul>
 * Les compteurs sont l'etat du module, cote serveur : chaque interaction envoie une action au nom de la fenetre
 * ({@code sendAction(window, ...)}), que le serveur verifie (acces, visibilite, inspection) puis renvoie a tous.
 * Le fond de la fenetre intercepte les clics : viser la fenetre ne casse jamais le bloc derriere.
 */
public final class DemoInteractionModule extends HoloModule
{
    private static final int WIDTH = 240;
    private static final int HEIGHT = 160;
    private static final float DISTANCE = 1.6f;
    private static final int ACCENT = 0xFF40C0FF;

    private static final int ACTION_LEFT = 0;
    private static final int ACTION_RIGHT = 1;
    private static final int ACTION_MIDDLE = 2;
    private static final int ACTION_SCROLL = 3;
    private static final int ACTION_ACCESS = 4;
    /** Crans de molette maximum acceptes par action. */
    private static final int MAX_SCROLL = 10;

    private int left;
    private int right;
    private int middle;
    private int scroll;
    /** Vrai : tous les joueurs qui voient la fenetre peuvent l'utiliser. */
    private boolean shared;
    /** Vrai une fois la fenetre orientee vers le regard du joueur (sauvegarde). */
    private boolean oriented;

    private HoloWindow window;
    private HoloButton leftButton;
    private HoloButton rightButton;
    private HoloButton middleButton;
    private HoloButton accessButton;
    private DemoScrollZone scrollZone;

    @Override
    public void onLoad()
    {
        window = addObject(new HoloWindow("interaction", new HoloAnchor.Local(false),
                HoloTransform.translation(0f, 0f, DISTANCE), WIDTH, HEIGHT, 1f / 160f));
        window.setVisibility(HoloVisibility.PUBLIC);
        window.setAccess(shared ? HoloAccess.EVERYONE : HoloAccess.OWNER);
        window.setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK, HoloControl.MIDDLE_CLICK);
        window.root().setBackground(0x8010304A).setBorder(ACCENT, 1f);

        window.root().add(new HoloLabel(4, 4, WIDTH - 8, 14, Component.literal("Interaction"))
                .setAlign(HoloLabel.Align.CENTER).setColor(ACCENT));

        leftButton = window.root().add(new HoloButton(6, 22, 72, 20, Component.empty(),
                button -> send(ACTION_LEFT, 0)));
        rightButton = window.root().add(new HoloButton(84, 22, 72, 20, Component.empty(),
                button -> send(ACTION_RIGHT, 0)).setTrigger(HoloControl.RIGHT_CLICK));
        middleButton = window.root().add(new HoloButton(162, 22, 72, 20, Component.empty(),
                button -> send(ACTION_MIDDLE, 0)).setTrigger(HoloControl.MIDDLE_CLICK));

        scrollZone = window.root().add(new DemoScrollZone(6, 48, 111, 44, steps -> send(ACTION_SCROLL, steps)));
        window.root().add(new DemoHoverZone(123, 48, 111, 44));
        window.root().add(new DemoAnimation(6, 98, 111, 36));
        window.root().add(new DemoCaptureZone(123, 98, 111, 36));

        accessButton = window.root().add(new HoloButton(6, 140, 228, 14, Component.empty(),
                button -> send(ACTION_ACCESS, 0)).setColors(0x40404040, 0xFFA0A0A0, 0xFFFFFFFF));
        refresh();
    }

    /** Cote client : action au nom de la fenetre. */
    private void send(int action, int value)
    {
        sendAction(window, buf -> {
            buf.writeByte(action);
            if (action == ACTION_SCROLL) buf.writeVarInt(value);
        });
    }

    private void refresh()
    {
        leftButton.setText(Component.literal("Gauche : " + left));
        rightButton.setText(Component.literal("Droit : " + right));
        middleButton.setText(Component.literal("Molette : " + middle));
        scrollZone.setText(Component.literal("Crans : " + scroll));
        accessButton.setText(Component.literal(shared ? "Acces : tout le monde" : "Acces : proprietaire seul"));
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
            case ACTION_ACCESS -> {
                if (!player.getUUID().equals(owner())) return;
                shared = !shared;
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
        buf.writeBoolean(shared);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        left = buf.readVarInt();
        right = buf.readVarInt();
        middle = buf.readVarInt();
        scroll = buf.readVarInt();
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
        shared = tag.getBoolean("shared");
        oriented = tag.getBoolean("oriented");
    }
}
