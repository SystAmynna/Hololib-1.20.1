package com.amynna.hololib.network;

import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client vers serveur : le proprietaire demande un nouveau placement (ancrage + transform) pour un objet de sa memoire
 * de joueur (refuse pour les autres portees).
 */
public record HoloMoveObjectPacket(HoloScope scope, UUID module, String key, HoloAnchor anchor,
                                   HoloTransform transform)
{
    private static final int MAX_KEY_LENGTH = 64;

    void encode(FriendlyByteBuf buf)
    {
        scope.write(buf);
        buf.writeUUID(module);
        buf.writeUtf(key, MAX_KEY_LENGTH);
        anchor.write(buf);
        transform.write(buf);
    }

    static HoloMoveObjectPacket decode(FriendlyByteBuf buf)
    {
        return new HoloMoveObjectPacket(HoloScope.read(buf), buf.readUUID(), buf.readUtf(MAX_KEY_LENGTH),
                HoloAnchor.read(buf), HoloTransform.read(buf));
    }

    void handle(Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player != null) HoloContainers.handleMove(player, this);
    }
}
