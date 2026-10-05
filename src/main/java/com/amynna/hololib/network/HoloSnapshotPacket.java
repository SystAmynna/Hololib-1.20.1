package com.amynna.hololib.network;

import com.amynna.hololib.client.holo.ClientHoloPackets;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.network.HoloSyncData.ModuleData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Serveur vers client : etat complet des modules d'une portee (modules partages seulement pour un observateur), qui
 * remplace tout ce que le client en connaissait.
 *
 * @param forOwner vrai si le destinataire est le proprietaire (etat complet), faux pour un observateur
 * @param origin   origine des ancrages {@code World} de la portee (marker), {@code null} si le client la connait
 */
public record HoloSnapshotPacket(HoloScope scope, boolean forOwner, List<ModuleData> modules, Vec3 origin)
{
    void encode(FriendlyByteBuf buf)
    {
        scope.write(buf);
        buf.writeBoolean(forOwner);
        buf.writeVarInt(modules.size());
        for (ModuleData module : modules) module.write(buf);
        buf.writeBoolean(origin != null);
        if (origin != null) HoloOriginPacket.writeVec(buf, origin);
    }

    static HoloSnapshotPacket decode(FriendlyByteBuf buf)
    {
        HoloScope scope = HoloScope.read(buf);
        boolean forOwner = buf.readBoolean();
        int count = buf.readVarInt();
        List<ModuleData> modules = new ArrayList<>(count);
        for (int i = 0; i < count; i++) modules.add(ModuleData.read(buf));
        Vec3 origin = buf.readBoolean() ? HoloOriginPacket.readVec(buf) : null;
        return new HoloSnapshotPacket(scope, forOwner, modules, origin);
    }

    void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHoloPackets.snapshot(this));
    }
}
