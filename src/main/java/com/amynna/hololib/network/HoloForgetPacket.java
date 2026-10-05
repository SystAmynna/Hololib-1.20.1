package com.amynna.hololib.network;

import com.amynna.hololib.client.holo.ClientHoloPackets;
import com.amynna.hololib.holo.memory.HoloScope;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serveur vers client : oublier les modules d'une portee (joueur plus suivi, chunk plus suivi, memoire retiree). */
public record HoloForgetPacket(HoloScope scope)
{
    void encode(FriendlyByteBuf buf)
    {
        scope.write(buf);
    }

    static HoloForgetPacket decode(FriendlyByteBuf buf)
    {
        return new HoloForgetPacket(HoloScope.read(buf));
    }

    void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHoloPackets.forget(this));
    }
}
