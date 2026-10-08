package com.amynna.hololib.network;

import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.HoloScope;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client vers serveur : le proprietaire ouvre ou ferme un objet de sa memoire de joueur ({@code HoloObject.opened()},
 * refuse pour les autres portees). Le serveur le transmet aux observateurs.
 */
public record HoloOpenObjectPacket(HoloScope scope, UUID module, String key, boolean open)
{
    private static final int MAX_KEY_LENGTH = 64;

    void encode(FriendlyByteBuf buf)
    {
        scope.write(buf);
        buf.writeUUID(module);
        buf.writeUtf(key, MAX_KEY_LENGTH);
        buf.writeBoolean(open);
    }

    static HoloOpenObjectPacket decode(FriendlyByteBuf buf)
    {
        return new HoloOpenObjectPacket(HoloScope.read(buf), buf.readUUID(), buf.readUtf(MAX_KEY_LENGTH),
                buf.readBoolean());
    }

    void handle(Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player != null) HoloContainers.handleOpen(player, this);
    }
}
