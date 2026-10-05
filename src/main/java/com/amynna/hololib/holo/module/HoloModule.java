package com.amynna.hololib.holo.module;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.network.HoloModuleActionPacket;
import com.amynna.hololib.network.HoloNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Module holographique : un etat, une logique et les {@link HoloObject} qui l'affichent, dans une memoire de joueur,
 * de dimension ou de position ({@link HoloModuleHost#scope()}). Le module existe des deux cotes, comme une
 * {@code Entity} : l'instance serveur fait autorite (logique gameplay, sauvegarde dans la {@code HoloMemory}) ;
 * chaque client concerne en a un miroir, recree a partir du type et de l'etat synchronise.
 * <p>
 * Cycle de vie : {@link #load} (sauvegarde, serveur) ou {@link #readSync} (reseau, client), puis {@link #onLoad} qui
 * cree les objets <b>a partir de l'etat</b> (meme code des deux cotes, memes cles), puis le framework restaure les
 * ancrages et visibilites. A chaque mise a jour cote client : {@link #readSync} puis {@link #onSync}. Un objet ajoute
 * ou retire plus tard doit l'etre des deux cotes (le client le deduit de l'etat recu).
 * <p>
 * Les widgets et les objets ne transitent jamais sur le reseau : seul l'etat ecrit par {@link #writeSync} circule.
 */
public abstract class HoloModule
{
    private HoloModuleType<?> type;
    private UUID instanceId;
    private HoloModuleHost host;
    private final Map<String, HoloObject> objects = new LinkedHashMap<>();
    private final Collection<HoloObject> objectsView = Collections.unmodifiableCollection(objects.values());
    private boolean stateDirty;

    // ---------------------------------------------------------------- identite

    public final HoloModuleType<?> type() { return type; }

    /** Identifiant de l'instance, stable entre les sauvegardes (un joueur peut avoir plusieurs instances d'un type). */
    public final UUID instanceId() { return instanceId; }

    public final HoloModuleHost host() { return host; }

    public final HoloScope scope() { return host.scope(); }

    /** Joueur proprietaire, ou {@code null} pour un module de dimension ou de position. */
    public final UUID owner() { return host.scope().owner(); }

    // ---------------------------------------------------------------- objets

    /** Ajoute un objet ; sa cle doit etre unique dans le module. Renvoie l'objet. */
    protected final <O extends HoloObject> O addObject(O object)
    {
        if (objects.containsKey(object.key()))
        {
            throw new IllegalArgumentException("Cle d'objet deja utilisee : " + object.key());
        }
        object.attach(this);
        objects.put(object.key(), object);
        return object;
    }

    protected final void removeObject(String key) { objects.remove(key); }

    public final HoloObject object(String key) { return objects.get(key); }

    /** Objets du module, dans l'ordre d'ajout (vue non modifiable). */
    public final Collection<HoloObject> objects() { return objectsView; }

    // ---------------------------------------------------------------- etat

    /** Cote serveur : l'etat du module a change, il sera envoye aux clients au prochain tick. */
    protected final void markDirty() { stateDirty = true; }

    /**
     * Cote client : envoie une action au module serveur ({@link #handleAction}), si ce miroir le peut
     * ({@link HoloModuleHost#canAct()}). Le contenu est ecrit par {@code writer}.
     */
    protected final void sendAction(Consumer<FriendlyByteBuf> writer)
    {
        if (!host.canAct()) return;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buf);
            byte[] payload = new byte[buf.readableBytes()];
            buf.readBytes(payload);
            HoloNetwork.CHANNEL.sendToServer(new HoloModuleActionPacket(host.scope(), instanceId, payload));
        } finally {
            buf.release();
        }
    }

    // ---------------------------------------------------------------- hooks

    /** Cree les objets a partir de l'etat courant (deux cotes). */
    public void onLoad() {}

    /** Le module est retire ou sa memoire dechargee (deux cotes). */
    public void onUnload() {}

    /** Chaque tick serveur, tant que la memoire est chargee ; {@code level} = niveau du joueur ou de la memoire. */
    public void serverTick(ServerLevel level) {}

    /** Chaque tick client, avant le tick des objets. */
    public void clientTick() {}

    /** Ecrit l'etat persistant (serveur). */
    public void save(CompoundTag tag) {}

    /** Relit l'etat persistant (serveur), avant {@link #onLoad}. */
    public void load(CompoundTag tag) {}

    /**
     * Ecrit l'etat envoye aux clients.
     *
     * @param forOwner faux pour les autres joueurs : n'ecrire que ce qu'ils ont le droit de voir
     */
    public void writeSync(FriendlyByteBuf buf, boolean forOwner) {}

    /** Relit l'etat ecrit par {@link #writeSync} (client). */
    public void readSync(FriendlyByteBuf buf) {}

    /** Cote client, apres une mise a jour de l'etat : rafraichir les widgets. */
    public void onSync() {}

    /**
     * Cote serveur : action envoyee par un client autorise (proprietaire ; pour une dimension ou une position, un
     * joueur qui la recoit). A valider, le client n'est pas fiable.
     */
    public void handleAction(ServerPlayer player, FriendlyByteBuf buf) {}

    // ---------------------------------------------------------------- interne

    @ApiStatus.Internal
    final void bind(HoloModuleType<?> type, UUID instanceId, HoloModuleHost host)
    {
        if (this.type != null) throw new IllegalStateException("Module deja initialise");
        this.type = type;
        this.instanceId = instanceId;
        this.host = host;
    }

    @ApiStatus.Internal
    public final boolean stateDirty() { return stateDirty; }

    @ApiStatus.Internal
    public final void clearDirty()
    {
        stateDirty = false;
        for (HoloObject object : objects.values()) object.clearSyncDirty();
    }

    /** Usage interne : etat complet sauvegarde (etat du module + ancrage, transform et visibilite de chaque objet). */
    @ApiStatus.Internal
    public final CompoundTag saveAll()
    {
        CompoundTag state = new CompoundTag();
        save(state);
        CompoundTag objectTags = new CompoundTag();
        for (HoloObject object : objects.values()) {
            CompoundTag objectTag = new CompoundTag();
            objectTag.put("anchor", object.anchor().save());
            objectTag.put("transform", object.transform().save());
            objectTag.putString("visibility", object.visibility().name());
            objectTags.put(object.key(), objectTag);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("state", state);
        tag.put("objects", objectTags);
        return tag;
    }

    /** Usage interne (serveur) : relit {@link #saveAll}, cree les objets et restaure leurs placements. */
    @ApiStatus.Internal
    public final void loadAll(CompoundTag tag)
    {
        load(tag.getCompound("state"));
        onLoad();
        CompoundTag objectTags = tag.getCompound("objects");
        for (HoloObject object : objects.values()) {
            if (!objectTags.contains(object.key(), Tag.TAG_COMPOUND)) continue;
            CompoundTag objectTag = objectTags.getCompound(object.key());
            HoloAnchor anchor = HoloAnchor.load(objectTag.getCompound("anchor"));
            HoloVisibility visibility = object.visibility();
            try {
                visibility = HoloVisibility.valueOf(objectTag.getString("visibility"));
            } catch (IllegalArgumentException e) {
                HoloLib.LOGGER.warn("Visibilite invalide pour l'objet {} du module {}", object.key(), type);
            }
            object.restore(visibility, anchor, HoloTransform.load(objectTag.getCompound("transform")));
        }
    }

    /** Usage interne : decharge le module et oublie ses objets. */
    @ApiStatus.Internal
    public final void unload()
    {
        onUnload();
        objects.clear();
    }
}
