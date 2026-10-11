package com.amynna.hololib.modules.monde.areadisplayer;

import java.util.Arrays;

/**
 * Grille de voxels d'une zone de l'Area Displayer, calculee de facon deterministe des deux cotes a partir du coin
 * minimal, de la taille de la zone en blocs et de la resolution demandee (le serveur n'envoie que ces sept entiers).
 * <p>
 * <b>Resolution</b> (taille de zone illimitee) : un voxel couvre {@code step} blocs par axe. Demande {@link #AUTO} :
 * {@code step} = plus petit entier tel que le nombre de voxels ne depasse pas {@link #MAX_VOXELS} ({@code 128^3}) ;
 * une zone de {@code 128^3} blocs ou moins est donc rendue bloc par bloc. Demande d'un palier de {@link #LEVELS}
 * ({@code 1} = bloc par bloc) : premier palier superieur ou egal qui tient dans {@link #HARD_MAX_VOXELS}
 * ({@code 256^3}), a defaut le plus petit pas qui y tient ; {@link #raised()} dit si le pas a ete releve. Le dernier
 * voxel d'un axe peut deborder de la zone (il ne compte que les blocs de la zone).
 * <p>
 * <b>Sections</b> : cubes de {@value #SECTION} voxels de cote, unite d'envoi et de maillage ; indexees
 * {@code (sy * sectionsZ + sz) * sectionsX + sx}. Voxel {@code (x, y, z)} a l'indice {@code (y * sizeZ + z) * sizeX +
 * x}. Valeurs : {@link #AIR}, {@link #SOLID}, {@link #FLUID} (2 bits sur le reseau).
 */
public final class AreaGrid
{
    /** Nombre maximal de voxels d'une grille en resolution {@link #AUTO}. */
    public static final long MAX_VOXELS = 128L * 128L * 128L;
    /** Nombre maximal de voxels d'une grille a resolution choisie (palier). */
    public static final long HARD_MAX_VOXELS = 256L * 256L * 256L;
    /** Resolution automatique. */
    public static final int AUTO = 0;
    /** Paliers de resolution proposes, en blocs par voxel ({@code 1} = bloc par bloc). */
    public static final int[] LEVELS = {1, 2, 4, 8, 16, 32};
    /** Arete d'une section, en voxels. */
    public static final int SECTION = 16;
    /** Voxels d'une section (completee par du vide au bord de la grille). */
    public static final int SECTION_VOXELS = SECTION * SECTION * SECTION;
    /** Octets d'une section empaquetee (2 bits par voxel). */
    public static final int PACKED_BYTES = SECTION_VOXELS / 4;

    public static final byte AIR = 0;
    public static final byte SOLID = 1;
    public static final byte FLUID = 2;

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int blocksX;
    private final int blocksY;
    private final int blocksZ;
    private final int requestedStep;
    private final int step;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int sectionsX;
    private final int sectionsY;
    private final int sectionsZ;

    /**
     * @param blocksX       taille de la zone en blocs (au moins 1 par axe)
     * @param requestedStep {@link #AUTO} ou un palier de {@link #LEVELS} (autre valeur : Auto)
     */
    public AreaGrid(int minX, int minY, int minZ, int blocksX, int blocksY, int blocksZ, int requestedStep)
    {
        this.requestedStep = isLevel(requestedStep) ? requestedStep : AUTO;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.blocksX = Math.max(1, blocksX);
        this.blocksY = Math.max(1, blocksY);
        this.blocksZ = Math.max(1, blocksZ);
        this.step = this.requestedStep == AUTO ? stepFor(this.blocksX, this.blocksY, this.blocksZ, MAX_VOXELS)
                : levelStep(this.blocksX, this.blocksY, this.blocksZ, this.requestedStep);
        this.sizeX = ceilDiv(this.blocksX, step);
        this.sizeY = ceilDiv(this.blocksY, step);
        this.sizeZ = ceilDiv(this.blocksZ, step);
        this.sectionsX = ceilDiv(sizeX, SECTION);
        this.sectionsY = ceilDiv(sizeY, SECTION);
        this.sectionsZ = ceilDiv(sizeZ, SECTION);
    }

    /** Vrai pour {@link #AUTO} ou un palier de {@link #LEVELS}. */
    public static boolean isLevel(int step)
    {
        if (step == AUTO) return true;
        for (int level : LEVELS) if (level == step) return true;
        return false;
    }

    /** Plus petit pas tel que la grille tienne dans {@code max} voxels. */
    static int stepFor(long bx, long by, long bz, long max)
    {
        double ideal = Math.cbrt((double) bx * by * bz / max);
        int step = Math.max(1, (int) Math.floor(ideal));
        while (voxels(bx, by, bz, step) > max) step++;
        return step;
    }

    /** Premier palier {@code >= requested} qui tient dans {@link #HARD_MAX_VOXELS}, sinon le plus petit pas qui y tient. */
    private static int levelStep(long bx, long by, long bz, int requested)
    {
        for (int level : LEVELS) {
            if (level >= requested && voxels(bx, by, bz, level) <= HARD_MAX_VOXELS) return level;
        }
        return Math.max(requested, stepFor(bx, by, bz, HARD_MAX_VOXELS));
    }

