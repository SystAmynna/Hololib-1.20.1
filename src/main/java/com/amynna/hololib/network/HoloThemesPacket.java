package com.amynna.hololib.network;

import com.amynna.hololib.client.theme.ClientThemes;
import com.amynna.hololib.holo.theme.HoloThemeEntry;
import com.amynna.hololib.holo.theme.HoloThemeLibrary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Serveur vers client : bibliotheque des themes et selections du serveur ({@link HoloThemeLibrary}), instantane
 * complet (a la connexion, puis apres chaque changement).
 */
public record HoloThemesPacket(List<HoloThemeEntry> entries, int standard, int staff)
{
    void encode(FriendlyByteBuf buf)
    {
        buf.writeVarInt(entries.size());
        for (HoloThemeEntry entry : entries) entry.write(buf);
        buf.writeVarInt(standard);
        buf.writeVarInt(staff);
    }

    static HoloThemesPacket decode(FriendlyByteBuf buf)
    {
        int count = buf.readVarInt();
        if (count < 0 || count > HoloThemeLibrary.MAX_THEMES) throw new IllegalArgumentException("Trop de themes");
        List<HoloThemeEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(HoloThemeEntry.read(buf));
        return new HoloThemesPacket(List.copyOf(entries), buf.readVarInt(), buf.readVarInt());
    }

    void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientThemes.receive(this));
    }
}
