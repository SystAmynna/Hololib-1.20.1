package com.amynna.hololib.modules.camera.config;

import com.amynna.hololib.holo.module.HoloModule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

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
 */
public final class ConfigModule extends HoloModule
{
    private static final int MAX_ID_LENGTH = 256;
    private static final int MAX_PINNED = 64;

    private final Set<String> pinned = new HashSet<>();
    private ConfigNutWindow nut;
    private ConfigListWindow list;

    @Override
    public void onLoad()
    {
        nut = addObject(new ConfigNutWindow("nut", button -> toggleList()));
        list = addObject(new ConfigListWindow("list"));
        list.link(on -> requestPinned(listId(), on));
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
        sendAction(buf -> buf.writeUtf(id, MAX_ID_LENGTH).writeBoolean(on));
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
        String id = buf.readUtf(MAX_ID_LENGTH);
        boolean on = buf.readBoolean();
        if (on ? pinned.size() < MAX_PINNED && pinned.add(id) : pinned.remove(id)) markDirty();
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
