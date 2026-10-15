package com.amynna.hololib.client.modules.monde.areadisplayer;

import com.amynna.hololib.modules.monde.areadisplayer.AreaGrid;
import com.amynna.hololib.modules.monde.areadisplayer.AreaVoxels;

import java.util.Arrays;

/**
 * Maillage glouton d'une <b>tuile</b> de voxels (64 voxels de cote, client) : seules les faces visibles (voisin non
 * solide pour un solide, vide pour un fluide ; hors de la grille = vide), fusionnees en rectangles sur toute la tuile
 * par direction et par type (monochrome : aucune autre distinction), plusieurs voxels cote a cote ne donnant qu'une
 * face.
 * <p>
 * En deux temps : {@link #snapshot} (fil client) copie la tuile et une couche de 1 voxel de ses voisines dans un tableau
 * de {@value #PAD}^3 octets ; {@link #mesh} (pur, sans etat partage : utilisable sur un autre fil) calcule les quads de
 * cette copie. Resultat : {@link #FLOATS_PER_QUAD} floats par quad, quatre coins en unites de voxels <b>relatives a
 * l'origine de la tuile</b> (0 a 64 : pas de perte de precision sur une grande zone ; ordre anti-horaire vu de
 * l'exterieur : la face arriere est eliminee), puis le code {@code direction + 6 * type} ({@code type} 0 = solide,
 * 1 = fluide ; direction 0..5 = +X, -X, +Y, -Y, +Z, -Z). Les quads solides et fluides sont dans deux tableaux.
 */
public final class AreaMesher
{
    public static final int FLOATS_PER_QUAD = 13;
    /** Arete d'une tuile, en voxels. */
    public static final int TILE = AreaGrid.TILE_SECTIONS * AreaGrid.SECTION;
    /** Arete de la copie d'une tuile (une couche de voisines de chaque cote). */
    public static final int PAD = TILE + 2;

    private static final int[] STRIDE = {1, PAD * PAD, PAD};
    private static final int ORIGIN = STRIDE[0] + STRIDE[1] + STRIDE[2];

    /** Quads d'une tuile : {@code null} si aucune face de ce type. */
    public record Result(float[] solid, int solidQuads, float[] fluid, int fluidQuads) {}

    private final byte[] padded;
    private final int[] mask = new int[TILE * TILE];
    private float[] solid = new float[FLOATS_PER_QUAD * 256];
    private int solidCount;
    private float[] fluid = new float[FLOATS_PER_QUAD * 64];
    private int fluidCount;

    private AreaMesher(byte[] padded) { this.padded = padded; }

    /**
     * Copie la tuile {@code tileKey} et la couche de voisines qui l'entoure (fil client seulement : la table de
     * voxels n'est pas partagee).
     */
    public static byte[] snapshot(AreaVoxels voxels, long tileKey)
    {
        byte[] padded = new byte[PAD * PAD * PAD];
        int tx = AreaGrid.keyX(tileKey);
        int ty = AreaGrid.keyY(tileKey);
        int tz = AreaGrid.keyZ(tileKey);
        int s = AreaGrid.SECTION;
        for (int i = 0; i < AreaGrid.TILE_SECTIONS; i++) {
            for (int j = 0; j < AreaGrid.TILE_SECTIONS; j++) {
                for (int k = 0; k < AreaGrid.TILE_SECTIONS; k++) {
                    byte[] section = voxels.section(AreaGrid.sectionKey(tx * AreaGrid.TILE_SECTIONS + i,
                            ty * AreaGrid.TILE_SECTIONS + j, tz * AreaGrid.TILE_SECTIONS + k));
                    if (section == null) continue;
                    for (int y = 0; y < s; y++) {
                        for (int z = 0; z < s; z++) {
                            System.arraycopy(section, (y * s + z) * s, padded,
                                    index(i * s, j * s + y, k * s + z), s);
                        }
                    }
                }
            }
        }
        int ox = tx * TILE;
        int oy = ty * TILE;
        int oz = tz * TILE;
        // Couches voisines : les voxels de la tuile voisine qui touchent celle-ci.
        for (int a = 0; a < TILE; a++) {
            for (int b = 0; b < TILE; b++) {
                padded[index(-1, a, b)] = (byte) get(voxels, ox - 1, oy + a, oz + b);
                padded[index(TILE, a, b)] = (byte) get(voxels, ox + TILE, oy + a, oz + b);
                padded[index(a, -1, b)] = (byte) get(voxels, ox + a, oy - 1, oz + b);
                padded[index(a, TILE, b)] = (byte) get(voxels, ox + a, oy + TILE, oz + b);
                padded[index(a, b, -1)] = (byte) get(voxels, ox + a, oy + b, oz - 1);
                padded[index(a, b, TILE)] = (byte) get(voxels, ox + a, oy + b, oz + TILE);
            }
        }
        return padded;
    }

