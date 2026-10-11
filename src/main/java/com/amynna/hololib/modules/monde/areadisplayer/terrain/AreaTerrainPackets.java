package com.amynna.hololib.modules.monde.areadisplayer.terrain;

import com.amynna.hololib.client.modules.monde.areadisplayer.AreaTerrainClient;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerModule;
import com.amynna.hololib.modules.monde.areadisplayer.AreaGrid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paquets du canal du terrain de l'Area Displayer ({@link AreaTerrainNetwork}). Tailles bornees des la lecture.
 * Handlers serveur : {@link AreaDisplayerModule} ; handlers client : {@code AreaTerrainClient} via
 * {@code DistExecutor}.
 */
public final class AreaTerrainPackets
{
    /** Sections au plus dans un paquet. */
    static final int MAX_SECTIONS = 1 << 16;
    /** Taille maximale d'une section deflatee (2 bits par voxel, plus la marge de deflate). */
    static final int MAX_SECTION_BYTES = AreaGrid.PACKED_BYTES + 256;
    /** Bornes de la taille d'une zone relue (le serveur envoie des zones valides). */
    private static final int MAX_BLOCKS = 60_000_000;

    /** Genre d'une position suivie : joueur. */
    public static final int KIND_PLAYER = 0;
    /** Genre : entite hostile ({@code Enemy}). */
    public static final int KIND_HOSTILE = 1;
    /** Genre : autre entite vivante. */
    public static final int KIND_OTHER = 2;

    private AreaTerrainPackets() {}

    /** Client -> serveur : le client a le miroir du Displayer du marker {@code marker} et demande son terrain. */
    public record Subscribe(String marker, UUID instance)
    {
        void encode(FriendlyByteBuf buf)
        {
            buf.writeUtf(marker, HoloScope.Position.MAX_ID_LENGTH);
            buf.writeUUID(instance);
        }

        static Subscribe decode(FriendlyByteBuf buf)
        {
            return new Subscribe(buf.readUtf(HoloScope.Position.MAX_ID_LENGTH), buf.readUUID());
        }

        void handle(Supplier<NetworkEvent.Context> context)
        {
            ServerPlayer player = context.get().getSender();
            if (player != null) AreaDisplayerModule.onSubscribe(player, marker, instance, true);
        }
    }

    /** Client -> serveur : miroir retire, plus rien a envoyer. */
    public record Unsubscribe(String marker, UUID instance)
    {
        void encode(FriendlyByteBuf buf)
        {
            buf.writeUtf(marker, HoloScope.Position.MAX_ID_LENGTH);
            buf.writeUUID(instance);
        }

        static Unsubscribe decode(FriendlyByteBuf buf)
        {
            return new Unsubscribe(buf.readUtf(HoloScope.Position.MAX_ID_LENGTH), buf.readUUID());
        }

        void handle(Supplier<NetworkEvent.Context> context)
        {
            ServerPlayer player = context.get().getSender();
            if (player != null) AreaDisplayerModule.onSubscribe(player, marker, instance, false);
        }
    }

