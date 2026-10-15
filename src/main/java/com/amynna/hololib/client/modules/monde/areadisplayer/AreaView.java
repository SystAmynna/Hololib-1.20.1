package com.amynna.hololib.client.modules.monde.areadisplayer;

import com.amynna.hololib.modules.monde.areadisplayer.AreaGrid;
import com.amynna.hololib.modules.monde.areadisplayer.AreaVoxels;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Donnees client d'un Area Displayer abonne (fil client) : grille et voxels recus (stockage creux), maillage par
 * {@linkplain Tile tuile} de 64 voxels (unite de maillage, de culling et de dessin), positions suivies et leurs
 * trainees. Ecrit par {@link AreaTerrainClient}, lu par le renderer du pave.
 * <p>
 * <b>Mise a jour locale</b> : une section recue salit sa tuile (et celles des voisines si elle est au bord de la
 * sienne : faces partagees) ; seules ces tuiles sont remaillees, par {@link AreaMeshWorker} (fil a part, sur une copie),
 * puis reprises ici au tick suivant. Un resultat d'une version perimee (tuile re-salie entre-temps) ou d'une ancienne
 * grille est jete.
 */
public final class AreaView
{
    /** Points de trainee gardes par entree. */
    public static final int TRAIL_POINTS = 64;
    /** Duree d'une trainee, en ticks (5 s). */
    public static final int TRAIL_TICKS = 100;
    /** Ticks entre deux positions recues (interpolation). */
    public static final float TRACK_PERIOD = 2f;

    /** Libere les ressources GPU d'une tuile (installe par le package de rendu ; inerte sur un serveur dedie). */
    private static Consumer<Tile> releaser = tile -> {};

    /** Branche la liberation des ressources GPU des tuiles (setup client). */
    public static void setReleaser(Consumer<Tile> releaser) { AreaView.releaser = releaser; }

    /**
     * Tuile : groupe de {@value AreaGrid#TILE_SECTIONS}^3 sections maillees, dessinee d'un bloc. Les quads sont en
     * voxels relatifs a l'origine de la tuile ({@link #originX()}...), solides et fluides separes ({@link AreaMesher}).
     * Les tableaux CPU ne servent qu'a l'envoi au GPU ({@link #uploaded()} les libere).
     */
    public static final class Tile
    {
        final int x;
        final int y;
        final int z;
        float[] solid;
        float[] fluid;
        int solidQuads;
        int fluidQuads;
        boolean uploadPending;
        Object gpu;

        Tile(int x, int y, int z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** Origine de la tuile dans la grille, en voxels. */
        public int originX() { return x * AreaMesher.TILE; }

        public int originY() { return y * AreaMesher.TILE; }

        public int originZ() { return z * AreaMesher.TILE; }

        /** Quads solides en attente d'envoi au GPU ({@code null} : aucun ou deja envoyes). */
        public float[] solid() { return solid; }

        /** Quads fluides en attente d'envoi au GPU. */
        public float[] fluid() { return fluid; }

        /** Quads de toute la tuile. */
        public int quads() { return solidQuads + fluidQuads; }

        public int solidQuads() { return solidQuads; }

        public int fluidQuads() { return fluidQuads; }

        /** Vrai si un nouveau maillage attend d'etre envoye au GPU. */
        public boolean needsUpload() { return uploadPending; }

        /** Les quads ont ete envoyes au GPU : les tableaux CPU sont liberes. */
        public void uploaded()
        {
            uploadPending = false;
            solid = null;
            fluid = null;
        }

        /** Ressources GPU de la tuile (posees et lues par le package de rendu). */
        public Object gpu() { return gpu; }

        public void setGpu(Object gpu) { this.gpu = gpu; }
    }

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
    AreaVoxels voxels;
    /** Tuiles qui ont au moins une face, par cle de tuile. */
    final Long2ObjectOpenHashMap<Tile> tiles = new Long2ObjectOpenHashMap<>();
    /** Memes tuiles en liste (parcours sans iterateur par le renderer). */
    final List<Tile> tileList = new ArrayList<>();
    /** Cles des tuiles a remailler, dans l'ordre de demande. */
    final LongLinkedOpenHashSet dirty = new LongLinkedOpenHashSet();
    /** Derniere version soumise au worker de chaque tuile en cours de calcul. */
    private final Long2IntOpenHashMap submitted = new Long2IntOpenHashMap();
    private int nextVersion;
    /** Change a chaque nouvelle grille : un resultat d'une ancienne grille est jete. */
    private int epoch;
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

