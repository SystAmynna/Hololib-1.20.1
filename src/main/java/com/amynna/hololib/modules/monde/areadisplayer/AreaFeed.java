package com.amynna.hololib.modules.monde.areadisplayer;

import com.amynna.hololib.modules.monde.areadisplayer.terrain.AreaTerrainNetwork;
import com.amynna.hololib.modules.monde.areadisplayer.terrain.AreaTerrainPackets;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.Deflater;

/**
 * Envois du terrain et des positions suivies d'un Area Displayer (serveur) aux clients abonnes, par le canal dedie
 * {@link AreaTerrainNetwork}. Un abonne doit suivre le chunk du marker (il a alors le miroir du module) ; verifie a
 * l'abonnement puis toutes les {@value #PURGE_TICKS} ticks (deconnecte, autre dimension, hors de portee : oublie).
 * <p>
 * Terrain : a l'abonnement ou au changement de zone, un {@code Header} puis toutes les sections non vides ; ensuite
 * seules les sections changees. Sections empaquetees et deflatees une fois (cache par section), envoyees dans la
 * limite de {@link AreaTerrainNetwork#BYTES_PER_TICK} octets par tick et par abonne.
 * <p>
 * Positions suivies : toutes les {@value #TRACK_TICKS} ticks, si une option l'exige : joueurs de la zone (hors
 * spectateurs), puis entites vivantes de la zone hors joueurs et porte-armures (les block entities ne sont pas des
 * entites), {@link AreaTerrainNetwork#MAX_TRACKS} au plus ; le nom d'une entree n'est envoye qu'a l'abonne qui ne le
 * connait pas encore.
 */
final class AreaFeed
{
    /** Ticks entre deux verifications des abonnes. */
    private static final int PURGE_TICKS = 20;
    /** Ticks entre deux envois des positions suivies. */
    private static final int TRACK_TICKS = 2;

    private static final class Subscriber
    {
        boolean header = true;
        final BitSet pending = new BitSet();
        final Int2ObjectOpenHashMap<String> names = new Int2ObjectOpenHashMap<>();
    }

    private final AreaDisplayerModule module;
    private final Map<UUID, Subscriber> subscribers = new LinkedHashMap<>();

    private AreaGrid grid;
    private byte[] voxels;
    private int generation;
    private final BitSet nonEmpty = new BitSet();
    /** Cache des sections empaquetees : valeur uniforme ({@code -1} : octets dans {@link #compressed}), -2 = a faire. */
    private int[] uniform = new int[0];
    private byte[][] compressed = new byte[0][];
    private final byte[] packed = new byte[AreaGrid.PACKED_BYTES];
    private final byte[] deflateBuffer = new byte[AreaGrid.PACKED_BYTES + 512];
    private final Deflater deflater = new Deflater(Deflater.BEST_SPEED);

    // Positions suivies du tick courant (reutilisees).
    private int trackCount;
    private int[] trackIds = new int[16];
    private byte[] trackKinds = new byte[16];
    private float[] trackXyz = new float[48];
    private String[] trackNames = new String[16];

    AreaFeed(AreaDisplayerModule module) { this.module = module; }

    boolean hasSubscribers() { return !subscribers.isEmpty(); }

    /** Nouvelle grille (ou aucune) : chaque abonne recoit un {@code Header} puis les sections non vides. */
    void reset(AreaGrid grid, byte[] voxels, int generation)
    {
        this.grid = grid;
        this.voxels = voxels;
        this.generation = generation;
        int sections = grid != null ? grid.sections() : 0;
        uniform = new int[sections];
        Arrays.fill(uniform, -2);
        compressed = new byte[sections][];
        nonEmpty.clear();
        for (int s = 0; s < sections; s++) if (!grid.sectionEmpty(voxels, s)) nonEmpty.set(s);
        for (Subscriber subscriber : subscribers.values()) {
            subscriber.header = true;
            subscriber.pending.clear();
            subscriber.names.clear();
        }
    }

