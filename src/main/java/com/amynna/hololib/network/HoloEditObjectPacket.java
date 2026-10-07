package com.amynna.hololib.network;

import com.amynna.hololib.holo.edit.HoloDisplay;
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
 * Client vers serveur : resultat d'une edition en jeu d'un objet de n'importe quelle portee (etat complet du
 * placement). Le serveur verifie le droit d'edition ({@code HoloEditRules}) et que seuls les elements permis par les
 * interfaces de l'objet changent ; sinon il renvoie son etat au client.
 *
 * @param openOffset nouvelle position relative d'un {@code HoloRelativeToOpen}, {@code null} = inchangee
 * @param editRange  portee d'edition (objet ancre {@code World})
 */
public record HoloEditObjectPacket(HoloScope scope, UUID module, String key, HoloAnchor anchor,
                                   HoloTransform transform, HoloDisplay display, HoloTransform openOffset,
                                   float editRange)
{
    private static final int MAX_KEY_LENGTH = 64;

    void encode(FriendlyByteBuf buf)
    {
        scope.write(buf);
        buf.writeUUID(module);
        buf.writeUtf(key, MAX_KEY_LENGTH);
        anchor.write(buf);
        transform.write(buf);
        buf.writeByte(display.ordinal());
        buf.writeBoolean(openOffset != null);
        if (openOffset != null) openOffset.write(buf);
        buf.writeFloat(editRange);
    }

    static HoloEditObjectPacket decode(FriendlyByteBuf buf)
    {
        return new HoloEditObjectPacket(HoloScope.read(buf), buf.readUUID(), buf.readUtf(MAX_KEY_LENGTH),
                HoloAnchor.read(buf), HoloTransform.read(buf), HoloDisplay.byOrdinal(buf.readByte()),
                buf.readBoolean() ? HoloTransform.read(buf) : null, buf.readFloat());
    }

    void handle(Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player != null) HoloContainers.handleEdit(player, this);
    }
}
