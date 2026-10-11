package com.amynna.hololib.modules.camera.config;

import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.player.PlayerHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Config : point d'entree de la configuration en jeu des modules {@link Configurable} de la memoire du joueur. Deux
 * objets, prives, visibles seulement en mode focus :
 * <ul>
 *     <li>{@code nut} ({@link ConfigNutWindow}) : bouton ecrou ; un clic le fait tourner et ouvre / ferme la liste ;</li>
 *     <li>{@code list} ({@link ConfigListWindow}) : les modules configurables, un clic ouvre leur fenetre.</li>
 * </ul>
 * Etat : les fenetres epinglees (rouvertes avec le focus), par identifiant de type du module configurable (celui de
 * Config pour la liste). Sauvegarde, synchronise au proprietaire, modifie par {@link #requestPinned}. L'ouverture des
 * fenetres est locale au client ({@code client.modules.camera.config.ConfigController}).
 * <p>
 * Relaie aussi la position relative au regard d'une fenetre deplacee au cadre ({@link #requestOffset}) : l'objet
 * appartient a un module voisin de la memoire du joueur, que le serveur retrouve par son {@code instanceId}.
 */
public final class ConfigModule extends HoloModule
{
    private static final int MAX_ID_LENGTH = 256;
    private static final int MAX_PINNED = 64;
    private static final int MAX_KEY_LENGTH = 64;
    private static final byte ACTION_PIN = 0;
    private static final byte ACTION_OFFSET = 1;

    private final Set<String> pinned = new HashSet<>();
    private ConfigNutWindow nut;
    private ConfigListWindow list;

    @Override
    public void onLoad()
    {
        nut = addObject(new ConfigNutWindow("nut", button -> toggleList()));
        list = addObject(new ConfigListWindow("list"));
        list.link(on -> requestPinned(listId(), on));
        list.linkMoved(() -> requestOffset(list));
    }

    public ConfigNutWindow nut() { return nut; }

    public ConfigListWindow list() { return list; }

    /** Identifiant d'epinglage de la liste : celui du type de Config. */
    public String listId() { return type().id().toString(); }

    public boolean pinned(String id) { return pinned.contains(id); }

    /** Cote client (proprietaire) : epingle ou desepingle une fenetre, localement puis sur le serveur. */
    public void requestPinned(String id, boolean on)
    {
        if (!host().canAct() || pinned.contains(id) == on || id.length() > MAX_ID_LENGTH) return;
        if (on) pinned.add(id);
        else pinned.remove(id);
        sendAction(buf -> {
            buf.writeByte(ACTION_PIN);
            buf.writeUtf(id, MAX_ID_LENGTH).writeBoolean(on);
        });
    }

    /**
     * Cote client (proprietaire) : envoie la position relative au regard de {@code window} (fenetre de Config d'un
     * module de la memoire du joueur), deja recalculee localement apres un deplacement au cadre.
     */
    public void requestOffset(ConfigWindow window)
    {
        HoloModule owner = window.module();
        if (!host().canAct() || owner == null || window.openOffset() == null
                || window.key().length() > MAX_KEY_LENGTH) {
            return;
        }
        HoloTransform offset = window.openOffset().copy();
        sendAction(buf -> {
            buf.writeByte(ACTION_OFFSET);
            buf.writeUUID(owner.instanceId()).writeUtf(window.key(), MAX_KEY_LENGTH);
            offset.write(buf);
        });
    }

    /** Cote client : clic sur l'ecrou. */
    private void toggleList()
    {
        nut.button().spin();
        if (list.isOpen()) list.close();
        else list.open();
    }

    @Override
    public void handleAction(ServerPlayer player, FriendlyByteBuf buf)
    {
        byte action = buf.readByte();
        if (action == ACTION_PIN) {
            String id = buf.readUtf(MAX_ID_LENGTH);
            boolean on = buf.readBoolean();
            if (on ? pinned.size() < MAX_PINNED && pinned.add(id) : pinned.remove(id)) markDirty();
        } else if (action == ACTION_OFFSET) {
            UUID instanceId = buf.readUUID();
            String key = buf.readUtf(MAX_KEY_LENGTH);
            applyOffset(instanceId, key, HoloTransform.read(buf));
        }
    }

    /**
     * Serveur : position relative d'une {@link ConfigWindow} d'un module de la memoire du proprietaire (seul a pouvoir
     * envoyer une action sans source), bornee comme une edition ; translation et rotation (l'echelle reste celle du
     * serveur). Synchronisee et sauvegardee par le framework.
     */
    private void applyOffset(UUID instanceId, String key, HoloTransform offset)
    {
        PlayerHoloContainer container = owner() != null ? HoloContainers.player(owner()) : null;
        HoloModule module = container != null ? container.module(instanceId) : null;
        HoloObject object = module != null ? module.object(key) : null;
        if (!(object instanceof ConfigWindow) || object.openOffset() == null
                || !new HoloAnchor.Local(true).acceptable(offset)) {
            return;
        }
        object.openOffset().setTranslation(offset.x(), offset.y(), offset.z())
                .setRotation(offset.yaw(), offset.pitch(), offset.roll());
    }

    @Override
    public void save(CompoundTag tag)
    {
        ListTag ids = new ListTag();
        for (String id : pinned) ids.add(StringTag.valueOf(id));
        tag.put("pinned", ids);
    }

    @Override
    public void load(CompoundTag tag)
    {
        pinned.clear();
        ListTag ids = tag.getList("pinned", Tag.TAG_STRING);
        for (int i = 0; i < ids.size() && pinned.size() < MAX_PINNED; i++) pinned.add(ids.getString(i));
    }

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        if (!forOwner) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(pinned.size());
        for (String id : pinned) buf.writeUtf(id, MAX_ID_LENGTH);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        pinned.clear();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) pinned.add(buf.readUtf(MAX_ID_LENGTH));
    }
}
