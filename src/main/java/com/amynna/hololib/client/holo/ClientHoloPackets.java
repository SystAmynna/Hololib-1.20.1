package com.amynna.hololib.client.holo;

import com.amynna.hololib.network.HoloForgetPacket;
import com.amynna.hololib.network.HoloModulePacket;
import com.amynna.hololib.network.HoloObjectPacket;
import com.amynna.hololib.network.HoloOriginPacket;
import com.amynna.hololib.network.HoloSnapshotPacket;
import com.amynna.hololib.network.HoloSyncData.ModuleData;

/** Handlers clients des paquets de {@code network}, appeles sur le fil client. */
public final class ClientHoloPackets
{
    private ClientHoloPackets() {}

    public static void snapshot(HoloSnapshotPacket packet)
    {
        ClientHoloContainer container = ClientHoloWorld.container(packet.scope());
        container.clear();
        if (packet.origin() != null) container.origin(packet.origin());
        for (ModuleData module : packet.modules()) container.add(module, packet.forOwner());
    }

    public static void module(HoloModulePacket packet)
    {
        switch (packet.op()) {
            case ADD -> ClientHoloWorld.container(packet.scope()).add(packet.module(), packet.forOwner());
            case UPDATE -> {
                ClientHoloContainer container = ClientHoloWorld.existing(packet.scope());
                if (container != null) container.update(packet.module());
            }
            case REMOVE -> {
                ClientHoloContainer container = ClientHoloWorld.existing(packet.scope());
                if (container != null) container.remove(packet.module().instanceId());
            }
        }
    }

    public static void object(HoloObjectPacket packet)
    {
        ClientHoloContainer container = ClientHoloWorld.existing(packet.scope());
        if (container != null) container.object(packet.module(), packet.object());
    }

    public static void origin(HoloOriginPacket packet)
    {
        ClientHoloContainer container = ClientHoloWorld.existing(packet.scope());
        if (container != null) container.origin(packet.origin());
    }

    public static void forget(HoloForgetPacket packet)
    {
        ClientHoloWorld.forget(packet.scope());
    }
}
