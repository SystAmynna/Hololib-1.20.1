package com.amynna.hololib.modules.monde.areadisplayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;

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
 * existants ; les sections changees sont signalees.
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
    private final byte[] voxels;

    /** Echantillons par axe : coordonnee de bloc et voxel, croissants ; nombre d'echantillons par voxel. */
    private final int[] sampleX;
    private final int[] voxelX;
    private final int[] countX;
    private final int[] sampleY;
    private final int[] voxelY;
    private final int[] countY;
    private final int[] sampleZ;
    private final int[] voxelZ;
    private final int[] countZ;
    /** Premier echantillon de chaque colonne de sections, par axe ({@code sections + 1} bornes). */
    private final int[] startX;
    private final int[] startZ;

    // ---------------------------------------------------------------- passage en cours

    private Mode mode;
    private boolean running;
    /** Colonne de sections courante (indice {@code sz * sectionsX + sx}). */
    private int column;
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
    private final boolean[] changedSections;

    /** @param voxels voxels de la grille, mis a jour sur place */
    public AreaScanner(AreaGrid grid, byte[] voxels)
    {
        this.grid = grid;
        this.voxels = voxels;
        int step = grid.step();
        int per = step <= 4 ? step : step < 32 ? 4 : 2;
        int[][] x = samples(grid.minX(), grid.blocksX(), grid.sizeX(), step, per);
        int[][] y = samples(grid.minY(), grid.blocksY(), grid.sizeY(), step, per);
        int[][] z = samples(grid.minZ(), grid.blocksZ(), grid.sizeZ(), step, per);
        sampleX = x[0];
        voxelX = x[1];
        countX = x[2];
        sampleY = y[0];
        voxelY = y[1];
        countY = y[2];
        sampleZ = z[0];
        voxelZ = z[1];
        countZ = z[2];
        startX = starts(voxelX, grid.sectionsX());
        startZ = starts(voxelZ, grid.sectionsZ());
        solid = new byte[AreaGrid.SECTION * AreaGrid.SECTION * grid.sizeY()];
        fluid = new byte[solid.length];
        changedSections = new boolean[grid.sectionsY()];
    }

    /** Echantillons d'un axe : {@code [blocs, voxels, comptes par voxel]}. */
    private static int[][] samples(int min, int blocks, int size, int step, int per)
    {
        int[] coords = new int[size * per];
        int[] owners = new int[size * per];
        int[] counts = new int[size];
        int n = 0;
        for (int v = 0; v < size; v++) {
            for (int k = 0; k < per; k++) {
                int offset = (int) ((k + 0.5) * step / per);
                int block = v * step + offset;
                if (block >= blocks) break;
                coords[n] = min + block;
                owners[n] = v;
                counts[v]++;
                n++;
            }
        }
        return new int[][] {Arrays.copyOf(coords, n), Arrays.copyOf(owners, n), counts};
    }

    /** Indice du premier echantillon de chaque section ({@code sections + 1} bornes). */
    private static int[] starts(int[] owners, int sections)
    {
        int[] starts = new int[sections + 1];
        int i = 0;
        for (int s = 0; s <= sections; s++) {
            int first = s * AreaGrid.SECTION;
            while (i < owners.length && owners[i] < first) i++;
            starts[s] = i;
        }
        return starts;
    }

    public AreaGrid grid() { return grid; }

    public boolean running() { return running; }

    /** Type du passage en cours ou du dernier. */
    public Mode mode() { return mode; }

    /** Avancement du passage en cours, de 0 a 1 ({@code 1} sans passage). */
    public float progress()
    {
        int total = grid.sectionsX() * grid.sectionsZ();
        return running ? (float) column / total : 1f;
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
     * Avance le passage dans la limite de {@code budgetNanos} ; {@code changed} recoit l'indice de chaque section dont
     * les voxels ont change.
     */
    public void tick(ServerLevel level, long budgetNanos, IntConsumer changed)
    {
        if (!running) return;
        long deadline = System.nanoTime() + budgetNanos;
        int tick = level.getServer().getTickCount();
        int columns = grid.sectionsX() * grid.sectionsZ();
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
        int sx = column % grid.sectionsX();
        int sz = column / grid.sectionsX();
        chunkXs = distinctChunks(sampleX, startX[sx], startX[sx + 1]);
        chunkZs = distinctChunks(sampleZ, startZ[sz], startZ[sz + 1]);
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

    private static int[] distinctChunks(int[] coords, int from, int to)
    {
        int[] out = new int[Math.max(0, to - from)];
        int n = 0;
        for (int i = from; i < to; i++) {
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
        int sx = column % grid.sectionsX();
        int sz = column / grid.sectionsX();
        int x0 = startX[sx];
        int x1 = startX[sx + 1];
        int z0 = startZ[sz];
        int z1 = startZ[sz + 1];
        int baseX = sx * AreaGrid.SECTION;
        int baseZ = sz * AreaGrid.SECTION;
        LevelChunkSection[] sections = chunk.getSections();
        int lastIndex = Integer.MIN_VALUE;
        LevelChunkSection section = null;
        for (int iy = 0; iy < sampleY.length; iy++) {
            int by = sampleY[iy];
            int index = chunk.getSectionIndex(by);
            if (index != lastIndex) {
                lastIndex = index;
                section = index >= 0 && index < sections.length ? sections[index] : null;
                if (section != null && section.hasOnlyAir()) section = null;
            }
            if (section == null) continue;
            int rowY = voxelY[iy] * AreaGrid.SECTION;
            for (int iz = z0; iz < z1; iz++) {
                int bz = sampleZ[iz];
                if (bz >> 4 != cz) continue;
                int row = (rowY + voxelZ[iz] - baseZ) * AreaGrid.SECTION - baseX;
                for (int ix = x0; ix < x1; ix++) {
                    int bx = sampleX[ix];
                    if (bx >> 4 != cx) continue;
                    BlockState state = section.getBlockState(bx & 15, by & 15, bz & 15);
                    if (state.isAir()) continue;
                    int at = row + voxelX[ix];
                    if (state.blocksMotion()) solid[at]++;
                    else if (!state.getFluidState().isEmpty()) fluid[at]++;
                }
            }
        }
    }

    /** Classe les voxels de la colonne, ecrit ceux qui changent et signale leurs sections. */
    private void finishColumn(IntConsumer changed)
    {
        int sx = column % grid.sectionsX();
        int sz = column / grid.sectionsX();
        int baseX = sx * AreaGrid.SECTION;
        int baseZ = sz * AreaGrid.SECTION;
        int endX = Math.min(grid.sizeX(), baseX + AreaGrid.SECTION);
        int endZ = Math.min(grid.sizeZ(), baseZ + AreaGrid.SECTION);
        Arrays.fill(changedSections, false);
        for (int vy = 0; vy < grid.sizeY(); vy++) {
            for (int vz = baseZ; vz < endZ; vz++) {
                int row = (vy * AreaGrid.SECTION + vz - baseZ) * AreaGrid.SECTION - baseX;
                for (int vx = baseX; vx < endX; vx++) {
                    int total = countX[vx] * countY[vy] * countZ[vz];
                    int s = solid[row + vx];
                    int f = fluid[row + vx];
                    byte value = total == 0 ? AreaGrid.AIR
                            : s * 4 >= total ? AreaGrid.SOLID
                            : (s + f) * 4 >= total ? AreaGrid.FLUID : AreaGrid.AIR;
                    int index = grid.index(vx, vy, vz);
                    if (voxels[index] != value) {
                        voxels[index] = value;
                        changedSections[vy / AreaGrid.SECTION] = true;
                    }
                }
            }
        }
        for (int sy = 0; sy < changedSections.length; sy++) {
            if (changedSections[sy]) changed.accept(grid.sectionIndex(sx, sy, sz));
        }
    }
}
