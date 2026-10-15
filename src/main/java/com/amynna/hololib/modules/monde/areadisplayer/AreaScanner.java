package com.amynna.hololib.modules.monde.areadisplayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongConsumer;

/**
 * Calcul des voxels d'une {@link AreaGrid} cote serveur, etale sur les ticks (budget de temps par tick). Aucune
 * ecoute des changements de blocs : un passage est lance a la demande (zone changee, « Actualiser ») ou
 * periodiquement par l'Auto Update.
 * <p>
 * <b>Echantillonnage</b> : par voxel et par axe, {@code min(step, m)} points ({@code m} = 4, 2 au-dela de 32 blocs
 * par voxel) ; un voxel est solide si au moins un quart de ses points le sont ({@code BlockState.blocksMotion}), sinon
 * fluide si au moins un quart sont solides ou fluides, sinon vide.
 * <p>
 * <b>Ordre</b> : par colonne de sections (16 x 16 voxels en X / Z, toute la hauteur) ; dans une colonne, chunk par
 * chunk des qu'il est disponible, comptes accumules, puis la colonne entiere est classee et comparee aux voxels
 * existants ; les sections changees sont signalees (cle de section). Les echantillons des axes X et Z sont calcules
 * colonne par colonne (taille fixe), jamais pour toute la zone : une zone immense ne coute pas de memoire de scan.
 * Les blocs de la liste noire ({@code ignored}) sont comptes comme de l'air.
 * <p>
 * <b>Chunks</b> : {@link Mode#FULL} : un chunk non charge est d'abord cherche sur le disque ({@code ChunkMap.read},
 * asynchrone) ; jamais genere (absent, ou non termine) = vide, aucune generation forcee ; sinon charge par un ticket
 * temporaire ({@link #TICKET}), retire apres lecture. {@link Mode#LOADED} (Auto Update) : seuls les chunks deja
 * charges sont lus ; une colonne dont un chunk n'est pas charge est sautee (voxels gardes).
 */
public final class AreaScanner
{
    /** Type de passage. */
    public enum Mode
    {
        /** Toute la zone, chunks charges a la demande (zone changee, « Actualiser »). */
        FULL,
        /** Chunks deja charges seulement (Auto Update). */
        LOADED
    }

    /** Ticket de chargement des chunks lus, expire seul apres {@value #TICKET_LIFESPAN} ticks. */
    private static final int TICKET_LIFESPAN = 300;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("hololib_area",
            Comparator.comparingLong(ChunkPos::toLong), TICKET_LIFESPAN);
    /** Chunks en cours de lecture disque ou de chargement, au plus. */
    private static final int MAX_PENDING = 48;
    /** Ticks apres lesquels un chunk attendu est tenu pour vide. */
    private static final int LOAD_TIMEOUT = 600;
    /** Ticks entre deux renouvellements du ticket d'un chunk attendu. */
    private static final int TICKET_REFRESH = 100;

    private static final int STATE_UNKNOWN = 0;
    private static final int STATE_READING = 1;
    private static final int STATE_LOADING = 2;
    private static final int STATE_DONE = 3;

    private final AreaGrid grid;
    private final AreaVoxels voxels;
    /** Blocs ignores (comptes comme de l'air) ; remplace en bloc, lu par le fil serveur seulement. */
    private Set<Block> ignored;

    /** Echantillons par axe et par voxel (comme {@code min(step, m)}). */
    private final int per;
    /** Axe Y, pour toute la hauteur : coordonnee de bloc, voxel et comptes par voxel. */
    private final int[] sampleY;
    private final int[] voxelY;
    private final int[] countY;
    private final int samplesY;
    /** Axes X et Z de la colonne courante : coordonnee de bloc, voxel relatif a la colonne, comptes par voxel. */
    private final int[] sampleX;
    private final int[] voxelX;
    private final int[] countX;
    private int samplesX;
    private final int[] sampleZ;
    private final int[] voxelZ;
    private final int[] countZ;
    private int samplesZ;

    // ---------------------------------------------------------------- passage en cours