    private static int get(AreaVoxels voxels, int x, int y, int z)
    {
        return x < 0 || y < 0 || z < 0 ? AreaGrid.AIR : voxels.get(x, y, z);
    }

    private static int index(int x, int y, int z) { return ORIGIN + x * STRIDE[0] + y * STRIDE[1] + z * STRIDE[2]; }

    /** Quads de la copie {@code padded} d'une tuile ({@link #snapshot}). Sans etat partage : utilisable hors du fil client. */
    public static Result mesh(byte[] padded) { return new AreaMesher(padded).run(); }

    private Result run()
    {
        for (int axis = 0; axis < 3; axis++) {
            int ua = (axis + 1) % 3;
            int va = (axis + 2) % 3;
            for (int sign = 1; sign >= -1; sign -= 2) {
                int direction = axis * 2 + (sign > 0 ? 0 : 1);
                int step = sign * STRIDE[axis];
                for (int slice = 0; slice < TILE; slice++) {
                    boolean any = false;
                    int base = ORIGIN + slice * STRIDE[axis];
                    for (int v = 0; v < TILE; v++) {
                        int row = base + v * STRIDE[va];
                        for (int u = 0; u < TILE; u++) {
                            int at = row + u * STRIDE[ua];
                            int type = padded[at];
                            int face = 0;
                            if (type != AreaGrid.AIR) {
                                int neighbor = padded[at + step];
                                boolean visible = type == AreaGrid.SOLID ? neighbor != AreaGrid.SOLID
                                        : neighbor == AreaGrid.AIR;
                                if (visible) face = type == AreaGrid.SOLID ? 1 : 2;
                            }
                            mask[v * TILE + u] = face;
                            any |= face != 0;
                        }
                    }
                    if (!any) continue;
                    greedy(axis, ua, va, slice + (sign > 0 ? 1 : 0), sign, direction);
                }
            }
        }
        return new Result(solidCount == 0 ? null : Arrays.copyOf(solid, solidCount * FLOATS_PER_QUAD), solidCount,
                fluidCount == 0 ? null : Arrays.copyOf(fluid, fluidCount * FLOATS_PER_QUAD), fluidCount);
    }

    /** Fusionne les faces du masque en rectangles et les ecrit. */
    private void greedy(int axis, int ua, int va, float plane, int sign, int direction)
    {
        for (int v = 0; v < TILE; v++) {
            for (int u = 0; u < TILE; ) {
                int face = mask[v * TILE + u];
                if (face == 0) {
                    u++;
                    continue;
                }
                int width = 1;
                while (u + width < TILE && mask[v * TILE + u + width] == face) width++;
                int height = 1;
                grow:
                while (v + height < TILE) {
                    for (int k = 0; k < width; k++) if (mask[(v + height) * TILE + u + k] != face) break grow;
                    height++;
                }
                for (int h = 0; h < height; h++) Arrays.fill(mask, (v + h) * TILE + u, (v + h) * TILE + u + width, 0);
                quad(axis, ua, va, plane, u, v, width, height, sign, direction + 6 * (face - 1), face == 1);
                u += width;
            }
        }
    }

    private void quad(int axis, int ua, int va, float plane, float u0, float v0, float du, float dv, int sign,
                      int code, boolean isSolid)
    {
        float[] out;
        int at;
        if (isSolid) {
            if ((solidCount + 1) * FLOATS_PER_QUAD > solid.length) solid = Arrays.copyOf(solid, solid.length * 2);
            out = solid;
            at = solidCount++ * FLOATS_PER_QUAD;
        } else {
            if ((fluidCount + 1) * FLOATS_PER_QUAD > fluid.length) fluid = Arrays.copyOf(fluid, fluid.length * 2);
            out = fluid;
            at = fluidCount++ * FLOATS_PER_QUAD;
        }
        // Coins : base, +u, +u+v, +v (normale +axe) ; ordre inverse pour une face -axe.
        float u1 = u0 + du;
        float v1 = v0 + dv;
        for (int i = 0; i < 4; i++) {
            int corner = sign > 0 ? i : (4 - i) % 4;
            out[at + 3 * i + axis] = plane;
            out[at + 3 * i + ua] = corner == 0 || corner == 3 ? u0 : u1;
            out[at + 3 * i + va] = corner < 2 ? v0 : v1;
        }
        out[at + 12] = code;
    }
}