    /** Section changee : cache invalide, envoyee a tous les abonnes. */
    void sectionChanged(int section)
    {
        uniform[section] = -2;
        compressed[section] = null;
        nonEmpty.set(section, !grid.sectionEmpty(voxels, section));
        for (Subscriber subscriber : subscribers.values()) {
            if (!subscriber.header) subscriber.pending.set(section);
        }
    }

    void subscribe(ServerPlayer player) { subscribers.put(player.getUUID(), new Subscriber()); }

    void unsubscribe(UUID player) { subscribers.remove(player); }

    void clear() { subscribers.clear(); }

    /** Vrai si {@code player} suit le chunk du marker dans le niveau {@code level}. */
    static boolean tracks(ServerPlayer player, ServerLevel level, ChunkPos chunk)
    {
        return player.level() == level
                && level.getChunkSource().chunkMap.getPlayers(chunk, false).contains(player);
    }

    /** Verifications, puis envois du tick. */
    void tick(ServerLevel level, ChunkPos chunk, int tick, boolean players, boolean entities)
    {
        if (subscribers.isEmpty()) return;
        MinecraftServer server = level.getServer();
        boolean purge = tick % PURGE_TICKS == 0;
        boolean track = tick % TRACK_TICKS == 0 && grid != null && (players || entities);
        if (track) collectTracks(level, players, entities);
        Iterator<Map.Entry<UUID, Subscriber>> iterator = subscribers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Subscriber> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || purge && (chunk == null || !tracks(player, level, chunk))) {
                iterator.remove();
                continue;
            }
            Subscriber subscriber = entry.getValue();
            if (subscriber.header) sendHeader(player, subscriber);
            if (!subscriber.pending.isEmpty()) sendSections(player, subscriber);
            if (track) sendTracks(player, subscriber);
            else if (tick % TRACK_TICKS == 0 && !subscriber.names.isEmpty() && !players && !entities) {
                // Options coupees : une liste vide efface les points du client, une seule fois.
                subscriber.names.clear();
                sendEmptyTracks(player);
            }
        }
    }

    private void sendHeader(ServerPlayer player, Subscriber subscriber)
    {
        subscriber.header = false;
        subscriber.names.clear();
        String marker = module.markerId();
        if (grid == null) {
            AreaTerrainNetwork.send(player, new AreaTerrainPackets.Header(marker, module.instanceId(), generation,
                    false, 0, 0, 0, 1, 1, 1, AreaGrid.AUTO));
            return;
        }
        AreaTerrainNetwork.send(player, new AreaTerrainPackets.Header(marker, module.instanceId(), generation, true,
                grid.minX(), grid.minY(), grid.minZ(), grid.blocksX(), grid.blocksY(), grid.blocksZ(),
                grid.requestedStep()));
        subscriber.pending.clear();
        subscriber.pending.or(nonEmpty);
    }

    private void sendSections(ServerPlayer player, Subscriber subscriber)
    {
        if (grid == null) {
            subscriber.pending.clear();
            return;
        }
        List<Integer> indices = new ArrayList<>();
        int bytes = 0;
        for (int s = subscriber.pending.nextSetBit(0); s >= 0; s = subscriber.pending.nextSetBit(s + 1)) {
            ensurePacked(s);
            int size = uniform[s] >= 0 ? 4 : compressed[s].length + 6;
            if (!indices.isEmpty() && bytes + size > AreaTerrainNetwork.BYTES_PER_TICK) break;
            indices.add(s);
            bytes += size;
        }
        int n = indices.size();
        int[] ids = new int[n];
        int[] values = new int[n];
        byte[][] data = new byte[n][];
        for (int i = 0; i < n; i++) {
            int s = indices.get(i);
            ids[i] = s;
            values[i] = uniform[s];
            data[i] = compressed[s];
            subscriber.pending.clear(s);
        }
        AreaTerrainNetwork.send(player, new AreaTerrainPackets.Sections(module.markerId(), generation, ids, values,
                data));
    }

    /** Empaquette et deflate la section si son cache est vide. */
    private void ensurePacked(int section)
    {
        if (uniform[section] != -2) return;
        int value = grid.pack(voxels, section, packed);
        uniform[section] = value;
        if (value >= 0) return;
        deflater.reset();
        deflater.setInput(packed);
        deflater.finish();
        int length = 0;
        while (!deflater.finished() && length < deflateBuffer.length) {
            length += deflater.deflate(deflateBuffer, length, deflateBuffer.length - length);
        }
        compressed[section] = Arrays.copyOf(deflateBuffer, length);
    }

    private void collectTracks(ServerLevel level, boolean players, boolean entities)
    {
        trackCount = 0;
        AABB box = new AABB(grid.minX(), grid.minY(), grid.minZ(), grid.minX() + (double) grid.blocksX(),
                grid.minY() + (double) grid.blocksY(), grid.minZ() + (double) grid.blocksZ());
        if (players) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || !box.contains(player.getX(), player.getY(), player.getZ())) continue;
                addTrack(player, AreaTerrainPackets.KIND_PLAYER, player.getGameProfile().getName());
            }
        }
        if (entities) {
            List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive() && !(e instanceof Player) && !(e instanceof ArmorStand));
            for (LivingEntity entity : found) {
                addTrack(entity, entity instanceof Enemy ? AreaTerrainPackets.KIND_HOSTILE
                        : AreaTerrainPackets.KIND_OTHER, entity.getDisplayName().getString());
            }
        }
    }

    private void addTrack(LivingEntity entity, int kind, String name)
    {
        if (trackCount >= AreaTerrainNetwork.MAX_TRACKS) return;
        if (trackCount == trackIds.length) {
            int size = Math.min(AreaTerrainNetwork.MAX_TRACKS, trackCount * 2);
            trackIds = Arrays.copyOf(trackIds, size);
            trackKinds = Arrays.copyOf(trackKinds, size);
            trackXyz = Arrays.copyOf(trackXyz, 3 * size);
            trackNames = Arrays.copyOf(trackNames, size);
        }
        int i = trackCount++;
        trackIds[i] = entity.getId();
        trackKinds[i] = (byte) kind;
        trackXyz[3 * i] = (float) (entity.getX() - grid.minX());
        trackXyz[3 * i + 1] = (float) (entity.getY() + entity.getBbHeight() * 0.5 - grid.minY());
        trackXyz[3 * i + 2] = (float) (entity.getZ() - grid.minZ());
        trackNames[i] = name.length() > AreaTerrainNetwork.MAX_NAME
                ? name.substring(0, AreaTerrainNetwork.MAX_NAME) : name;
    }

    private void sendTracks(ServerPlayer player, Subscriber subscriber)
    {
        int n = trackCount;
        int[] ids = Arrays.copyOf(trackIds, n);
        byte[] kinds = Arrays.copyOf(trackKinds, n);
        float[] xyz = Arrays.copyOf(trackXyz, 3 * n);
        String[] names = new String[n];
        for (int i = 0; i < n; i++) {
            String name = trackNames[i];
            if (!name.equals(subscriber.names.get(ids[i]))) {
                subscriber.names.put(ids[i], name);
                names[i] = name;
            }
        }
        if (subscriber.names.size() > 4 * AreaTerrainNetwork.MAX_TRACKS) {
            // Noms d'entites disparues : on repart de zero (renvoyes au prochain envoi).
            subscriber.names.clear();
        }
        AreaTerrainNetwork.send(player, new AreaTerrainPackets.Tracks(module.markerId(), generation, ids, kinds, xyz,
                names));
    }

    private void sendEmptyTracks(ServerPlayer player)
    {
        AreaTerrainNetwork.send(player, new AreaTerrainPackets.Tracks(module.markerId(), generation, new int[0],
                new byte[0], new float[0], new String[0]));
    }
}
