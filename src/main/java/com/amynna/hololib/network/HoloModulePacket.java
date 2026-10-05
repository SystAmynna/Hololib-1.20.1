package com.amynna.hololib.network;

import com.amynna.hololib.client.holo.ClientHoloPackets;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.network.HoloSyncData.ModuleData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Serveur vers client : ajout, mise a jour d'etat ou retrait d'un module d'une portee.
 *
 * @param module donnees du module ; pour {@link Op#REMOVE}, seul {@code instanceId} est utilise
 */
public record HoloModulePacket(HoloScope scope, boolean forOwner, Op op, ModuleData module)
{
    public enum Op
    {
        /** Nouveau module, avec ses objets. */
        ADD,
        /** Nouvel etat ({@code readSync} puis {@code onSync}). */
        UPDATE,
        REMOVE
    }

    private static final Op[] OPS = Op.values();

    public static HoloModulePacket remove(HoloScope scope, HoloModule module)
    {
        return new HoloModulePacket(scope, false, Op.REMOVE,
                new ModuleData(module.type().id(), module.instanceId(), new byte[0], List.of()));
    }

    void encode(FriendlyByteBuf buf)
    {
        scope.write(buf);
        buf.writeBoolean(forOwner);
        buf.writeByte(op.ordinal());
        module.write(buf);
    }

    static HoloModulePacket decode(FriendlyByteBuf buf)
    {
        HoloScope scope = HoloScope.read(buf);
        boolean forOwner = buf.readBoolean();
        int op = buf.readByte();
        if (op < 0 || op >= OPS.length) throw new IllegalArgumentException("Operation inconnue : " + op);
        return new HoloModulePacket(scope, forOwner, OPS[op], ModuleData.read(buf));
    }

    void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHoloPackets.module(this));
    }
}