    private Mode mode;
    private boolean running;
    /** Colonne de sections courante (indice {@code sz * sectionsX + sx}). */
    private long column;
    private boolean columnReady;
    private boolean columnAborted;
    /** Chunks de la colonne : coordonnees distinctes, etat et date d'attente. */
    private int[] chunkXs = new int[0];
    private int[] chunkZs = new int[0];
    private int[] chunkState = new int[0];
    private int[] chunkSince = new int[0];
    @SuppressWarnings("unchecked")
    private CompletableFuture<Optional<CompoundTag>>[] chunkReads = new CompletableFuture[0];
    private int remaining;
    /** Comptes de la colonne : solides, fluides ; indice {@code (vy * 16 + lz) * 16 + lx}. */
    private final byte[] solid;
    private final byte[] fluid;
    /** Voxels d'une section en cours de classement (reutilise). */
    private final byte[] scratch = new byte[AreaGrid.SECTION_VOXELS];

    /**
     * @param voxels  voxels de la grille, mis a jour sur place
     * @param ignored blocs comptes comme de l'air (liste noire du Displayer)
     */
    public AreaScanner(AreaGrid grid, AreaVoxels voxels, Set<Block> ignored)
    {
        this.grid = grid;
        this.voxels = voxels;
        this.ignored = ignored;
        int step = grid.step();
        per = step <= 4 ? step : step < 32 ? 4 : 2;
        sampleY = new int[grid.sizeY() * per];
        voxelY = new int[sampleY.length];
        countY = new int[grid.sizeY()];
        samplesY = samples(grid.minY(), grid.blocksY(), step, per, 0, grid.sizeY(), sampleY, voxelY, countY);
        sampleX = new int[AreaGrid.SECTION * per];
        voxelX = new int[sampleX.length];
        countX = new int[AreaGrid.SECTION];
        sampleZ = new int[AreaGrid.SECTION * per];
        voxelZ = new int[sampleZ.length];
        countZ = new int[AreaGrid.SECTION];
        solid = new byte[AreaGrid.SECTION * AreaGrid.SECTION * grid.sizeY()];
        fluid = new byte[solid.length];
    }

    /**
     * Echantillons des voxels {@code first} a {@code first + count - 1} d'un axe : coordonnee de bloc, voxel relatif a
     * {@code first} et nombre d'echantillons par voxel relatif.
     *
     * @return le nombre d'echantillons ecrits
     */
    private static int samples(int min, int blocks, int step, int per, int first, int count, int[] coords,
                               int[] owners, int[] counts)
    {
        Arrays.fill(counts, 0, count, 0);
        int n = 0;
        for (int v = 0; v < count; v++) {
            for (int k = 0; k < per; k++) {
                int offset = (int) ((k + 0.5) * step / per);
                long block = (long) (first + v) * step + offset;
                if (block >= blocks) break;
                coords[n] = min + (int) block;
                owners[n] = v;
                counts[v]++;
                n++;
            }
        }
        return n;
    }

    /** Nouveaux blocs ignores ; le prochain passage complet en tient compte. */
    public void setIgnored(Set<Block> ignored) { this.ignored = ignored; }

    public AreaGrid grid() { return grid; }

    public boolean running() { return running; }

    /** Type du passage en cours ou du dernier. */
    public Mode mode() { return mode; }

    /** Avancement du passage en cours, de 0 a 1 ({@code 1} sans passage). */
    public float progress()
    {
        return running ? (float) ((double) column / grid.columns()) : 1f;
    }

    /** Lance un passage (remplace celui en cours). */
    public void start(ServerLevel level, Mode mode)
    {
        stop(level);
        this.mode = mode;
        running = true;
        column = 0;
        columnReady = false;
    }

    /** Arrete le passage en cours et rend les tickets poses. */
    public void stop(ServerLevel level)
    {
        if (level != null) releaseTickets(level);
        running = false;
        columnReady = false;
    }

    /**
     * Avance le passage dans la limite de {@code budgetNanos} ; {@code changed} recoit la cle de chaque section dont
     * les voxels ont change.
     */
    public void tick(ServerLevel level, long budgetNanos, LongConsumer changed)
    {
        if (!running) return;
        long deadline = System.nanoTime() + budgetNanos;
        int tick = level.getServer().getTickCount();
        long columns = grid.columns();
        while (running && System.nanoTime() < deadline) {
            if (column >= columns) {
                running = false;
                break;
            }
            if (!columnReady) prepareColumn();
            boolean progress = advanceColumn(level, tick, deadline);
            if (remaining == 0 || columnAborted) {
                if (!columnAborted) finishColumn(changed);
                releaseTickets(level);
                column++;
                columnReady = false;
                continue;
            }
            if (!progress) break;
        }
    }

