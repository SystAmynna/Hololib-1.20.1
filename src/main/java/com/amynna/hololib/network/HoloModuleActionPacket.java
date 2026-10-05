package com.amynna.hololib.network;

import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.HoloScope;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client vers serveur : action vers {@code HoloModule.handleAction} d'un module d'une portee.
 *
 * @param source cle de l'objet au nom duquel l'action est envoyee, ou {@code null}
 */
public record HoloModuleActionPacket(HoloScope scope, UUID module, String source, byte[] payload)
{
    /** Taille maximale du contenu d'une action, en octets. */
    public static final int MAX_PAYLOAD = 32767;

    void encode(FriendlyByteBuf buf)
    {
        scope.write(buf);
        buf.writeUUID(module);
        buf.writeNullable(source, FriendlyByteBuf::writeUtf);
        buf.writeByteArray(payload);
    }

    static HoloModuleActionPacket decode(FriendlyByteBuf buf)
    {
        return new HoloModuleActionPacket(HoloScope.read(buf), buf.readUUID(), buf.readNullable(FriendlyByteBuf::readUtf),
                buf.readByteArray(MAX_PAYLOAD));
    }

    void handle(Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player != null) HoloContainers.handleAction(player, this);
    }
}
