package com.amynna.hololib.network;

import com.amynna.hololib.client.holo.ClientHoloPackets;
import com.amynna.hololib.holo.memory.HoloScope;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Serveur vers client : nouvelle origine des objets ancres {@code World} d'une portee, quand son hote bouge (marker
 * d'une memoire de position). L'origine initiale arrive avec le snapshot.
 */
public record HoloOriginPacket(HoloScope scope, Vec3 origin)
{
    void encode(FriendlyByteBuf buf)
    {
        scope.write(buf);
        writeVec(buf, origin);
    }

    static HoloOriginPacket decode(FriendlyByteBuf buf)
    {
        return new HoloOriginPacket(HoloScope.read(buf), readVec(buf));
    }

    void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHoloPackets.origin(this));
    }

    static void writeVec(FriendlyByteBuf buf, Vec3 vec)
    {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    static Vec3 readVec(FriendlyByteBuf buf)
    {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