    // ---------------------------------------------------------------- colonne

    private void prepareColumn()
    {
        int sx = (int) (column % grid.sectionsX());
        int sz = (int) (column / grid.sectionsX());
        int step = grid.step();
        int firstX = sx * AreaGrid.SECTION;
        int firstZ = sz * AreaGrid.SECTION;
        samplesX = samples(grid.minX(), grid.blocksX(), step, per, firstX,
                Math.min(AreaGrid.SECTION, grid.sizeX() - firstX), sampleX, voxelX, countX);
        samplesZ = samples(grid.minZ(), grid.blocksZ(), step, per, firstZ,
                Math.min(AreaGrid.SECTION, grid.sizeZ() - firstZ), sampleZ, voxelZ, countZ);
        chunkXs = distinctChunks(sampleX, samplesX);
        chunkZs = distinctChunks(sampleZ, samplesZ);
        int count = chunkXs.length * chunkZs.length;
        if (chunkState.length < count) {
            chunkState = new int[count];
            chunkSince = new int[count];
            @SuppressWarnings("unchecked")
            CompletableFuture<Optional<CompoundTag>>[] reads = new CompletableFuture[count];
            chunkReads = reads;
        }
        Arrays.fill(chunkState, 0, count, STATE_UNKNOWN);
        Arrays.fill(chunkReads, 0, count, null);
        remaining = count;
        columnAborted = false;
        columnReady = true;
        Arrays.fill(solid, (byte) 0);
        Arrays.fill(fluid, (byte) 0);
    }

    private static int[] distinctChunks(int[] coords, int count)
    {
        int[] out = new int[count];
        int n = 0;
        for (int i = 0; i < count; i++) {
            int chunk = coords[i] >> 4;
            if (n == 0 || out[n - 1] != chunk) out[n++] = chunk;
        }
        return Arrays.copyOf(out, n);
    }

    /** Lit les chunks disponibles de la colonne ; faux si rien n'a pu avancer (attente du disque). */
    private boolean advanceColumn(ServerLevel level, int tick, long deadline)
    {
        ServerChunkCache source = level.getChunkSource();
        boolean progress = false;
        int pending = 0;
        int count = chunkXs.length * chunkZs.length;
        for (int i = 0; i < count && System.nanoTime() < deadline; i++) {
            int state = chunkState[i];
            if (state == STATE_DONE) continue;
            int cx = chunkXs[i % chunkXs.length];
            int cz = chunkZs[i / chunkXs.length];
            LevelChunk chunk = source.getChunkNow(cx, cz);
            if (chunk != null) {
                readChunk(chunk, cx, cz);
                if (state == STATE_LOADING) source.removeRegionTicket(TICKET, new ChunkPos(cx, cz), 0,
                        new ChunkPos(cx, cz));
                markDone(i);
                progress = true;
                continue;
            }
            if (mode == Mode.LOADED) {
                columnAborted = true;
                return true;
            }
            switch (state) {
                case STATE_UNKNOWN -> {
                    if (pending >= MAX_PENDING) continue;
                    chunkReads[i] = source.chunkMap.read(new ChunkPos(cx, cz));
                    chunkState[i] = STATE_READING;
                    chunkSince[i] = tick;
                    pending++;
                }
                case STATE_READING -> {
                    CompletableFuture<Optional<CompoundTag>> read = chunkReads[i];
                    if (!read.isDone()) {
                        if (tick - chunkSince[i] > LOAD_TIMEOUT) {
                            markDone(i);
                            progress = true;
                        } else {
                            pending++;
                        }
                        continue;
                    }
                    chunkReads[i] = null;
                    CompoundTag tag = read.isCompletedExceptionally() ? null : read.join().orElse(null);
                    if (tag == null || ChunkSerializer.getChunkTypeFromTag(tag) != ChunkStatus.ChunkType.LEVELCHUNK) {
                        markDone(i);
                        progress = true;
                        continue;
                    }
                    ChunkPos pos = new ChunkPos(cx, cz);
                    source.addRegionTicket(TICKET, pos, 0, pos);
                    chunkState[i] = STATE_LOADING;
                    chunkSince[i] = tick;
                    pending++;
                    progress = true;
                }
                case STATE_LOADING -> {
                    int waited = tick - chunkSince[i];
                    ChunkPos pos = new ChunkPos(cx, cz);
                    if (waited > LOAD_TIMEOUT) {
                        source.removeRegionTicket(TICKET, pos, 0, pos);
                        markDone(i);
                        progress = true;
                        continue;
                    }
                    if (waited > 0 && waited % TICKET_REFRESH == 0) source.addRegionTicket(TICKET, pos, 0, pos);
                    pending++;
                }
                default -> {
                }
            }
        }
        return progress;
    }

