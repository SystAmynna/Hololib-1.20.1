package com.amynna.hololib.holo.theme;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

/**
 * Theme de la bibliotheque : identifiant stable, createur ({@code null} = theme du serveur, cree par un op ou
 * integre) et valeur. Immuable : une edition remplace l'entree.
 */
public record HoloThemeEntry(int id, UUID creator, String creatorName, HoloTheme theme)
{
    public HoloThemeEntry
    {
        Objects.requireNonNull(theme);
        if (creatorName == null) creatorName = "";
    }

    public HoloThemeEntry with(HoloTheme theme) { return new HoloThemeEntry(id, creator, creatorName, theme); }

    /** Vrai pour un theme du serveur (sans createur). */
    public boolean serverTheme() { return creator == null; }

    public void write(FriendlyByteBuf buf)
    {
        buf.writeVarInt(id);
        buf.writeBoolean(creator != null);
        if (creator != null) {
            buf.writeUUID(creator);
            buf.writeUtf(creatorName, 64);
        }
        theme.write(buf);
    }

    public static HoloThemeEntry read(FriendlyByteBuf buf)
    {
        int id = buf.readVarInt();
        UUID creator = null;
        String creatorName = "";
        if (buf.readBoolean()) {
            creator = buf.readUUID();
            creatorName = buf.readUtf(64);
        }
        return new HoloThemeEntry(id, creator, creatorName, HoloTheme.read(buf));
    }

    public CompoundTag save()
    {
        CompoundTag tag = theme.save();
        tag.putInt("id", id);
        if (creator != null) {
            tag.putUUID("creator", creator);
            tag.putString("creatorName", creatorName);
        }
        return tag;
    }

    public static HoloThemeEntry load(CompoundTag tag)
    {
        UUID creator = tag.hasUUID("creator") ? tag.getUUID("creator") : null;
        return new HoloThemeEntry(tag.getInt("id"), creator, tag.getString("creatorName"),
                HoloTheme.load(tag, HoloTheme.DEFAULT));
    }
}
