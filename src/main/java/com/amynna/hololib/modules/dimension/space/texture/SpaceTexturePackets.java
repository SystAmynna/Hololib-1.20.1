package com.amynna.hololib.modules.dimension.space.texture;

import com.amynna.hololib.client.modules.dimension.space.SpaceTextureClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquets du canal des textures Space ({@link SpaceTextureNetwork}, T45). Empreintes ecrites en 20 octets bruts,
 * relues en 40 hexadecimaux minuscules ({@link SpaceTextureStore#writeSha1}) ; morceaux bornes a
 * {@link SpaceTextureNetwork#CHUNK_SIZE} des la lecture. Handlers serveur : {@link SpaceTextureServer} ; handlers
 * client : {@code SpaceTextureClient} via {@code DistExecutor}.
 */
public final class SpaceTexturePackets
{
    /** Etat d'un {@link Result} : le serveur attend les morceaux. */
    public static final int ACCEPT = 0;
    /** Etat d'un {@link Result} : texture stockee (ou deja presente) ; l'astre peut la recevoir. */
    public static final int DONE = 1;
    /** Etat d'un {@link Result} : import refuse ou abandonne (message). */
    public static final int REFUSED = 2;

    /** Longueur maximale d'un message de refus. */
    static final int MAX_MESSAGE = 256;

    private SpaceTexturePackets() {}

    /** Client -> serveur : debut d'un import (empreinte SHA-1 du PNG, taille en octets). */
    public record Begin(String sha1, int size)
    {
        void encode(FriendlyByteBuf buf)
        {
            SpaceTextureStore.writeSha1(buf, sha1);
            buf.writeVarInt(size);
        }

        static Begin decode(FriendlyByteBuf buf)
        {
            return new Begin(SpaceTextureStore.readSha1(buf), buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> context)
        {
            ServerPlayer player = context.get().getSender();
            if (player != null) SpaceTextureServer.onBegin(player, this);
        }
    }

    /** Client -> serveur : morceau de l'import en cours, a son decalage dans le PNG. */
    public record Chunk(int offset, byte[] bytes)
    {
        void encode(FriendlyByteBuf buf)
        {
            buf.writeVarInt(offset);
            buf.writeByteArray(bytes);
        }

        static Chunk decode(FriendlyByteBuf buf)
        {
            int offset = buf.readVarInt();
            return new Chunk(offset, buf.readByteArray(SpaceTextureNetwork.CHUNK_SIZE));
        }

        void handle(Supplier<NetworkEvent.Context> context)
        {
            ServerPlayer player = context.get().getSender();
            if (player != null) SpaceTextureServer.onChunk(player, this);
        }
    }

    /** Client -> serveur : demande de la texture importee {@code sha1} (inconnue du client). */
    public record Request(String sha1)
    {
        void encode(FriendlyByteBuf buf) { SpaceTextureStore.writeSha1(buf, sha1); }

        static Request decode(FriendlyByteBuf buf) { return new Request(SpaceTextureStore.readSha1(buf)); }

        void handle(Supplier<NetworkEvent.Context> context)
        {
            ServerPlayer player = context.get().getSender();
            if (player != null) SpaceTextureServer.onRequest(player, this);
        }
    }

    /** Serveur -> client : reponse a un import ({@link #ACCEPT}, {@link #DONE}, {@link #REFUSED} + message). */
    public record Result(String sha1, int status, String message)
    {
        void encode(FriendlyByteBuf buf)
        {
            SpaceTextureStore.writeSha1(buf, sha1);
            buf.writeByte(status);
            buf.writeUtf(message == null ? "" : message, MAX_MESSAGE);
        }

        static Result decode(FriendlyByteBuf buf)
        {
            String sha1 = SpaceTextureStore.readSha1(buf);
            int status = buf.readByte();
            return new Result(sha1, status, buf.readUtf(MAX_MESSAGE));
        }

        void handle(Supplier<NetworkEvent.Context> context)
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SpaceTextureClient.onResult(this));
        }
    }

    /** Serveur -> client : morceau de la texture {@code sha1} de {@code total} octets, a {@code offset}. */
    public record Data(String sha1, int total, int offset, byte[] bytes)
    {
        void encode(FriendlyByteBuf buf)
        {
            SpaceTextureStore.writeSha1(buf, sha1);
            buf.writeVarInt(total);
            buf.writeVarInt(offset);
            buf.writeByteArray(bytes);
        }

        static Data decode(FriendlyByteBuf buf)
        {
            String sha1 = SpaceTextureStore.readSha1(buf);
            int total = buf.readVarInt();
            int offset = buf.readVarInt();
            return new Data(sha1, total, offset, buf.readByteArray(SpaceTextureNetwork.CHUNK_SIZE));
        }

        void handle(Supplier<NetworkEvent.Context> context)
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SpaceTextureClient.onData(this));
        }
    }

    /** Serveur -> client : la texture demandee {@code sha1} n'est pas sur le serveur (ou illisible). */
    public record Missing(String sha1)
    {
        void encode(FriendlyByteBuf buf) { SpaceTextureStore.writeSha1(buf, sha1); }

        static Missing decode(FriendlyByteBuf buf) { return new Missing(SpaceTextureStore.readSha1(buf)); }

        void handle(Supplier<NetworkEvent.Context> context)
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SpaceTextureClient.onMissing(this));
        }
    }
}
