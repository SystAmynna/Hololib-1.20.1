package com.amynna.hololib.network;

import com.amynna.hololib.HoloLib;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal reseau du mod. Les paquets serveur vers client portent la {@code HoloScope} concernee : les memes paquets
 * servent au proprietaire et aux observateurs, pour les memoires de joueur, de dimension et de position. Les handlers
 * clients sont delegues a {@code client.holo.ClientHoloPackets} via {@code DistExecutor} : aucune classe client n'est
 * chargee sur un serveur dedie.
 */
public final class HoloNetwork
{
    private static final String PROTOCOL = "6";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals,
            PROTOCOL::equals);

    private HoloNetwork() {}

    /** A appeler une fois, au setup commun. */
    public static void register()
    {
        int id = 0;
        CHANNEL.messageBuilder(HoloSnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HoloSnapshotPacket::encode).decoder(HoloSnapshotPacket::decode)
                .consumerMainThread(HoloSnapshotPacket::handle).add();
        CHANNEL.messageBuilder(HoloModulePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HoloModulePacket::encode).decoder(HoloModulePacket::decode)
                .consumerMainThread(HoloModulePacket::handle).add();
        CHANNEL.messageBuilder(HoloObjectPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HoloObjectPacket::encode).decoder(HoloObjectPacket::decode)
                .consumerMainThread(HoloObjectPacket::handle).add();
        CHANNEL.messageBuilder(HoloForgetPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HoloForgetPacket::encode).decoder(HoloForgetPacket::decode)
                .consumerMainThread(HoloForgetPacket::handle).add();
        CHANNEL.messageBuilder(HoloOriginPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HoloOriginPacket::encode).decoder(HoloOriginPacket::decode)
                .consumerMainThread(HoloOriginPacket::handle).add();
        CHANNEL.messageBuilder(HoloMoveObjectPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HoloMoveObjectPacket::encode).decoder(HoloMoveObjectPacket::decode)
                .consumerMainThread(HoloMoveObjectPacket::handle).add();
        CHANNEL.messageBuilder(HoloModuleActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HoloModuleActionPacket::encode).decoder(HoloModuleActionPacket::decode)
                .consumerMainThread(HoloModuleActionPacket::handle).add();
        CHANNEL.messageBuilder(HoloEditObjectPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HoloEditObjectPacket::encode).decoder(HoloEditObjectPacket::decode)
                .consumerMainThread(HoloEditObjectPacket::handle).add();
    }

    public static void send(ServerPlayer player, Object packet)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Envoie a tous les joueurs qui suivent {@code entity} (pas a l'entite elle-meme). */
    public static void sendToTracking(Entity entity, Object packet)
    {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }

    /** Envoie a tous les joueurs qui suivent le chunk. */
    public static void sendToTracking(LevelChunk chunk, Object packet)
    {
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), packet);
    }

    /** Envoie a tous les joueurs de la dimension. */
    public static void sendToDimension(ResourceKey<Level> dimension, Object packet)
    {
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dimension), packet);
    }
}