    private void markDone(int i)
    {
        chunkState[i] = STATE_DONE;
        chunkReads[i] = null;
        remaining--;
    }

    /** Rend les tickets des chunks encore attendus de la colonne courante. */
    private void releaseTickets(ServerLevel level)
    {
        if (!columnReady) return;
        int count = chunkXs.length * chunkZs.length;
        for (int i = 0; i < count; i++) {
            if (chunkState[i] != STATE_LOADING) continue;
            ChunkPos pos = new ChunkPos(chunkXs[i % chunkXs.length], chunkZs[i / chunkXs.length]);
            level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
            chunkState[i] = STATE_DONE;
        }
    }

    /** Ajoute aux comptes de la colonne les echantillons du chunk {@code (cx, cz)}. */
    private void readChunk(LevelChunk chunk, int cx, int cz)
    {
        LevelChunkSection[] sections = chunk.getSections();
        int lastIndex = Integer.MIN_VALUE;
        LevelChunkSection section = null;
        for (int iy = 0; iy < samplesY; iy++) {
            int by = sampleY[iy];
            int index = chunk.getSectionIndex(by);
            if (index != lastIndex) {
                lastIndex = index;
                section = index >= 0 && index < sections.length ? sections[index] : null;
                if (section != null && section.hasOnlyAir()) section = null;
            }
            if (section == null) continue;
            int rowY = voxelY[iy] * AreaGrid.SECTION;
            for (int iz = 0; iz < samplesZ; iz++) {
                int bz = sampleZ[iz];
                if (bz >> 4 != cz) continue;
                int row = (rowY + voxelZ[iz]) * AreaGrid.SECTION;
                for (int ix = 0; ix < samplesX; ix++) {
                    int bx = sampleX[ix];
                    if (bx >> 4 != cx) continue;
                    BlockState state = section.getBlockState(bx & 15, by & 15, bz & 15);
                    if (state.isAir() || !ignored.isEmpty() && ignored.contains(state.getBlock())) continue;
                    int at = row + voxelX[ix];
                    if (state.blocksMotion()) solid[at]++;
                    else if (!state.getFluidState().isEmpty()) fluid[at]++;
                }
            }
        }
    }

    /** Classe les voxels de la colonne, remplace ses sections et signale celles qui changent. */
    private void finishColumn(LongConsumer changed)
    {
        int sx = (int) (column % grid.sectionsX());
        int sz = (int) (column / grid.sectionsX());
        int width = Math.min(AreaGrid.SECTION, grid.sizeX() - sx * AreaGrid.SECTION);
        int depth = Math.min(AreaGrid.SECTION, grid.sizeZ() - sz * AreaGrid.SECTION);
        for (int sy = 0; sy < grid.sectionsY(); sy++) {
            Arrays.fill(scratch, (byte) 0);
            int baseY = sy * AreaGrid.SECTION;
            int height = Math.min(AreaGrid.SECTION, grid.sizeY() - baseY);
            for (int ly = 0; ly < height; ly++) {
                int vy = baseY + ly;
                for (int lz = 0; lz < depth; lz++) {
                    int row = (vy * AreaGrid.SECTION + lz) * AreaGrid.SECTION;
                    for (int lx = 0; lx < width; lx++) {
                        int total = countX[lx] * countY[vy] * countZ[lz];
                        int s = solid[row + lx];
                        int f = fluid[row + lx];
                        byte value = total == 0 ? AreaGrid.AIR
                                : s * 4 >= total ? AreaGrid.SOLID
                                : (s + f) * 4 >= total ? AreaGrid.FLUID : AreaGrid.AIR;
                        scratch[AreaGrid.localIndex(lx, ly, lz)] = value;
                    }
                }
            }
            long key = AreaGrid.sectionKey(sx, sy, sz);
            if (voxels.put(key, scratch)) changed.accept(key);
        }
    }
}