    /** Nombre de tuiles a dessiner. */
    public int tileCount() { return tileList.size(); }

    /** Tuile {@code i} (quads des sections : voir {@link AreaMesher}). */
    public Tile tile(int i) { return tileList.get(i); }

    /** Nombre de positions suivies. */
    public int trackCount() { return trackList.size(); }

    /** Position suivie {@code i} (ordre de reception). */
    public Track track(int i) { return trackList.get(i); }

    void reset(AreaGrid grid)
    {
        this.grid = grid;
        releaseTiles();
        voxels = grid != null ? new AreaVoxels() : null;
        dirty.clear();
        submitted.clear();
        epoch++;
        tracks.clear();
        trackList.clear();
    }

    /** Fin de vie de la vue (miroir retire, deconnexion) : ressources GPU liberees. */
    void dispose()
    {
        releaseTiles();
        dirty.clear();
        submitted.clear();
        epoch++;
    }

    private void releaseTiles()
    {
        for (Tile tile : tileList) releaser.accept(tile);
        tiles.clear();
        tileList.clear();
    }

    /** Section {@code key} : sa tuile, et celles de ses six voisines si elle est au bord de la sienne. */
    void markDirty(long key)
    {
        int sx = AreaGrid.keyX(key);
        int sy = AreaGrid.keyY(key);
        int sz = AreaGrid.keyZ(key);
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
        dirty.add(AreaGrid.tileKey(AreaGrid.sectionKey(sx, sy, sz)));
    }

    /** Envoie au worker au plus {@code budget} tuiles sales (copie des voxels faite ici) ; rend le budget restant. */
    int submitSome(int budget)
    {
        for (LongIterator it = dirty.iterator(); it.hasNext() && budget > 0; budget--) {
            long tileKey = it.nextLong();
            it.remove();
            int version = ++nextVersion;
            submitted.put(tileKey, version);
            AreaMeshWorker.submit(this, epoch, tileKey, version, AreaMesher.snapshot(voxels, tileKey));
        }
        return budget;
    }

    /** Reprend le maillage d'une tuile calcule par le worker ({@code false} : resultat perime, jete). */
    boolean apply(int epoch, long tileKey, int version, AreaMesher.Result result)
    {
        if (epoch != this.epoch || submitted.get(tileKey) != version) return false;
        submitted.remove(tileKey);
        Tile tile = tiles.get(tileKey);
        boolean empty = result.solidQuads() + result.fluidQuads() == 0;
        if (tile == null) {
            if (empty) return true;
            tile = new Tile(AreaGrid.keyX(tileKey), AreaGrid.keyY(tileKey), AreaGrid.keyZ(tileKey));
            tiles.put(tileKey, tile);
            tileList.add(tile);
        } else if (empty) {
            releaser.accept(tile);
            tiles.remove(tileKey);
            tileList.remove(tile);
            return true;
        }
        tile.solid = result.solid();
        tile.fluid = result.fluid();
        tile.solidQuads = result.solidQuads();
        tile.fluidQuads = result.fluidQuads();
        tile.uploadPending = true;
        return true;
    }

    /** Vrai s'il reste des tuiles a remailler ou en cours de calcul. */
    boolean busy() { return !dirty.isEmpty() || !submitted.isEmpty(); }
}
