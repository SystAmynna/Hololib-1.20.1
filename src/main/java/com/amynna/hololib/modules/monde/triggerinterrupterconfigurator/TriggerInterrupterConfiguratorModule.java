package com.amynna.hololib.modules.monde.triggerinterrupterconfigurator;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.modules.monde.trigger.TriggerCommands;
import com.amynna.hololib.modules.monde.trigger.TriggerOps;
import com.amynna.hololib.modules.monde.triggerinterrupter.TriggerInterrupterModule;
import com.amynna.hololib.modules.monde.triggerinterrupter.TriggerInterrupterWindow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Module {@code hololib:trigger_interrupter_configurator} (memoire de position, marker), reserve aux operateurs :
 * reglages du Trigger Interrupter <b>du meme marker</b> ({@link TriggerInterrupterModule#of}, deux cotes ; sans lui :
 * « Trigger Interrupter requis sur ce marker », rien d'editable). Il ne porte aucun etat : le Trigger Interrupter reste
 * la seule autorite, modifie par son API serveur ; la fenetre ({@link TriggerInterrupterConfiguratorWindow}) relit son
 * etat quand son compteur de changements bouge ({@link TriggerInterrupterModule#revision()}).
 * <p>
 * <b>Actions</b> ({@code sendAction(window, ...)}, premier octet = type), verifiees cote serveur : operateur d'abord
 * (refus silencieux), source = la fenetre, Trigger Interrupter present :
 * <pre>
 * 0 TEXT       + byte champ + utf texte   titre, texte du bouton ou texte du dessous (TriggerInterrupterModule.FIELD_*)
 * 1 REQUIRE_OP + boolean                  seul un operateur peut agir sur le bouton
 * 2 ADD       + byte emplacement + utf                  ajoute une commande a la fin de la liste (vide = ignoree)
 * 3 REMOVE    + byte emplacement + varint index + utf   retire la ligne si elle vaut encore cette commande
 * 4 MOVE      + byte emplacement + varint from + varint to   deplace une ligne
 * 5 EDIT      + byte emplacement + varint index + utf ancien + utf nouveau   remplace une ligne si elle vaut encore l'ancien
 * </pre>
 * {@code emplacement} : {@link TriggerInterrupterModule#slot} ; chaque emplacement est une liste de commandes,
 * executee dans l'ordre.
 * <pre>
 * </pre>
 * <b>Acces</b> de la fenetre : {@code HoloAccess.players(<operateurs connectes>)}, recalcule chaque tick serveur
 * ({@link TriggerOps#updateAccess}) ; fenetre dessinee seulement chez un operateur ({@code shown()}).
 */
public final class TriggerInterrupterConfiguratorModule extends HoloModule
{
    public static final int ACTION_TEXT = 0;
    public static final int ACTION_REQUIRE_OP = 1;
    public static final int ACTION_ADD = 2;
    public static final int ACTION_REMOVE = 3;
    public static final int ACTION_MOVE = 4;
    public static final int ACTION_EDIT = 5;
    /** Ecart entre la fenetre de l'Interrupter et celle-ci (placement par defaut), en blocs. */
    public static final float WINDOW_GAP = 0.3f;
    private static final Component NO_INTERRUPTER = Component.literal("Trigger Interrupter requis sur ce marker");

    private TriggerInterrupterConfiguratorWindow window;
    private final Set<UUID> ops = new HashSet<>();
    /** Client : Trigger Interrupter et revision affiches par la fenetre. */
    private TriggerInterrupterModule shown;
    private int shownRevision = -1;

    /**
     * Placement par defaut de la fenetre (aussi son RESET) : a droite de la fenetre de l'Interrupter par defaut vue de
     * face ({@code +X}), a sa hauteur.
     */
    public static HoloTransform defaultWindowTransform()
    {
        float x = TriggerInterrupterWindow.WIDTH * TriggerInterrupterWindow.PIXEL_SCALE * 0.5f + WINDOW_GAP
                + TriggerInterrupterConfiguratorWindow.WIDTH * TriggerInterrupterConfiguratorWindow.PIXEL_SCALE * 0.5f;
        return HoloTransform.translation(x, TriggerInterrupterWindow.DEFAULT_HEIGHT, 0f);
    }

    // ---------------------------------------------------------------- cycle de vie

    @Override
    public void onLoad()
    {
        window = addObject(new TriggerInterrupterConfiguratorWindow("window", this, defaultWindowTransform()));
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

    /** Operateurs seulement (verifie en premier), source = la fenetre, Trigger Interrupter du marker requis. */
    @Override
    public void handleAction(ServerPlayer player, HoloObject source, FriendlyByteBuf buf)
    {
        if (!player.hasPermissions(TriggerOps.OP_LEVEL)) return;
        if (source == null || source != window) return;
        TriggerInterrupterModule interrupter = TriggerInterrupterModule.of(this);
        if (interrupter == null) {
            player.displayClientMessage(NO_INTERRUPTER, true);
            return;
        }
        int action = buf.readByte();
        switch (action) {
            case ACTION_TEXT -> {
                int field = buf.readByte();
                interrupter.setText(field, buf.readUtf(TriggerInterrupterModule.MAX_TEXT));
            }
            case ACTION_REQUIRE_OP -> interrupter.setRequireOp(buf.readBoolean());
            case ACTION_ADD -> {
                int slot = buf.readByte();
                interrupter.addCommand(slot, buf.readUtf(TriggerCommands.MAX_LENGTH));
            }
            case ACTION_REMOVE -> {
                int slot = buf.readByte();
                int index = buf.readVarInt();
                interrupter.removeCommand(slot, index, buf.readUtf(TriggerCommands.MAX_LENGTH));
            }
            case ACTION_EDIT -> {
                int slot = buf.readByte();
                int index = buf.readVarInt();
                String expected = buf.readUtf(TriggerCommands.MAX_LENGTH);
                interrupter.editCommand(slot, index, expected, buf.readUtf(TriggerCommands.MAX_LENGTH));
            }
            case ACTION_MOVE -> {
                int slot = buf.readByte();
                int from = buf.readVarInt();
                interrupter.moveCommand(slot, from, buf.readVarInt());
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- client

    /** Fenetre rafraichie quand le Trigger Interrupter du marker ou son etat changent. */
    @Override
    public void clientTick()
    {
        refresh();
    }

    private void refresh()
    {
        if (window == null) return;
        TriggerInterrupterModule interrupter = TriggerInterrupterModule.of(this);
        int revision = interrupter != null ? interrupter.revision() : -1;
        if (interrupter == shown && revision == shownRevision) return;
        shown = interrupter;
        shownRevision = revision;
        window.showState(interrupter);
    }

    // ---------------------------------------------------------------- demandes du client

    /** Client : texte {@code field} ({@code TriggerInterrupterModule.FIELD_*}). */
    void requestText(int field, String text)
    {
        sendTyped(ACTION_TEXT, buf -> {
            buf.writeByte(field);
            buf.writeUtf(text, TriggerInterrupterModule.MAX_TEXT);
        });
    }

    /** Client : « OP requis ». */
    void requestRequireOp(boolean on) { sendTyped(ACTION_REQUIRE_OP, buf -> buf.writeBoolean(on)); }

    /** Client : ajoute {@code command} a la liste de l'emplacement {@code slot}. */
    void requestAdd(int slot, String command)
    {
        sendTyped(ACTION_ADD, buf -> {
            buf.writeByte(slot);
            buf.writeUtf(command, TriggerCommands.MAX_LENGTH);
        });
    }

    /** Client : retire la ligne {@code index} de la liste de {@code slot} ({@code command} = son texte, verifie). */
    void requestRemove(int slot, int index, String command)
    {
        sendTyped(ACTION_REMOVE, buf -> {
            buf.writeByte(slot);
            buf.writeVarInt(index);
            buf.writeUtf(command, TriggerCommands.MAX_LENGTH);
        });
    }

    /** Client : remplace la ligne {@code index} de la liste de {@code slot} ({@code old} = son texte, verifie). */
    void requestEdit(int slot, int index, String old, String command)
    {
        sendTyped(ACTION_EDIT, buf -> {
            buf.writeByte(slot);
            buf.writeVarInt(index);
            buf.writeUtf(old, TriggerCommands.MAX_LENGTH);
            buf.writeUtf(command, TriggerCommands.MAX_LENGTH);
        });
    }

    /** Client : deplace une ligne de la liste de {@code slot}. */
    void requestMove(int slot, int from, int to)
    {
        sendTyped(ACTION_MOVE, buf -> {
            buf.writeByte(slot);
            buf.writeVarInt(from);
            buf.writeVarInt(to);
        });
    }

    private void sendTyped(int action, Consumer<FriendlyByteBuf> content)
    {
        if (window == null) return;
        sendAction(window, buf -> {
            buf.writeByte(action);
            content.accept(buf);
        });
    }
}
