package com.amynna.hololib.modules.monde.areadisplayer;

import java.util.Arrays;

/**
 * Grille de voxels d'une zone de l'Area Displayer, calculee de facon deterministe des deux cotes a partir du coin
 * minimal, de la taille de la zone en blocs et de la resolution (le serveur n'envoie que ces sept entiers). Elle ne
 * contient pas les voxels : ils sont tenus par {@link AreaVoxels}, section par section.
 * <p>
 * <b>Resolution</b> : un voxel couvre {@code step} blocs par axe, <b>strictement</b> (un palier de {@link #LEVELS},
 * {@code 1} = bloc par bloc) ; la grille n'a aucun plafond de taille, seul le stockage creux borne la memoire. Le
 * dernier voxel d'un axe peut deborder de la zone (il ne compte que les blocs de la zone).
 * <p>
 * <b>Sections</b> : cubes de {@value #SECTION} voxels de cote, unite de stockage, d'envoi et de maillage ; identifiees
 * par une cle {@code long} ({@link #sectionKey}). Dans une section, le voxel {@code (x, y, z)} est a l'indice
 * {@code (y * 16 + z) * 16 + x}. <b>Tuiles</b> : groupes de {@value #TILE_SECTIONS}^3 sections (64 voxels de cote,
 * 4 x 4 x 4 chunks a 1:1), unite de culling et de dessin cote client ({@link #tileKey}). Valeurs : {@link #AIR},
 * {@link #SOLID}, {@link #FLUID} (2 bits sur le reseau).
 */
public final class AreaGrid
{
    /** Paliers de resolution proposes, en blocs par voxel ({@code 1} = bloc par bloc). */
    public static final int[] LEVELS = {1, 2, 4, 8, 16, 32};
    /** Resolution par defaut (bloc par bloc). */
    public static final int DEFAULT_STEP = 1;
    /** Arete d'une section, en voxels. */
    public static final int SECTION = 16;
    /** Voxels d'une section. */
    public static final int SECTION_VOXELS = SECTION * SECTION * SECTION;
    /** Octets d'une section empaquetee (2 bits par voxel). */
    public static final int PACKED_BYTES = SECTION_VOXELS / 4;
    /** Arete d'une tuile, en sections. */
    public static final int TILE_SECTIONS = 4;
    /** Sections d'une tuile. */
    public static final int TILE_VOLUME = TILE_SECTIONS * TILE_SECTIONS * TILE_SECTIONS;

    public static final byte AIR = 0;
    public static final byte SOLID = 1;
    public static final byte FLUID = 2;

    private static final int KEY_X_BITS = 26;
    private static final int KEY_Z_BITS = 26;
    private static final long KEY_X_MASK = (1L << KEY_X_BITS) - 1;
    private static final long KEY_Z_MASK = (1L << KEY_Z_BITS) - 1;
    private static final int KEY_Y_MASK = (1 << 12) - 1;

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int blocksX;
    private final int blocksY;
    private final int blocksZ;
    private final int step;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int sectionsX;
    private final int sectionsY;
    private final int sectionsZ;

    /**
     * @param blocksX taille de la zone en blocs (au moins 1 par axe)
     * @param step    palier de {@link #LEVELS} (autre valeur : {@link #DEFAULT_STEP})
     */
    public AreaGrid(int minX, int minY, int minZ, int blocksX, int blocksY, int blocksZ, int step)
    {
        this.step = isLevel(step) ? step : DEFAULT_STEP;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.blocksX = Math.max(1, blocksX);
        this.blocksY = Math.max(1, blocksY);
        this.blocksZ = Math.max(1, blocksZ);
        this.sizeX = ceilDiv(this.blocksX, this.step);
        this.sizeY = ceilDiv(this.blocksY, this.step);
        this.sizeZ = ceilDiv(this.blocksZ, this.step);
        this.sectionsX = ceilDiv(sizeX, SECTION);
        this.sectionsY = ceilDiv(sizeY, SECTION);
        this.sectionsZ = ceilDiv(sizeZ, SECTION);
    }

    /** Vrai pour un palier de {@link #LEVELS}. */
    public static boolean isLevel(int step)
    {
        for (int level : LEVELS) if (level == step) return true;
        return false;
    }

    private static int ceilDiv(int a, int b) { return (int) (((long) a + b - 1) / b); }

    public int minX() { return minX; }

    public int minY() { return minY; }

    public int minZ() { return minZ; }

    public int blocksX() { return blocksX; }

    public int blocksY() { return blocksY; }

    public int blocksZ() { return blocksZ; }

    /** Blocs par voxel et par axe (la resolution choisie, jamais modifiee). */
    public int step() { return step; }

    public int sizeX() { return sizeX; }

    public int sizeY() { return sizeY; }

    public int sizeZ() { return sizeZ; }

    public int sectionsX() { return sectionsX; }

    public int sectionsY() { return sectionsY; }

