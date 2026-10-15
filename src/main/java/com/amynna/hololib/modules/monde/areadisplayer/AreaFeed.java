package com.amynna.hololib.modules.monde.areadisplayer;

import com.amynna.hololib.modules.monde.areadisplayer.terrain.AreaTerrainNetwork;
import com.amynna.hololib.modules.monde.areadisplayer.terrain.AreaTerrainPackets;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
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
 * Terrain : a l'abonnement ou au changement de zone, un {@code Header} puis toutes les sections non vides (cle de
 * section, voir {@link AreaGrid#sectionKey}) ; ensuite seules les sections changees. Sections empaquetees et
 * deflatees une fois (cache par cle), envoyees dans l'ordre du scan et dans la limite de
 * {@link AreaTerrainNetwork#BYTES_PER_TICK} octets par tick et par abonne. Une section devenue vide est envoyee comme
 * uniforme vide.
 * <p>
 * Positions suivies : toutes les {@value #TRACK_TICKS} ticks, si une option l'exige : joueurs de la zone (hors
 * spectateurs), puis entites vivantes de la zone hors joueurs et porte-armures (les block entities ne sont pas des
 * entites), {@link AreaTerrainNetwork#MAX_TRACKS} au plus ; le nom d'une entree n'est envoye qu'a l'abonne qui ne le
 * connait pas encore ; l'abonne oublie les noms des entrees absentes d'un paquet (le client retire alors la
 * piste), pour les renvoyer si elle revient.
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
        final LongLinkedOpenHashSet pending = new LongLinkedOpenHashSet();
        final Int2ObjectOpenHashMap<String> names = new Int2ObjectOpenHashMap<>();
        /** Ids du dernier paquet de positions (reutilise : sert a oublier les noms des pistes disparues). */
        final IntOpenHashSet present = new IntOpenHashSet();
    }

    private final AreaDisplayerModule module;
    private final Map<UUID, Subscriber> subscribers = new LinkedHashMap<>();

    /** Section empaquetee : valeur uniforme ({@code -1} : octets deflates dans {@code data}). */
    private record Packed(int uniform, byte[] data) {}

    private AreaGrid grid;
    private AreaVoxels voxels;
    private int generation;
    /** Cache des sections empaquetees, par cle de section. */
    private final Long2ObjectOpenHashMap<Packed> cache = new Long2ObjectOpenHashMap<>();
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
    void reset(AreaGrid grid, AreaVoxels voxels, int generation)
    {
        this.grid = grid;
        this.voxels = voxels;
        this.generation = generation;
        cache.clear();
        for (Subscriber subscriber : subscribers.values()) {
            subscriber.header = true;
            subscriber.pending.clear();
            subscriber.names.clear();
        }
    }

    /** Section changee : cache invalide, envoyee a tous les abonnes. */
    void sectionChanged(long key)
    {
        cache.remove(key);
        for (Subscriber subscriber : subscribers.values()) {
            if (!subscriber.header) subscriber.pending.add(key);
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
                    false, 0, 0, 0, 1, 1, 1, AreaGrid.DEFAULT_STEP));
            return;
        }
        AreaTerrainNetwork.send(player, new AreaTerrainPackets.Header(marker, module.instanceId(), generation, true,
                grid.minX(), grid.minY(), grid.minZ(), grid.blocksX(), grid.blocksY(), grid.blocksZ(),
                grid.step()));
        subscriber.pending.clear();
        subscriber.pending.addAll(voxels.keys());
    }

    private void sendSections(ServerPlayer player, Subscriber subscriber)
    {
        if (grid == null) {
            subscriber.pending.clear();
            return;
        }
        LongArrayList keys = new LongArrayList();
        int bytes = 0;
        for (LongIterator it = subscriber.pending.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            Packed packed = ensurePacked(key);
            int size = packed.uniform >= 0 ? 12 : packed.data.length + 12;
            if (!keys.isEmpty() && bytes + size > AreaTerrainNetwork.BYTES_PER_TICK) break;
            keys.add(key);
            bytes += size;
        }
        int n = keys.size();
        int[] values = new int[n];
        byte[][] data = new byte[n][];
        for (int i = 0; i < n; i++) {
            Packed packed = cache.get(keys.getLong(i));
            values[i] = packed.uniform;
            data[i] = packed.data;
            subscriber.pending.remove(keys.getLong(i));
        }
        AreaTerrainNetwork.send(player, new AreaTerrainPackets.Sections(module.markerId(), generation,
                keys.toLongArray(), values, data));
    }

    /** Section empaquetee et deflatee (cache). */
    private Packed ensurePacked(long key)
    {
        Packed cached = cache.get(key);
        if (cached != null) return cached;
        int value = AreaGrid.pack(voxels.section(key), packed);
        Packed result;
        if (value >= 0) {
            result = new Packed(value, null);
        } else {
            deflater.reset();
            deflater.setInput(packed);
            deflater.finish();
            int length = 0;
            while (!deflater.finished() && length < deflateBuffer.length) {
                length += deflater.deflate(deflateBuffer, length, deflateBuffer.length - length);
            }
            result = new Packed(-1, Arrays.copyOf(deflateBuffer, length));
        }
        cache.put(key, result);
        return result;
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
        // Le client retire une piste absente d'un paquet : son nom doit etre renvoye si elle revient.
        subscriber.present.clear();
        for (int i = 0; i < n; i++) subscriber.present.add(ids[i]);
        subscriber.names.keySet().removeIf(id -> !subscriber.present.contains(id));
        AreaTerrainNetwork.send(player, new AreaTerrainPackets.Tracks(module.markerId(), generation, ids, kinds, xyz,
                names));
    }

    private void sendEmptyTracks(ServerPlayer player)
    {
        AreaTerrainNetwork.send(player, new AreaTerrainPackets.Tracks(module.markerId(), generation, new int[0],
                new byte[0], new float[0], new String[0]));
    }
}
