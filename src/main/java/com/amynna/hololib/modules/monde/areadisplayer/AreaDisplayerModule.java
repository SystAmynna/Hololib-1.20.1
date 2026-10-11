package com.amynna.hololib.modules.monde.areadisplayer;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.memory.position.PositionHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.module.HoloModuleHost;
import com.amynna.hololib.holo.object.HoloTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Module {@code hololib:area_displayer} (memoire de position, marker) : un pave droit holographique
 * ({@link AreaDisplayerCube}) montre en monochrome le terrain de la zone comprise entre deux coins A et B, ainsi que
 * les joueurs et les entites vivantes qui s'y trouvent (points, trainees, nom au regard). Visible par tous.
 * <p>
 * <b>Etat</b> (sauvegarde et synchronise) : coins A et B (zone definie si les deux le sont, hauteur bornee a celle du
 * monde), options ({@link #FLAG_FRAME} cadre, {@link #FLAG_AUTO_UPDATE}, {@link #FLAG_PLAYERS},
 * {@link #FLAG_PLAYER_TRAILS}, {@link #FLAG_ENTITIES}, {@link #FLAG_ENTITY_TRAILS}), intervalle de l'Auto Update,
 * generation de la grille (changee avec la zone) et avancement du scan. Les reglages ne sont modifies que par l'API
 * serveur publique ({@link #setCorner}, {@link #setFlag}, {@link #setInterval}, {@link #setResolution},
 * {@link #refresh}), appelee par l'Area
 * Displayer Configurator du meme marker (operateurs) ; le module n'a aucune action.
 * <p>
 * <b>Terrain</b> (serveur) : grille de voxels ({@link AreaGrid}, resolution Auto ou palier choisi, relevee si la zone
 * est trop grande, taille de zone illimitee)
 * calculee par {@link AreaScanner} avec un budget de {@value #SCAN_BUDGET_NANOS} ns par tick : passage complet a la
 * definition de la zone et sur « Actualiser » ; <b>Auto Update</b> = nouveau passage toutes les {@code interval}
 * secondes sur les seuls chunks charges (jamais en temps reel : aucune ecoute des blocs). Sans Auto Update, le
 * terrain reste fige jusqu'a « Actualiser », y compris apres un redemarrage : les voxels sont sauvegardes (deflates)
 * avec l'etat du module. Le terrain et les positions suivies partent par le canal dedie
 * ({@code terrain.AreaTerrainNetwork}) aux seuls clients abonnes ({@link AreaFeed}) ; l'etat synchronise du module
 * reste petit.
 * <p>
 * <b>Etat synchronise</b> ({@link #writeSync}) :
 * <pre>
 * boolean A [+ 3 int], boolean B [+ 3 int], byte options, varint intervalle (s), varint resolution demandee
 * (0 = Auto), varint generation,
 * byte avancement (0-100, 255 = aucun scan)
 * </pre>
 * Cote client, le miroir s'abonne au terrain a sa creation et se desabonne a son retrait (par le {@link Backend}).
 */
public final class AreaDisplayerModule extends HoloModule
{
    /** Option : cadre du pave. */
    public static final int FLAG_FRAME = 0;
    /** Option : Auto Update (rescan periodique des chunks charges). */
    public static final int FLAG_AUTO_UPDATE = 1;
    /** Option : joueurs de la zone. */
    public static final int FLAG_PLAYERS = 2;
    /** Option : trainees des joueurs. */
    public static final int FLAG_PLAYER_TRAILS = 3;
    /** Option : entites de la zone. */
    public static final int FLAG_ENTITIES = 4;
    /** Option : trainees des entites. */
    public static final int FLAG_ENTITY_TRAILS = 5;
    /** Nombre d'options. */
    public static final int FLAG_COUNT = 6;
    /** Options par defaut : cadre, joueurs et leurs trainees, entites. */
    private static final int DEFAULT_FLAGS = 1 << FLAG_FRAME | 1 << FLAG_PLAYERS | 1 << FLAG_PLAYER_TRAILS
            | 1 << FLAG_ENTITIES;

    /** Bornes et defaut de l'intervalle de l'Auto Update, en secondes. */
    public static final int MIN_INTERVAL = 10;
    public static final int MAX_INTERVAL = 600;
    public static final int DEFAULT_INTERVAL = 60;
    /** Borne horizontale des coins (bord du monde). */
    public static final int MAX_HORIZONTAL = 30_000_000;
    /** Avancement synchronise quand aucun scan n'est en cours. */
    public static final int NO_SCAN = 255;

    /** Budget de temps du scan par tick serveur. */
    private static final long SCAN_BUDGET_NANOS = 3_000_000L;
    /** Ticks au moins entre deux envois de l'avancement. */
    private static final int PROGRESS_SYNC_TICKS = 10;
    /** Ecart relatif tolere entre les echelles du pave avant de les uniformiser. */
    private static final float UNIFORM_EPSILON = 1e-4f;

    private static final Map<HoloScope, AreaDisplayerModule> SERVER_DISPLAYERS = new ConcurrentHashMap<>();
    private static final Map<HoloScope, AreaDisplayerModule> CLIENT_DISPLAYERS = new ConcurrentHashMap<>();

    private static Backend backend;

    /** Pont vers le client, installe au setup client. */
    public interface Backend
    {
        /** Miroir cree : demande du terrain au serveur. */
        void subscribe(AreaDisplayerModule module);

        /** Miroir retire : fin des envois, donnees client oubliees. */
        void unsubscribe(AreaDisplayerModule module);
    }

    /** Branche le client (setup client). */
    public static void install(Backend backend)
    {
        AreaDisplayerModule.backend = Objects.requireNonNull(backend);
    }

    // ---------------------------------------------------------------- etat sauvegarde et synchronise

    private BlockPos cornerA;
    private BlockPos cornerB;
    private int flags = DEFAULT_FLAGS;
    private int interval = DEFAULT_INTERVAL;
    /** Resolution demandee : {@link AreaGrid#AUTO} ou un palier de {@link AreaGrid#LEVELS}. */
    private int resolution = AreaGrid.AUTO;
    private int generation;
    private int progress = NO_SCAN;

    // ---------------------------------------------------------------- deux cotes

    private AreaDisplayerCube cube;
    /** Grille de la zone (recalculee depuis A et B), {@code null} sans zone. */
    private AreaGrid grid;
    /** Compteur de changements d'etat (serveur : a chaque modification ; client : a chaque reception). */
    private int revision;
    private final HoloTransform lastCube = new HoloTransform();
    private boolean cubeKnown;

    // ---------------------------------------------------------------- serveur

    private byte[] voxels;
    private AreaScanner scanner;
    private final AreaFeed feed = new AreaFeed(this);
    private boolean fullScanRequested;
    private int autoTicks;
    private int progressTicks = PROGRESS_SYNC_TICKS;
    private ServerLevel lastLevel;
    /** Voxels deflates (sauvegarde), recalcules seulement apres un changement. */
    private byte[] savedVoxels;
    private boolean voxelsChanged;
    /** Voxels relus de la sauvegarde et zone qu'ils decrivent, en attente de la grille. */
    private byte[] loadedVoxels;
    private int[] loadedZone;
    /** Scan complet inacheve a la sauvegarde : relance au chargement (voxels partiels montres en attendant). */
    private boolean loadedPending;

    // ---------------------------------------------------------------- cycle de vie

    @Override
    public void onLoad()
    {
        cube = addObject(new AreaDisplayerCube("cube"));
        displayers(host()).put(host().scope(), this);
        rebuildGrid();
        if (host().clientSide()) {
            if (backend != null) backend.subscribe(this);
            return;
        }
        if (grid != null && loadedVoxels != null && loadedZone != null && sameZone(grid, loadedZone)
                && inflate(loadedVoxels, voxels)) {
            savedVoxels = loadedVoxels;
            fullScanRequested = loadedPending;
            autoTicks = interval * 20;
        }
        loadedVoxels = null;
        loadedZone = null;
        feed.reset(grid, voxels, generation);
    }

    @Override
    public void onUnload()
    {
        displayers(host()).remove(host().scope(), this);
        if (host().clientSide()) {
            if (backend != null) backend.unsubscribe(this);
            return;
        }
        if (scanner != null) scanner.stop(lastLevel);
        feed.clear();
        lastLevel = null;
    }

    private static Map<HoloScope, AreaDisplayerModule> displayers(HoloModuleHost host)
    {
        return host.clientSide() ? CLIENT_DISPLAYERS : SERVER_DISPLAYERS;
    }

    /**
     * Area Displayer de la meme memoire de position que {@code module} (meme marker, meme cote), {@code null} s'il n'y
     * en a pas (utilise par l'Area Displayer Configurator, deux cotes). Serveur : confirme par le conteneur du marker.
     * Sans allocation.
     */
    public static AreaDisplayerModule of(HoloModule module)
    {
        HoloModuleHost host = module.host();
        if (host == null || !(host.scope() instanceof HoloScope.Position position)) return null;
        return lookup(displayers(host), position);
    }

    private static AreaDisplayerModule lookup(Map<HoloScope, AreaDisplayerModule> table, HoloScope.Position position)
    {
        AreaDisplayerModule displayer = table.get(position);
        if (displayer == null || displayer.host().clientSide()) return displayer;
        PositionHoloContainer container = HoloContainers.position(position.markerId());
        return container != null && container.module(displayer.instanceId()) == displayer ? displayer : null;
    }

    /**
     * Serveur (canal du terrain) : abonnement ({@code on}) ou desabonnement de {@code player} au Displayer
     * {@code instance} du marker {@code marker} ; un abonnement n'est accepte que si le joueur suit le chunk du marker.
     */
    public static void onSubscribe(ServerPlayer player, String marker, UUID instance, boolean on)
    {
        if (marker.isEmpty() || marker.length() > HoloScope.Position.MAX_ID_LENGTH) return;
        AreaDisplayerModule displayer = lookup(SERVER_DISPLAYERS, new HoloScope.Position(marker));
        if (displayer == null || !displayer.instanceId().equals(instance)) return;
        if (!on) {
            displayer.feed.unsubscribe(player.getUUID());
            return;
        }
        PositionHoloContainer container = HoloContainers.position(marker);
        if (container == null || !AreaFeed.tracks(player, container.level(), container.chunk())) return;
        displayer.feed.subscribe(player);
    }

    /** Identifiant du marker qui porte le module. */
    public String markerId() { return ((HoloScope.Position) host().scope()).markerId(); }

    // ---------------------------------------------------------------- sauvegarde

    @Override
    public void save(CompoundTag tag)
    {
        if (cornerA != null) tag.putIntArray("a", new int[] {cornerA.getX(), cornerA.getY(), cornerA.getZ()});
        if (cornerB != null) tag.putIntArray("b", new int[] {cornerB.getX(), cornerB.getY(), cornerB.getZ()});
        tag.putInt("flags", flags);
        tag.putInt("interval", interval);
        tag.putInt("resolution", resolution);
        tag.putInt("generation", generation);
        if (grid == null) return;
        if (savedVoxels == null || voxelsChanged) {
            savedVoxels = deflate(grid, voxels);
            voxelsChanged = false;
        }
        tag.putIntArray("zone", zoneOf(grid));
        tag.putByteArray("voxels", savedVoxels);
        tag.putBoolean("pending", fullScanRequested
                || scanner != null && scanner.running() && scanner.mode() == AreaScanner.Mode.FULL);
    }

    /** Lecture tolerante : valeur invalide = defaut ; voxels gardes pour la grille de {@code onLoad}. */
    @Override
    public void load(CompoundTag tag)
    {
        cornerA = readCorner(tag, "a");
        cornerB = readCorner(tag, "b");
        flags = tag.contains("flags", Tag.TAG_INT) ? tag.getInt("flags") & (1 << FLAG_COUNT) - 1 : DEFAULT_FLAGS;
        interval = tag.contains("interval", Tag.TAG_INT)
                ? Mth.clamp(tag.getInt("interval"), MIN_INTERVAL, MAX_INTERVAL) : DEFAULT_INTERVAL;
        resolution = AreaGrid.isLevel(tag.getInt("resolution")) ? tag.getInt("resolution") : AreaGrid.AUTO;
        generation = Math.max(0, tag.getInt("generation"));
        int[] zone = tag.getIntArray("zone");
        loadedZone = zone.length == 7 ? zone : null;
        loadedVoxels = tag.contains("voxels", Tag.TAG_BYTE_ARRAY) ? tag.getByteArray("voxels") : null;
        loadedPending = tag.getBoolean("pending");
    }

    private static BlockPos readCorner(CompoundTag tag, String key)
    {
        int[] values = tag.getIntArray(key);
        if (values.length != 3) return null;
        return new BlockPos(Mth.clamp(values[0], -MAX_HORIZONTAL, MAX_HORIZONTAL), values[1],
                Mth.clamp(values[2], -MAX_HORIZONTAL, MAX_HORIZONTAL));
    }

    private static int[] zoneOf(AreaGrid grid)
    {
        return new int[] {grid.minX(), grid.minY(), grid.minZ(), grid.blocksX(), grid.blocksY(), grid.blocksZ(),
                grid.requestedStep()};
    }

    private static boolean sameZone(AreaGrid grid, int[] zone)
    {
        return Arrays.equals(zoneOf(grid), zone);
    }

    /** Toutes les sections empaquetees (2 bits par voxel) puis deflatees. */
    private static byte[] deflate(AreaGrid grid, byte[] voxels)
    {
        Deflater deflater = new Deflater();
        byte[] packed = new byte[AreaGrid.PACKED_BYTES];
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (int s = 0; s < grid.sections(); s++) {
                grid.pack(voxels, s, packed);
                deflater.setInput(packed);
                while (!deflater.needsInput()) {
                    int n = deflater.deflate(buffer);
                    out.write(buffer, 0, n);
                }
            }
            deflater.finish();
            while (!deflater.finished()) {
                int n = deflater.deflate(buffer);
                out.write(buffer, 0, n);
            }
        } finally {
            deflater.end();
        }
        return out.toByteArray();
    }

    /** Inverse de {@link #deflate} ; faux (voxels laisses vides) si les donnees sont invalides. */
    private boolean inflate(byte[] data, byte[] target)
    {
        Inflater inflater = new Inflater();
        byte[] packed = new byte[AreaGrid.PACKED_BYTES];
        try {
            inflater.setInput(data);
            for (int s = 0; s < grid.sections(); s++) {
                int read = 0;
                while (read < packed.length) {
                    int n = inflater.inflate(packed, read, packed.length - read);
                    if (n == 0) throw new IOException("Tronque");
                    read += n;
                }
                grid.unpack(packed, 0, s, target);
            }
            return true;
        } catch (DataFormatException | IOException e) {
            HoloLib.LOGGER.warn("Voxels sauvegardes invalides pour l'Area Displayer {} : rescan", markerId());
            Arrays.fill(target, AreaGrid.AIR);
            return false;
        } finally {
            inflater.end();
        }
    }

    // ---------------------------------------------------------------- synchronisation

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        writeCorner(buf, cornerA);
        writeCorner(buf, cornerB);
        buf.writeByte(flags);
        buf.writeVarInt(interval);
        buf.writeVarInt(resolution);
        buf.writeVarInt(generation);
        buf.writeByte(progress);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        cornerA = readCorner(buf);
        cornerB = readCorner(buf);
        flags = buf.readByte() & (1 << FLAG_COUNT) - 1;
        interval = Mth.clamp(buf.readVarInt(), MIN_INTERVAL, MAX_INTERVAL);
        int step = buf.readVarInt();
        resolution = AreaGrid.isLevel(step) ? step : AreaGrid.AUTO;
        generation = buf.readVarInt();
        progress = buf.readUnsignedByte();
        revision++;
        rebuildGrid();
    }

    private static void writeCorner(FriendlyByteBuf buf, BlockPos pos)
    {
        buf.writeBoolean(pos != null);
        if (pos == null) return;
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
    }

    private static BlockPos readCorner(FriendlyByteBuf buf)
    {
        if (!buf.readBoolean()) return null;
        return new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
    }

    /** Recalcule la grille depuis A et B (deux cotes) ; serveur : voxels et scanner neufs si la zone change. */
    private void rebuildGrid()
    {
        AreaGrid next = null;
        if (cornerA != null && cornerB != null) {
            int minX = Math.min(cornerA.getX(), cornerB.getX());
            int minY = Math.min(cornerA.getY(), cornerB.getY());
            int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
            next = new AreaGrid(minX, minY, minZ, Math.max(cornerA.getX(), cornerB.getX()) - minX + 1,
                    Math.max(cornerA.getY(), cornerB.getY()) - minY + 1,
                    Math.max(cornerA.getZ(), cornerB.getZ()) - minZ + 1, resolution);
        }
        if (next == null ? grid == null : next.sameZone(grid)) return;
        grid = next;
        if (host().clientSide()) return;
        if (scanner != null) scanner.stop(lastLevel);
        voxels = grid != null ? new byte[grid.voxels()] : null;
        scanner = grid != null ? new AreaScanner(grid, voxels) : null;
        fullScanRequested = grid != null;
        savedVoxels = null;
        voxelsChanged = true;
    }

    // ---------------------------------------------------------------- serveur

    /** Sondage du pave, scan (complet demande, Auto Update), avancement, envois aux abonnes. */
    @Override
    public void serverTick(ServerLevel level)
    {
        lastLevel = level;
        pollCube();
        if (scanner != null) {
            if (fullScanRequested) {
                fullScanRequested = false;
                autoTicks = 0;
                scanner.start(level, AreaScanner.Mode.FULL);
            } else if (flag(FLAG_AUTO_UPDATE) && !scanner.running() && ++autoTicks >= interval * 20) {
                autoTicks = 0;
                scanner.start(level, AreaScanner.Mode.LOADED);
            }
            scanner.tick(level, SCAN_BUDGET_NANOS, this::sectionChanged);
        }
        updateProgress();
        PositionHoloContainer container = HoloContainers.position(markerId());
        feed.tick(level, container != null ? container.chunk() : null, level.getServer().getTickCount(),
                flag(FLAG_PLAYERS), flag(FLAG_ENTITIES));
    }

    private void sectionChanged(int section)
    {
        voxelsChanged = true;
        feed.sectionChanged(section);
    }

    /** Avancement envoye au plus toutes les {@value #PROGRESS_SYNC_TICKS} ticks (et aussitot a la fin). */
    private void updateProgress()
    {
        int value = scanner != null && scanner.running() ? Math.min(99, (int) (scanner.progress() * 100f)) : NO_SCAN;
        if (progressTicks < PROGRESS_SYNC_TICKS) progressTicks++;
        if (value == progress || value != NO_SCAN && progressTicks < PROGRESS_SYNC_TICKS) return;
        progress = value;
        progressTicks = 0;
        changed();
    }

    private void changed()
    {
        revision++;
        markDirty();
    }

    /** Echelle du pave ramenee a la moyenne si une edition l'a etiree (proportions = celles de la zone). */
    private void pollCube()
    {
        HoloTransform t = cube.transform();
        if (cubeKnown && sameTransform(t, lastCube)) return;
        float mean = (t.scaleX() + t.scaleY() + t.scaleZ()) / 3f;
        float tolerance = UNIFORM_EPSILON * mean;
        if (Math.abs(t.scaleX() - mean) > tolerance || Math.abs(t.scaleY() - mean) > tolerance
                || Math.abs(t.scaleZ() - mean) > tolerance) {
            t.setScale(mean);
        }
        lastCube.set(t);
        cubeKnown = true;
    }

    private static boolean sameTransform(HoloTransform a, HoloTransform b)
    {
        return a.x() == b.x() && a.y() == b.y() && a.z() == b.z() && a.yaw() == b.yaw() && a.pitch() == b.pitch()
                && a.roll() == b.roll() && a.scaleX() == b.scaleX() && a.scaleY() == b.scaleY()
                && a.scaleZ() == b.scaleZ();
    }

    // ---------------------------------------------------------------- API serveur (Configurator)

    /**
     * Serveur : pose le coin A ({@code second} faux) ou B ; hauteur bornee a celle du niveau, horizontale a
     * {@value #MAX_HORIZONTAL}. Une nouvelle zone remet la grille a zero et lance un scan complet.
     */
    public void setCorner(boolean second, BlockPos pos, ServerLevel level)
    {
        BlockPos clamped = new BlockPos(Mth.clamp(pos.getX(), -MAX_HORIZONTAL, MAX_HORIZONTAL),
                Mth.clamp(pos.getY(), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1),
                Mth.clamp(pos.getZ(), -MAX_HORIZONTAL, MAX_HORIZONTAL));
        if (clamped.equals(second ? cornerB : cornerA)) return;
        if (second) cornerB = clamped;
        else cornerA = clamped;
        zoneChanged();
    }

    /**
     * Serveur : resolution demandee, {@link AreaGrid#AUTO} ou un palier de {@link AreaGrid#LEVELS} (autre valeur
     * ignoree) ; une nouvelle grille remet les voxels a zero et lance un scan complet.
     */
    public void setResolution(int step)
    {
        if (!AreaGrid.isLevel(step) || step == resolution) return;
        resolution = step;
        zoneChanged();
    }

    /** Grille recalculee ; si elle change : nouvelle generation, abonnes remis a zero (scan complet demande). */
    private void zoneChanged()
    {
        AreaGrid before = grid;
        rebuildGrid();
        if (grid != before) {
            generation++;
            feed.reset(grid, voxels, generation);
        }
        changed();
    }

    /** Serveur : option {@code flag} ({@code FLAG_*}). */
    public void setFlag(int flag, boolean on)
    {
        if (flag < 0 || flag >= FLAG_COUNT || flag(flag) == on) return;
        flags = on ? flags | 1 << flag : flags & ~(1 << flag);
        if (flag == FLAG_AUTO_UPDATE) autoTicks = 0;
        changed();
    }

    /** Serveur : intervalle de l'Auto Update, en secondes (borne). */
    public void setInterval(int seconds)
    {
        int value = Mth.clamp(seconds, MIN_INTERVAL, MAX_INTERVAL);
        if (value == interval) return;
        interval = value;
        changed();
    }

    /** Serveur : « Actualiser » : nouveau scan complet (sections changees seulement envoyees). */
    public void refresh()
    {
        if (scanner != null) fullScanRequested = true;
    }

    // ---------------------------------------------------------------- lecture (deux cotes)

    public AreaDisplayerCube cube() { return cube; }

    /** Grille de la zone, {@code null} sans zone. */
    public AreaGrid grid() { return grid; }

    public BlockPos cornerA() { return cornerA; }

    public BlockPos cornerB() { return cornerB; }

    public boolean flag(int flag) { return (flags & 1 << flag) != 0; }

    public int interval() { return interval; }

    /** Resolution demandee : {@link AreaGrid#AUTO} ou un palier (la resolution effective est {@code grid().step()}). */
    public int resolution() { return resolution; }

    public int generation() { return generation; }

    /** Avancement du scan en cours (0 a 99), {@link #NO_SCAN} sans scan. */
    public int progress() { return progress; }

    /** Compteur de changements d'etat : un lecteur (fenetre du Configurator) se rafraichit quand il change. */
    public int revision() { return revision; }
}