    public int sectionsZ() { return sectionsZ; }

    /** Nombre de sections de la grille (vides comprises). */
    public long sections() { return (long) sectionsX * sectionsY * sectionsZ; }

    /** Nombre de colonnes de sections (X, Z). */
    public long columns() { return (long) sectionsX * sectionsZ; }

    /** Plus grand nombre de voxels sur un axe (l'axe le plus long occupe tout le pave). */
    public int maxSize() { return Math.max(sizeX, Math.max(sizeY, sizeZ)); }

    /** Nombre de tuiles de la grille. */
    public long tiles()
    {
        return (long) ceilDiv(sectionsX, TILE_SECTIONS) * ceilDiv(sectionsY, TILE_SECTIONS)
                * ceilDiv(sectionsZ, TILE_SECTIONS);
    }

    // ---------------------------------------------------------------- cles

    /** Cle d'une section : {@code sx} sur 26 bits, {@code sz} sur 26 bits, {@code sy} sur 12 bits. */
    public static long sectionKey(int sx, int sy, int sz)
    {
        return (sx & KEY_X_MASK) | (sz & KEY_Z_MASK) << KEY_X_BITS
                | (long) (sy & KEY_Y_MASK) << (KEY_X_BITS + KEY_Z_BITS);
    }

    public static int keyX(long key) { return (int) (key & KEY_X_MASK); }

    public static int keyZ(long key) { return (int) (key >> KEY_X_BITS & KEY_Z_MASK); }

    public static int keyY(long key) { return (int) (key >> (KEY_X_BITS + KEY_Z_BITS) & KEY_Y_MASK); }

    /** Vrai si la cle designe une section de la grille. */
    public boolean contains(long key)
    {
        return keyX(key) < sectionsX && keyY(key) < sectionsY && keyZ(key) < sectionsZ;
    }

    /** Cle de la tuile qui contient la section {@code key}. */
    public static long tileKey(long key)
    {
        return sectionKey(keyX(key) / TILE_SECTIONS, keyY(key) / TILE_SECTIONS, keyZ(key) / TILE_SECTIONS);
    }

    /** Position de la section {@code key} dans sa tuile, de 0 a {@link #TILE_VOLUME}. */
    public static int slotInTile(long key)
    {
        return ((keyY(key) % TILE_SECTIONS) * TILE_SECTIONS + keyZ(key) % TILE_SECTIONS) * TILE_SECTIONS
                + keyX(key) % TILE_SECTIONS;
    }

    /** Position {@code slot} (voir {@link #slotInTile}) en sections relatives a la tuile. */
    public static int slotX(int slot) { return slot % TILE_SECTIONS; }

    public static int slotZ(int slot) { return slot / TILE_SECTIONS % TILE_SECTIONS; }

    public static int slotY(int slot) { return slot / (TILE_SECTIONS * TILE_SECTIONS); }

    /** Meme zone (coin et taille) et meme resolution : la grille est alors identique. */
    public boolean sameZone(AreaGrid other)
    {
        return other != null && other.minX == minX && other.minY == minY && other.minZ == minZ
                && other.blocksX == blocksX && other.blocksY == blocksY && other.blocksZ == blocksZ
                && other.step == step;
    }

    // ---------------------------------------------------------------- empaquetage

    /** Indice d'un voxel dans sa section. */
    public static int localIndex(int x, int y, int z) { return (y * SECTION + z) * SECTION + x; }

    /**
     * Empaquette une section ({@link #SECTION_VOXELS} octets, {@code null} = vide) a 2 bits par voxel dans
     * {@code out} ({@link #PACKED_BYTES} octets).
     *
     * @return la valeur commune si la section est uniforme, sinon {@code -1}
     */
    public static int pack(byte[] section, byte[] out)
    {
        if (section == null) return AIR;
        int first = section[0] & 3;
        boolean uniform = true;
        Arrays.fill(out, 0, PACKED_BYTES, (byte) 0);
        for (int i = 0; i < SECTION_VOXELS; i++) {
            int value = section[i] & 3;
            if (value != first) uniform = false;
            out[i >> 2] |= (byte) (value << ((i & 3) << 1));
        }
        return uniform ? first : -1;
    }

    /**
     * Inverse de {@link #pack} : ecrit la section dans {@code out} ({@link #SECTION_VOXELS} octets ;
     * {@code packed == null} : valeur uniforme {@code uniform}).
     */
    public static void unpack(byte[] packed, int uniform, byte[] out)
    {
        if (packed == null) {
            Arrays.fill(out, 0, SECTION_VOXELS, (byte) (uniform == 3 ? AIR : uniform));
            return;
        }
        for (int i = 0; i < SECTION_VOXELS; i++) {
            int value = packed[i >> 2] >> ((i & 3) << 1) & 3;
            out[i] = (byte) (value == 3 ? AIR : value);
        }
    }
}
