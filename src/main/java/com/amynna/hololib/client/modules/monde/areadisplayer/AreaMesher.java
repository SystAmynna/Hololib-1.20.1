package com.amynna.hololib.client.modules.monde.areadisplayer;

import com.amynna.hololib.modules.monde.areadisplayer.AreaGrid;

import java.util.Arrays;

/**
 * Maillage glouton d'une section de voxels (client, fil client) : seules les faces visibles (voisin non solide pour
 * un solide, vide pour un fluide ; hors de la grille = vide), fusionnees en rectangles par direction et par type
 * (monochrome : aucune autre distinction). Resultat : {@link #FLOATS_PER_QUAD} floats par quad, quatre coins en
 * unites de voxels de la grille (ordre anti-horaire vu de l'exterieur : la face arriere est eliminee), puis le code
 * {@code direction + 6 * type} ({@code type} 0 = solide, 1 = fluide ; direction 0..5 = +X, -X, +Y, -Y, +Z, -Z).
 */
public final class AreaMesher
{
    public static final int FLOATS_PER_QUAD = 13;

    private static final int S = AreaGrid.SECTION;

    /** Tampons reutilises (fil client seulement). */
    private static final int[] MASK = new int[S * S];
    private static float[] out = new float[FLOATS_PER_QUAD * 256];
    private static int count;
    private static final int[] ORIGIN = new int[3];
    private static final int[] POS = new int[3];
    private static final float[] US = new float[4];
    private static final float[] VS = new float[4];

    private AreaMesher() {}

    /** Quads de la section {@code section}, ou {@code null} si elle n'a aucune face visible. */
    public static float[] mesh(AreaGrid grid, byte[] voxels, int section)
    {
        count = 0;
        int ox = grid.sectionX(section) * S;
        int oy = grid.sectionY(section) * S;
        int oz = grid.sectionZ(section) * S;
        int[] origin = ORIGIN;
        origin[0] = ox;
        origin[1] = oy;
        origin[2] = oz;
        int[] pos = POS;
        for (int axis = 0; axis < 3; axis++) {
            int ua = (axis + 1) % 3;
            int va = (axis + 2) % 3;
            for (int sign = 1; sign >= -1; sign -= 2) {
                int direction = axis * 2 + (sign > 0 ? 0 : 1);
                for (int slice = 0; slice < S; slice++) {
                    // Masque des faces visibles de la tranche : type + 1, 0 = aucune.
                    boolean any = false;
                    for (int v = 0; v < S; v++) {
                        for (int u = 0; u < S; u++) {
                            pos[axis] = origin[axis] + slice;
                            pos[ua] = origin[ua] + u;
                            pos[va] = origin[va] + v;
                            int type = at(grid, voxels, pos[0], pos[1], pos[2]);
                            int face = 0;
                            if (type != AreaGrid.AIR) {
                                pos[axis] += sign;
                                int neighbor = at(grid, voxels, pos[0], pos[1], pos[2]);
                                boolean visible = type == AreaGrid.SOLID ? neighbor != AreaGrid.SOLID
                                        : neighbor == AreaGrid.AIR;
                                if (visible) face = type == AreaGrid.SOLID ? 1 : 2;
                            }
                            MASK[v * S + u] = face;
                            any |= face != 0;
                        }
                    }
                    if (!any) continue;
                    float plane = origin[axis] + slice + (sign > 0 ? 1 : 0);
                    greedy(axis, ua, va, origin[ua], origin[va], plane, sign, direction);
                }
            }
        }
        return count == 0 ? null : Arrays.copyOf(out, count * FLOATS_PER_QUAD);
    }

    private static int at(AreaGrid grid, byte[] voxels, int x, int y, int z)
    {
        if (x < 0 || y < 0 || z < 0 || x >= grid.sizeX() || y >= grid.sizeY() || z >= grid.sizeZ()) {
            return AreaGrid.AIR;
        }
        return voxels[grid.index(x, y, z)];
    }

    /** Fusionne les faces du masque en rectangles et les ecrit. */
    private static void greedy(int axis, int ua, int va, int ou, int ov, float plane, int sign, int direction)
    {
        for (int v = 0; v < S; v++) {
            for (int u = 0; u < S; ) {
                int face = MASK[v * S + u];
                if (face == 0) {
                    u++;
                    continue;
                }
                int width = 1;
                while (u + width < S && MASK[v * S + u + width] == face) width++;
                int height = 1;
                grow:
                while (v + height < S) {
                    for (int k = 0; k < width; k++) if (MASK[(v + height) * S + u + k] != face) break grow;
                    height++;
                }
                for (int h = 0; h < height; h++) Arrays.fill(MASK, (v + h) * S + u, (v + h) * S + u + width, 0);
                quad(axis, ua, va, plane, ou + u, ov + v, width, height, sign, direction + 6 * (face - 1));
                u += width;
            }
        }
    }

    private static void quad(int axis, int ua, int va, float plane, float u0, float v0, float du, float dv, int sign,
                             int code)
    {
        if ((count + 1) * FLOATS_PER_QUAD > out.length) out = Arrays.copyOf(out, out.length * 2);
        int at = count * FLOATS_PER_QUAD;
        // Coins : base, +u, +u+v, +v (normale +axe) ; ordre inverse pour une face -axe.
        float[] us = US;
        float[] vs = VS;
        us[0] = u0;
        us[1] = u0 + du;
        us[2] = u0 + du;
        us[3] = u0;
        vs[0] = v0;
        vs[1] = v0;
        vs[2] = v0 + dv;
        vs[3] = v0 + dv;
        for (int i = 0; i < 4; i++) {
            int corner = sign > 0 ? i : (4 - i) % 4;
            out[at + 3 * i + axis] = plane;
            out[at + 3 * i + ua] = us[corner];
            out[at + 3 * i + va] = vs[corner];
        }
        out[at + 12] = code;
        count++;
    }
}