    private static long voxels(long bx, long by, long bz, int step)
    {
        return (long) ceilDiv(bx, step) * ceilDiv(by, step) * ceilDiv(bz, step);
    }

    private static int ceilDiv(long a, long b) { return (int) ((a + b - 1) / b); }

    public int minX() { return minX; }

    public int minY() { return minY; }

    public int minZ() { return minZ; }

    public int blocksX() { return blocksX; }

    public int blocksY() { return blocksY; }

    public int blocksZ() { return blocksZ; }

    /** Resolution demandee : {@link #AUTO} ou un palier de {@link #LEVELS}. */
    public int requestedStep() { return requestedStep; }

    /** Blocs par voxel et par axe (resolution effective). */
    public int step() { return step; }

    /** Vrai si le palier demande a ete releve (zone trop grande pour lui). */
    public boolean raised() { return requestedStep != AUTO && step != requestedStep; }

    public int sizeX() { return sizeX; }

    public int sizeY() { return sizeY; }

    public int sizeZ() { return sizeZ; }

    public int voxels() { return sizeX * sizeY * sizeZ; }

    public int sectionsX() { return sectionsX; }

    public int sectionsY() { return sectionsY; }

    public int sectionsZ() { return sectionsZ; }

    public int sections() { return sectionsX * sectionsY * sectionsZ; }

    /** Plus grand nombre de voxels sur un axe (l'axe le plus long occupe tout le pave). */
    public int maxSize() { return Math.max(sizeX, Math.max(sizeY, sizeZ)); }

    public int index(int x, int y, int z) { return (y * sizeZ + z) * sizeX + x; }

    public int sectionIndex(int sx, int sy, int sz) { return (sy * sectionsZ + sz) * sectionsX + sx; }

    public int sectionX(int section) { return section % sectionsX; }

    public int sectionZ(int section) { return section / sectionsX % sectionsZ; }

    public int sectionY(int section) { return section / (sectionsX * sectionsZ); }

    /** Meme zone (coin et taille) et meme resolution demandee : la grille est alors identique. */
    public boolean sameZone(AreaGrid other)
    {
        return other != null && other.minX == minX && other.minY == minY && other.minZ == minZ
                && other.blocksX == blocksX && other.blocksY == blocksY && other.blocksZ == blocksZ
                && other.requestedStep == requestedStep;
    }

    /**
     * Empaquette la section {@code section} de {@code voxels} (2 bits par voxel, ordre {@code y, z, x} de la section ;
     * hors de la grille : vide) dans {@code out} ({@link #PACKED_BYTES} octets).
     *
     * @return la valeur commune si la section est uniforme, sinon {@code -1}
     */
    public int pack(byte[] voxels, int section, byte[] out)
    {
        int ox = sectionX(section) * SECTION;
        int oy = sectionY(section) * SECTION;
        int oz = sectionZ(section) * SECTION;
        int first = -2;
        boolean uniform = true;
        int bit = 0;
        Arrays.fill(out, 0, PACKED_BYTES, (byte) 0);
        for (int y = 0; y < SECTION; y++) {
            for (int z = 0; z < SECTION; z++) {
                for (int x = 0; x < SECTION; x++, bit += 2) {
                    int gx = ox + x;
                    int gy = oy + y;
                    int gz = oz + z;
                    int value = gx < sizeX && gy < sizeY && gz < sizeZ ? voxels[index(gx, gy, gz)] & 3 : 0;
                    if (first == -2) first = value;
                    else if (value != first) uniform = false;
                    out[bit >> 3] |= (byte) (value << (bit & 7));
                }
            }
        }
        return uniform ? first : -1;
    }

    /** Inverse de {@link #pack} : ecrit la section dans {@code voxels} ({@code packed == null} : valeur uniforme). */
    public void unpack(byte[] packed, int uniform, int section, byte[] voxels)
    {
        int ox = sectionX(section) * SECTION;
        int oy = sectionY(section) * SECTION;
        int oz = sectionZ(section) * SECTION;
        int bit = 0;
        for (int y = 0; y < SECTION; y++) {
            for (int z = 0; z < SECTION; z++) {
                for (int x = 0; x < SECTION; x++, bit += 2) {
                    int gx = ox + x;
                    int gy = oy + y;
                    int gz = oz + z;
                    if (gx >= sizeX || gy >= sizeY || gz >= sizeZ) continue;
                    int value = packed == null ? uniform : packed[bit >> 3] >> (bit & 7) & 3;
                    voxels[index(gx, gy, gz)] = (byte) (value == 3 ? AIR : value);
                }
            }
        }
    }

    /** Vrai si la section ne contient que du vide. */
    public boolean sectionEmpty(byte[] voxels, int section)
    {
        int ox = sectionX(section) * SECTION;
        int oy = sectionY(section) * SECTION;
        int oz = sectionZ(section) * SECTION;
        int ex = Math.min(sizeX, ox + SECTION);
        int ey = Math.min(sizeY, oy + SECTION);
        int ez = Math.min(sizeZ, oz + SECTION);
        for (int y = oy; y < ey; y++) {
            for (int z = oz; z < ez; z++) {
                int base = index(0, y, z);
                for (int x = ox; x < ex; x++) if (voxels[base + x] != AIR) return false;
            }
        }
        return true;
    }
}
