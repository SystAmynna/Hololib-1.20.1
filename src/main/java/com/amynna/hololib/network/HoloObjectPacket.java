package com.amynna.hololib.network;

import com.amynna.hololib.client.holo.ClientHoloPackets;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.network.HoloSyncData.ObjectData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Serveur vers client : nouvel ancrage et/ou nouvelle visibilite d'un objet. */
public record HoloObjectPacket(HoloScope scope, UUID module, ObjectData object)
{
    void encode(FriendlyByteBuf buf)
    {
        scope.write(buf);
        buf.writeUUID(module);
        object.write(buf);
    }

    static HoloObjectPacket decode(FriendlyByteBuf buf)
    {
        return new HoloObjectPacket(HoloScope.read(buf), buf.readUUID(), ObjectData.read(buf));
    }

    void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHoloPackets.object(this));
    }
}