    /**
     * Serveur -> client : nouvelle grille (abonnement, zone changee) ; le client repart de voxels vides.
     *
     * @param zone          faux : aucune zone definie (coordonnees ignorees)
     * @param requestedStep resolution demandee ({@code AreaGrid.AUTO} ou un palier)
     */
    public record Header(String marker, UUID instance, int generation, boolean zone, int minX, int minY, int minZ,
                         int blocksX, int blocksY, int blocksZ, int requestedStep)
    {
        void encode(FriendlyByteBuf buf)
        {
            buf.writeUtf(marker, HoloScope.Position.MAX_ID_LENGTH);
            buf.writeUUID(instance);
            buf.writeVarInt(generation);
            buf.writeBoolean(zone);
            if (!zone) return;
            buf.writeInt(minX);
            buf.writeInt(minY);
            buf.writeInt(minZ);
            buf.writeVarInt(blocksX);
            buf.writeVarInt(blocksY);
            buf.writeVarInt(blocksZ);
            buf.writeVarInt(requestedStep);
        }

        static Header decode(FriendlyByteBuf buf)
        {
            String marker = buf.readUtf(HoloScope.Position.MAX_ID_LENGTH);
            UUID instance = buf.readUUID();
            int generation = buf.readVarInt();
            if (!buf.readBoolean()) {
                return new Header(marker, instance, generation, false, 0, 0, 0, 1, 1, 1, AreaGrid.AUTO);
            }
            int minX = buf.readInt();
            int minY = buf.readInt();
            int minZ = buf.readInt();
            int blocksX = size(buf.readVarInt());
            int blocksY = size(buf.readVarInt());
            int blocksZ = size(buf.readVarInt());
            int step = buf.readVarInt();
            return new Header(marker, instance, generation, true, minX, minY, minZ, blocksX, blocksY, blocksZ,
                    AreaGrid.isLevel(step) ? step : AreaGrid.AUTO);
        }

        private static int size(int value)
        {
            if (value < 1 || value > MAX_BLOCKS) throw new IllegalArgumentException("Taille de zone invalide");
            return value;
        }

        void handle(Supplier<NetworkEvent.Context> context)
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AreaTerrainClient.onHeader(this));
        }
    }

    /**
     * Serveur -> client : sections changees de la grille {@code generation}.
     *
     * @param uniform valeur commune de chaque section, ou {@code -1} si ses octets sont dans {@code data}
     * @param data    octets deflates de la section empaquetee, {@code null} pour une section uniforme
     */
    public record Sections(String marker, int generation, int[] indices, int[] uniform, byte[][] data)
    {
        void encode(FriendlyByteBuf buf)
        {
            buf.writeUtf(marker, HoloScope.Position.MAX_ID_LENGTH);
            buf.writeVarInt(generation);
            buf.writeVarInt(indices.length);
            for (int i = 0; i < indices.length; i++) {
                buf.writeVarInt(indices[i]);
                buf.writeByte(uniform[i]);
                if (uniform[i] < 0) buf.writeByteArray(data[i]);
            }
        }

        static Sections decode(FriendlyByteBuf buf)
        {
            String marker = buf.readUtf(HoloScope.Position.MAX_ID_LENGTH);
            int generation = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_SECTIONS) throw new IllegalArgumentException("Trop de sections");
            int[] indices = new int[count];
            int[] uniform = new int[count];
            byte[][] data = new byte[count][];
            for (int i = 0; i < count; i++) {
                indices[i] = buf.readVarInt();
                uniform[i] = buf.readByte();
                if (uniform[i] < 0) data[i] = buf.readByteArray(MAX_SECTION_BYTES);
            }
            return new Sections(marker, generation, indices, uniform, data);
        }

        void handle(Supplier<NetworkEvent.Context> context)
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AreaTerrainClient.onSections(this));
        }
    }

    /**
     * Serveur -> client : joueurs et entites suivis de la zone (liste complete : un id absent a quitte la zone ou
     * n'est plus montre).
     *
     * @param kinds {@link #KIND_PLAYER}, {@link #KIND_HOSTILE} ou {@link #KIND_OTHER}
     * @param xyz   positions relatives au coin minimal de la zone, en blocs (trois par entree)
     * @param names nom de chaque entree, {@code null} s'il est deja connu du client
     */
    public record Tracks(String marker, int generation, int[] ids, byte[] kinds, float[] xyz, String[] names)
    {
        void encode(FriendlyByteBuf buf)
        {
            buf.writeUtf(marker, HoloScope.Position.MAX_ID_LENGTH);
            buf.writeVarInt(generation);
            buf.writeVarInt(ids.length);
            for (int i = 0; i < ids.length; i++) {
                buf.writeVarInt(ids[i]);
                buf.writeByte(kinds[i]);
                buf.writeFloat(xyz[3 * i]);
                buf.writeFloat(xyz[3 * i + 1]);
                buf.writeFloat(xyz[3 * i + 2]);
                buf.writeBoolean(names[i] != null);
                if (names[i] != null) buf.writeUtf(names[i], AreaTerrainNetwork.MAX_NAME);
            }
        }

        static Tracks decode(FriendlyByteBuf buf)
        {
            String marker = buf.readUtf(HoloScope.Position.MAX_ID_LENGTH);
            int generation = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > AreaTerrainNetwork.MAX_TRACKS) throw new IllegalArgumentException("Trop d'entrees");
            int[] ids = new int[count];
            byte[] kinds = new byte[count];
            float[] xyz = new float[3 * count];
            String[] names = new String[count];
            for (int i = 0; i < count; i++) {
                ids[i] = buf.readVarInt();
                kinds[i] = buf.readByte();
                xyz[3 * i] = buf.readFloat();
                xyz[3 * i + 1] = buf.readFloat();
                xyz[3 * i + 2] = buf.readFloat();
                if (buf.readBoolean()) names[i] = buf.readUtf(AreaTerrainNetwork.MAX_NAME);
            }
            return new Tracks(marker, generation, ids, kinds, xyz, names);
        }

        void handle(Supplier<NetworkEvent.Context> context)
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AreaTerrainClient.onTracks(this));
        }
    }
}
