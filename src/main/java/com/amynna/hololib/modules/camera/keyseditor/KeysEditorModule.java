package com.amynna.hololib.modules.camera.keyseditor;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.camera.config.Configurable;
import com.amynna.hololib.modules.camera.keys.KeySet;
import com.amynna.hololib.modules.camera.keys.KeysModule;
import com.amynna.hololib.modules.camera.targeting.TargetingModule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Module {@code hololib:keys_editor} (memoire de joueur, {@link Configurable}) : ajoute et retire des cles du module
 * {@code hololib:keys}, par sa fenetre de Config ({@link KeysEditorWindow}) :
 * <ul>
 *     <li>« Soi-meme » : les cles de la memoire active du proprietaire ;</li>
 *     <li>« Cible » (compatibilite Ciblage) : les cles du joueur cible par le module {@code hololib:targeting} du
 *     proprietaire, reserve aux operateurs (niveau 2, verifie par le serveur).</li>
 * </ul>
 * Le <b>serveur</b> resout le sujet chaque tick ({@link Status}, nom, cles) et l'envoie au proprietaire seulement
 * quand il change (la fenetre de Config est {@code PUBLIC} : un observateur ne recoit que l'onglet). Actions : onglet
 * ({@code MODE}), {@code ADD} / {@code REMOVE} d'une cle ; le serveur resout de nouveau le sujet avant d'appliquer.
 */
public final class KeysEditorModule extends HoloModule implements Configurable
{
    static final int SELF = 0;
    static final int TARGET = 1;

    private static final byte ACTION_MODE = 0;
    private static final byte ACTION_ADD = 1;
    private static final byte ACTION_REMOVE = 2;
    private static final Component NAME = Component.literal("Cles");

    /** Etat du sujet edite, calcule par le serveur. */
    enum Status
    {
        OK, NO_KEYS, NOT_OP, NO_TARGETING, NO_TARGET, NOT_PLAYER, TARGET_NO_KEYS;

        static final Status[] VALUES = values();
    }

    // Etat (deux cotes).
    private int mode = SELF;
    private Status status = Status.NO_KEYS;
    private String subjectName = "";
    private final KeySet shown = new KeySet();

    // Serveur : dernier sujet envoye.
    private KeysModule lastSubject;
    private int lastRevision;
    private Status resolvedStatus;
    private String resolvedName;

    private KeysEditorWindow window;

    int mode() { return mode; }

    Status status() { return status; }

    String subjectName() { return subjectName; }

    KeySet shown() { return shown; }

    @Override
    public void onLoad()
    {
        window = addObject(new KeysEditorWindow("config", NAME, this));
        window.refresh();
    }

    @Override
    public void onSync() { window.refresh(); }

    @Override
    public Component configName() { return NAME; }

    @Override
    public ConfigWindow configWindow() { return window; }

    // ---------------------------------------------------------------- client (proprietaire)

    void requestMode(int mode)
    {
        if (!host().canAct() || mode == this.mode) return;
        this.mode = mode;
        window.refresh();
        sendAction(buf -> buf.writeByte(ACTION_MODE).writeByte(mode));
    }

    void requestAdd(String key)
    {
        if (host().canAct()) sendAction(buf -> writeKey(buf, ACTION_ADD, key));
    }

    void requestRemove(String key)
    {
        if (host().canAct()) sendAction(buf -> writeKey(buf, ACTION_REMOVE, key));
    }

    private static void writeKey(FriendlyByteBuf buf, byte action, String key)
    {
        buf.writeByte(action);
        buf.writeUtf(key, KeySet.MAX_LENGTH);
    }

    // ---------------------------------------------------------------- serveur

    @Override
    public void serverTick(ServerLevel level)
    {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(owner());
        if (owner == null) return;
        KeysModule subject = resolve(owner);
        int revision = subject != null ? subject.revision() : 0;
        if (resolvedStatus == status && subject == lastSubject && revision == lastRevision
                && resolvedName.equals(subjectName)) {
            return;
        }
        status = resolvedStatus;
        subjectName = resolvedName;
        shown.setAll(subject != null ? subject.keys() : List.of());
        lastSubject = subject;
        lastRevision = revision;
        markDirty();
    }

    /** Sujet edite selon l'onglet et les droits ; pose {@link #resolvedStatus} et {@link #resolvedName}. */
    private KeysModule resolve(ServerPlayer owner)
    {
        resolvedName = "";
        if (mode == SELF) {
            KeysModule own = KeysModule.of(owner.getUUID());
            resolvedStatus = own != null ? Status.OK : Status.NO_KEYS;
            resolvedName = owner.getGameProfile().getName();
            return own;
        }
        if (!owner.hasPermissions(2)) {
            resolvedStatus = Status.NOT_OP;
            return null;
        }
        TargetingModule targeting = TargetingModule.of(owner.getUUID());
        if (targeting == null) {
            resolvedStatus = Status.NO_TARGETING;
            return null;
        }
        Entity target = targeting.targetEntity(owner.serverLevel());
        if (target == null) {
            resolvedStatus = targeting.hasTarget() ? Status.NOT_PLAYER : Status.NO_TARGET;
            return null;
        }
        if (!(target instanceof ServerPlayer player)) {
            resolvedStatus = Status.NOT_PLAYER;
            return null;
        }
        resolvedName = player.getGameProfile().getName();
        KeysModule keys = KeysModule.of(player.getUUID());
        resolvedStatus = keys != null ? Status.OK : Status.TARGET_NO_KEYS;
        return keys;
    }

    @Override
    public void handleAction(ServerPlayer player, FriendlyByteBuf buf)
    {
        if (!player.getUUID().equals(owner())) return;
        byte action = buf.readByte();
        if (action == ACTION_MODE) {
            int requested = buf.readByte();
            if (requested == SELF || requested == TARGET) mode = requested;
            markDirty();
            return;
        }
        if (action != ACTION_ADD && action != ACTION_REMOVE) return;
        String key = buf.readUtf(KeySet.MAX_LENGTH);
        KeysModule subject = resolve(player);
        if (subject == null) return;
        if (action == ACTION_ADD) subject.addKey(key);
        else subject.removeKey(key);
    }

    @Override
    public void save(CompoundTag tag) { tag.putByte("mode", (byte) mode); }

    @Override
    public void load(CompoundTag tag) { mode = tag.getByte("mode") == TARGET ? TARGET : SELF; }

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeByte(mode);
        buf.writeBoolean(forOwner);
        if (!forOwner) return;
        buf.writeByte(status.ordinal());
        buf.writeUtf(subjectName, 64);
        shown.write(buf);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        mode = buf.readByte() == TARGET ? TARGET : SELF;
        if (!buf.readBoolean()) return;
        int ordinal = buf.readUnsignedByte();
        status = ordinal < Status.VALUES.length ? Status.VALUES[ordinal] : Status.NO_KEYS;
        subjectName = buf.readUtf(64);
        shown.read(buf);
    }
}
