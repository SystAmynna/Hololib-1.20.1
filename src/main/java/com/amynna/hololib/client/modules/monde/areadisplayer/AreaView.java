package com.amynna.hololib.client.modules.monde.areadisplayer;

import com.amynna.hololib.modules.monde.areadisplayer.AreaGrid;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Donnees client d'un Area Displayer abonne (fil client) : grille et voxels recus, maillage par section, positions
 * suivies et leurs trainees. Ecrit par {@link AreaTerrainClient}, lu par le renderer du pave.
 */
public final class AreaView
{
    /** Points de trainee gardes par entree. */
    public static final int TRAIL_POINTS = 64;
    /** Duree d'une trainee, en ticks (5 s). */
    public static final int TRAIL_TICKS = 100;
    /** Ticks entre deux positions recues (interpolation). */
    public static final float TRACK_PERIOD = 2f;

    /** Position suivie : position courante et precedente (unites de voxels), nom, trainee circulaire. */
    public static final class Track
    {
        int kind;
        String name;
        float px, py, pz;
        float cx, cy, cz;
        int received;
        boolean seen;
        final float[] trail = new float[3 * TRAIL_POINTS];
        final int[] trailTicks = new int[TRAIL_POINTS];
        int trailHead;
        int trailSize;

        public int kind() { return kind; }

        /** Nom recu, {@code null} s'il n'est pas encore connu. */
        public String name() { return name; }

        /** Position interpolee entre les deux dernieres recues, ecrite dans {@code out} (unites de voxels). */
        public void position(int now, float partialTick, float[] out)
        {
            float t = Math.min(1f, Math.max(0f, (now - received + partialTick) / TRACK_PERIOD));
            out[0] = px + (cx - px) * t;
            out[1] = py + (cy - py) * t;
            out[2] = pz + (cz - pz) * t;
        }

        public int trailSize() { return trailSize; }

        /** Point {@code i} de la trainee, du plus ancien ({@code 0}) au plus recent ; renvoie sa date (tick client). */
        public int trailPoint(int i, float[] out)
        {
            int index = (trailHead - trailSize + i + TRAIL_POINTS) % TRAIL_POINTS;
            out[0] = trail[3 * index];
            out[1] = trail[3 * index + 1];
            out[2] = trail[3 * index + 2];
            return trailTicks[index];
        }

        void addTrail(float x, float y, float z, int tick)
        {
            trail[3 * trailHead] = x;
            trail[3 * trailHead + 1] = y;
            trail[3 * trailHead + 2] = z;
            trailTicks[trailHead] = tick;
            trailHead = (trailHead + 1) % TRAIL_POINTS;
            if (trailSize < TRAIL_POINTS) trailSize++;
        }
    }

    final String marker;
    final UUID instance;
    int generation = -1;
    AreaGrid grid;
    byte[] voxels;
    /** Quads de chaque section ({@code null} : aucune face), sections a remailler. */
    float[][] meshes = new float[0][];
    boolean[] dirty = new boolean[0];
    int dirtyCount;
    final Int2ObjectOpenHashMap<Track> tracks = new Int2ObjectOpenHashMap<>();
    /** Memes entrees en liste (parcours sans iterateur par le renderer), refaite a chaque reception. */
    final List<Track> trackList = new ArrayList<>();

    AreaView(String marker, UUID instance)
    {
        this.marker = marker;
        this.instance = instance;
    }

    /** Grille des voxels recus, {@code null} avant le premier {@code Header} ou sans zone. */
    public AreaGrid grid() { return grid; }

    /** Quads des sections (voir {@link AreaMesher}) ; une entree {@code null} n'a rien a dessiner. */
    public float[][] meshes() { return meshes; }

    /** Nombre de positions suivies. */
    public int trackCount() { return trackList.size(); }

    /** Position suivie {@code i} (ordre de reception). */
    public Track track(int i) { return trackList.get(i); }

    void reset(AreaGrid grid)
    {
        this.grid = grid;
        voxels = grid != null ? new byte[grid.voxels()] : null;
        int sections = grid != null ? grid.sections() : 0;
        meshes = new float[sections][];
        dirty = new boolean[sections];
        dirtyCount = 0;
        tracks.clear();
        trackList.clear();
    }

    /** Section {@code section} et ses six voisines a remailler (faces partagees). */
    void markDirty(int section)
    {
        int sx = grid.sectionX(section);
        int sy = grid.sectionY(section);
        int sz = grid.sectionZ(section);
        mark(sx, sy, sz);
        mark(sx - 1, sy, sz);
        mark(sx + 1, sy, sz);
        mark(sx, sy - 1, sz);
        mark(sx, sy + 1, sz);
        mark(sx, sy, sz - 1);
        mark(sx, sy, sz + 1);
    }

    private void mark(int sx, int sy, int sz)
    {
        if (sx < 0 || sy < 0 || sz < 0 || sx >= grid.sectionsX() || sy >= grid.sectionsY()
                || sz >= grid.sectionsZ()) {
            return;
        }
        int index = grid.sectionIndex(sx, sy, sz);
        if (!dirty[index]) {
            dirty[index] = true;
            dirtyCount++;
        }
    }
}
