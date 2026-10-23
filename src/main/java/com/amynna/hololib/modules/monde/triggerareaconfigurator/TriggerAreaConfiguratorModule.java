package com.amynna.hololib.modules.monde.triggerareaconfigurator;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.modules.monde.trigger.TriggerCommands;
import com.amynna.hololib.modules.monde.trigger.TriggerOps;
import com.amynna.hololib.modules.monde.triggerarea.TriggerAreaModule;
import com.amynna.hololib.modules.monde.triggerarea.TriggerAreaZone;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Module {@code hololib:trigger_area_configurator} (memoire de position, marker), reserve aux operateurs de niveau {@value TriggerOps#CONFIG_LEVEL} : reglages de
 * la Trigger Area <b>du meme marker</b> ({@link TriggerAreaModule#of}, deux cotes ; sans Trigger Area : « Trigger Area
 * requise sur ce marker », rien d'editable). Il ne porte aucun etat : la Trigger Area reste la seule autorite, modifiee
 * par son API serveur ; la fenetre ({@link TriggerAreaConfiguratorWindow}) relit son etat quand son compteur de
 * changements bouge ({@link TriggerAreaModule#revision()}).
 * <p>
 * <b>Actions</b> ({@code sendAction(window, ...)}, premier octet = type), verifiees cote serveur : operateur d'abord
 * (refus silencieux), source = la fenetre, Trigger Area presente ; {@code liste} = {@code TriggerAreaModule.KIND_*}
 * (entree, sortie, tick), chaque liste est executee dans l'ordre :
 * <pre>
 * 0 ADD     + byte liste + utf commande                  ajoute a la fin de la liste (vide = ignoree)
 * 1 REMOVE  + byte liste + varint index + utf commande   retire la ligne si elle vaut encore cette commande
 * 2 MOVE    + byte liste + varint from + varint to       deplace une ligne
 * 7 EDIT    + byte liste + varint index + utf ancien + utf nouveau   remplace une ligne si elle vaut encore l'ancien
 * 3 VISIBLE + boolean        zone dessinee
 * 4 PLAYERS + boolean        les joueurs declenchent la zone
 * 5 LIVING  + boolean        les autres entites vivantes declenchent la zone
 * 6 OP      + boolean        « Est OP » : seuls les joueurs operateurs declenchent la zone
 * </pre>
 * <b>Acces</b> de la fenetre : {@code HoloAccess.players(<operateurs connectes>)}, recalcule chaque tick serveur
 * ({@link TriggerOps#updateAccess}) ; fenetre dessinee seulement chez un operateur ({@code shown()}).
 */
public final class TriggerAreaConfiguratorModule extends HoloModule
{
    public static final int ACTION_ADD = 0;
    public static final int ACTION_REMOVE = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_EDIT = 7;
    public static final int ACTION_VISIBLE = 3;
    public static final int ACTION_PLAYERS = 4;
    public static final int ACTION_LIVING = 5;
    public static final int ACTION_OP = 6;
    /** Ecart entre la zone par defaut et la fenetre (placement par defaut), en blocs. */
    public static final float WINDOW_GAP = 0.3f;
    private static final Component NO_AREA = Component.literal("Trigger Area requise sur ce marker");

    private TriggerAreaConfiguratorWindow window;
    private final Set<UUID> ops = new HashSet<>();
    /** Client : Trigger Area et revision affichees par la fenetre. */
    private TriggerAreaModule shown;
    private int shownRevision = -1;

    /**
     * Placement par defaut de la fenetre (aussi son RESET) : a droite de la zone par defaut vue de face ({@code +X}),
     * centree sur sa hauteur.
     */
    public static HoloTransform defaultWindowTransform()
    {
        float x = TriggerAreaZone.DEFAULT_SCALE + WINDOW_GAP
                + TriggerAreaConfiguratorWindow.WIDTH * TriggerAreaConfiguratorWindow.PIXEL_SCALE * 0.5f;
        return HoloTransform.translation(x, TriggerAreaZone.DEFAULT_HEIGHT, 0f);
    }

    // ---------------------------------------------------------------- cycle de vie

    @Override
    public void onLoad()
    {
        window = addObject(new TriggerAreaConfiguratorWindow("window", this, defaultWindowTransform()));
        refresh();
    }

    @Override
    public void onUnload()
    {
        shown = null;
        shownRevision = -1;
    }

    // ---------------------------------------------------------------- serveur

    @Override
    public void serverTick(ServerLevel level)
    {
        TriggerOps.updateAccess(window, level.getServer(), ops);
    }

    /** Operateurs seulement (verifie en premier), source = la fenetre, Trigger Area du marker requise. */
    @Override
    public void handleAction(ServerPlayer player, HoloObject source, FriendlyByteBuf buf)
    {
        if (!player.hasPermissions(TriggerOps.CONFIG_LEVEL)) return;
        if (source == null || source != window) return;
        TriggerAreaModule area = TriggerAreaModule.of(this);
        if (area == null) {
            player.displayClientMessage(NO_AREA, true);
            return;
        }
        int action = buf.readByte();
        switch (action) {
            case ACTION_ADD -> {
                int kind = buf.readByte();
                area.addCommand(kind, buf.readUtf(TriggerCommands.MAX_LENGTH));
            }
            case ACTION_REMOVE -> {
                int kind = buf.readByte();
                int index = buf.readVarInt();
                area.removeCommand(kind, index, buf.readUtf(TriggerCommands.MAX_LENGTH));
            }
            case ACTION_EDIT -> {
                int kind = buf.readByte();
                int index = buf.readVarInt();
                String expected = buf.readUtf(TriggerCommands.MAX_LENGTH);
                area.editCommand(kind, index, expected, buf.readUtf(TriggerCommands.MAX_LENGTH));
            }
            case ACTION_MOVE -> {
                int kind = buf.readByte();
                int from = buf.readVarInt();
                area.moveCommand(kind, from, buf.readVarInt());
            }
            case ACTION_VISIBLE -> area.setVisible(buf.readBoolean());
            case ACTION_PLAYERS -> area.setPlayers(buf.readBoolean());
            case ACTION_LIVING -> area.setLiving(buf.readBoolean());
            case ACTION_OP -> area.setOpOnly(buf.readBoolean());
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- client

    /** Fenetre rafraichie quand la Trigger Area du marker ou son etat changent. */
    @Override
    public void clientTick()
    {
        refresh();
    }

    private void refresh()
    {
        if (window == null) return;
        TriggerAreaModule area = TriggerAreaModule.of(this);
        int revision = area != null ? area.revision() : -1;
        if (area == shown && revision == shownRevision) return;
        shown = area;
        shownRevision = revision;
        window.showState(area);
    }

    // ---------------------------------------------------------------- demandes du client

    /** Client : ajoute {@code command} a la liste {@code kind} ({@code TriggerAreaModule.KIND_*}). */
    void requestAdd(int kind, String command)
    {
        sendTyped(ACTION_ADD, buf -> {
            buf.writeByte(kind);
            buf.writeUtf(command, TriggerCommands.MAX_LENGTH);
        });
    }

    /** Client : retire la ligne {@code index} de la liste {@code kind} ({@code command} = son texte, verifie). */
    void requestRemove(int kind, int index, String command)
    {
        sendTyped(ACTION_REMOVE, buf -> {
            buf.writeByte(kind);
            buf.writeVarInt(index);
            buf.writeUtf(command, TriggerCommands.MAX_LENGTH);
        });
    }

    /** Client : remplace la ligne {@code index} de la liste {@code kind} ({@code old} = son texte, verifie). */
    void requestEdit(int kind, int index, String old, String command)
    {
        sendTyped(ACTION_EDIT, buf -> {
            buf.writeByte(kind);
            buf.writeVarInt(index);
            buf.writeUtf(old, TriggerCommands.MAX_LENGTH);
            buf.writeUtf(command, TriggerCommands.MAX_LENGTH);
        });
    }

    /** Client : deplace une ligne de la liste {@code kind}. */
    void requestMove(int kind, int from, int to)
    {
        sendTyped(ACTION_MOVE, buf -> {
            buf.writeByte(kind);
            buf.writeVarInt(from);
            buf.writeVarInt(to);
        });
    }

    /** Client : option {@code ACTION_VISIBLE}, {@code ACTION_PLAYERS}, {@code ACTION_LIVING} ou {@code ACTION_OP}. */
    void requestFlag(int action, boolean on) { sendTyped(action, buf -> buf.writeBoolean(on)); }

    private void sendTyped(int action, Consumer<FriendlyByteBuf> content)
    {
        if (window == null) return;
        sendAction(window, buf -> {
            buf.writeByte(action);
            content.accept(buf);
        });
    }
}
