package com.amynna.hololib.client.render.modules.camera.radar;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Sphere de l'onde dans l'air : icosaedre subdivise {@value #SUBDIVISIONS} fois (1 280 triangles), sommets et centres
 * unitaires precalcules. Chaque triangle est dessine en quads : une face tres translucide (triangle retreci vers son
 * centre, quad degenere) et trois bandes d'arete plus lumineuses entre le triangle et sa version retrecie : la sphere
 * parait decoupee en triangles sans lignes ({@code RenderType.lines()} ecrit dans une autre cible en Fabulous).
 * <p>
 * La largeur des bandes est donnee en blocs (et non en part du triangle) : le retrecissement est recalcule a chaque
 * dessin ({@code 1 - largeur / (rayon x rayon inscrit)}, borne a {@value #MIN_INNER}), sans allocation ; une largeur
 * nulle ne dessine que les faces.
 */
final class RadarSphere
{
    private static final int SUBDIVISIONS = 3;
    /** Part minimale du triangle conservee pour la face (bandes d'arete au plus larges). */
    private static final float MIN_INNER = 0.5f;

    /** Triangles : 9 flottants chacun (3 sommets unitaires). */
    private static final float[] TRIANGLES = build();
    /** Centre de chaque triangle : 3 flottants chacun. */
    private static final float[] CENTROIDS = centroids(TRIANGLES);
    /** Rayon inscrit moyen d'un triangle de la sphere unitaire. */
    private static final float INRADIUS = inradius(TRIANGLES);

    private RadarSphere() {}

    /**
     * Emet la sphere de rayon {@code radius} centree sur l'origine de {@code pose} ({@code POSITION_COLOR}, quads).
     *
     * @param edgeWidth largeur des bandes d'arete, en blocs ; 0 : faces seules
     * @param faceArgb  couleur des faces
     * @param edgeArgb  couleur des aretes
     */
    static void draw(Matrix4f pose, VertexConsumer consumer, float radius, float edgeWidth, int faceArgb,
                     int edgeArgb)
    {
        int fr = faceArgb >> 16 & 0xFF, fg = faceArgb >> 8 & 0xFF, fb = faceArgb & 0xFF, fa = faceArgb >>> 24;
        int er = edgeArgb >> 16 & 0xFF, eg = edgeArgb >> 8 & 0xFF, eb = edgeArgb & 0xFF, ea = edgeArgb >>> 24;
        boolean edges = edgeWidth > 0f && ea > 0;
        float inner = edges ? Math.max(MIN_INNER, 1f - edgeWidth / (radius * INRADIUS)) : 1f;
        float[] t = TRIANGLES;
        for (int i = 0, c = 0; i < t.length; i += 9, c += 3) {
            // Face : triangle retreci, en quad degenere.
            if (fa > 0) {
                shrunk(consumer, pose, i, c, inner, radius, fr, fg, fb, fa);
                shrunk(consumer, pose, i + 3, c, inner, radius, fr, fg, fb, fa);
                shrunk(consumer, pose, i + 6, c, inner, radius, fr, fg, fb, fa);
                shrunk(consumer, pose, i + 6, c, inner, radius, fr, fg, fb, fa);
            }
            if (!edges) continue;
            // Aretes : bande entre chaque cote et le cote retreci correspondant.
            for (int k = 0; k < 3; k++) {
                int a = i + 3 * k;
                int b = i + 3 * ((k + 1) % 3);
                shrunk(consumer, pose, a, c, 1f, radius, er, eg, eb, ea);
                shrunk(consumer, pose, b, c, 1f, radius, er, eg, eb, ea);
                shrunk(consumer, pose, b, c, inner, radius, er, eg, eb, ea);
                shrunk(consumer, pose, a, c, inner, radius, er, eg, eb, ea);
            }
        }
    }

    /** Sommet {@code index} rapproche du centre {@code center} de son triangle ({@code k} = 1 : sommet d'origine). */
    private static void shrunk(VertexConsumer consumer, Matrix4f pose, int index, int center, float k, float radius,
                               int r, int g, int b, int a)
    {
        float[] t = TRIANGLES;
        float[] c = CENTROIDS;
        float x = (c[center] + (t[index] - c[center]) * k) * radius;
        float y = (c[center + 1] + (t[index + 1] - c[center + 1]) * k) * radius;
        float z = (c[center + 2] + (t[index + 2] - c[center + 2]) * k) * radius;
        consumer.vertex(pose, x, y, z).color(r, g, b, a).endVertex();
    }

    // ---------------------------------------------------------------- construction

    private static float[] build()
    {
        float p = (float) ((1.0 + Math.sqrt(5.0)) / 2.0);
        float[][] v = {
                {-1, p, 0}, {1, p, 0}, {-1, -p, 0}, {1, -p, 0},
                {0, -1, p}, {0, 1, p}, {0, -1, -p}, {0, 1, -p},
                {p, 0, -1}, {p, 0, 1}, {-p, 0, -1}, {-p, 0, 1}
        };
        int[][] faces = {
                {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
                {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
                {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}
        };
        float[] out = new float[faces.length * (1 << 2 * SUBDIVISIONS) * 9];
        int[] cursor = {0};
        for (int[] f : faces) {
            subdivide(normalize(v[f[0]]), normalize(v[f[1]]), normalize(v[f[2]]), SUBDIVISIONS, out, cursor);
        }
        return out;
    }

    private static void subdivide(float[] a, float[] b, float[] c, int depth, float[] out, int[] cursor)
    {
        if (depth == 0) {
            int i = cursor[0];
            System.arraycopy(a, 0, out, i, 3);
            System.arraycopy(b, 0, out, i + 3, 3);
            System.arraycopy(c, 0, out, i + 6, 3);
            cursor[0] = i + 9;
            return;
        }
        float[] ab = normalize(mid(a, b));
        float[] bc = normalize(mid(b, c));
        float[] ca = normalize(mid(c, a));
        subdivide(a, ab, ca, depth - 1, out, cursor);
        subdivide(ab, b, bc, depth - 1, out, cursor);
        subdivide(ca, bc, c, depth - 1, out, cursor);
        subdivide(ab, bc, ca, depth - 1, out, cursor);
    }

    private static float[] mid(float[] a, float[] b)
    {
        return new float[]{(a[0] + b[0]) / 2f, (a[1] + b[1]) / 2f, (a[2] + b[2]) / 2f};
    }

    private static float[] normalize(float[] v)
    {
        float length = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new float[]{v[0] / length, v[1] / length, v[2] / length};
    }

    private static float[] centroids(float[] triangles)
    {
        float[] out = new float[triangles.length / 3];
        for (int i = 0, c = 0; i < triangles.length; i += 9, c += 3) {
            for (int axis = 0; axis < 3; axis++) {
                out[c + axis] = (triangles[i + axis] + triangles[i + 3 + axis] + triangles[i + 6 + axis]) / 3f;
            }
        }
        return out;
    }

    /** Rayon inscrit moyen : {@code 2 x aire / perimetre} par triangle. */
    private static float inradius(float[] t)
    {
        double sum = 0.0;
        for (int i = 0; i < t.length; i += 9) {
            double ux = t[i + 3] - t[i], uy = t[i + 4] - t[i + 1], uz = t[i + 5] - t[i + 2];
            double vx = t[i + 6] - t[i], vy = t[i + 7] - t[i + 1], vz = t[i + 8] - t[i + 2];
            double wx = t[i + 6] - t[i + 3], wy = t[i + 7] - t[i + 4], wz = t[i + 8] - t[i + 5];
            double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
            double area = Math.sqrt(cx * cx + cy * cy + cz * cz) / 2.0;
            double perimeter = Math.sqrt(ux * ux + uy * uy + uz * uz) + Math.sqrt(vx * vx + vy * vy + vz * vz)
                    + Math.sqrt(wx * wx + wy * wy + wz * wz);
            sum += 2.0 * area / perimeter;
        }
        return (float) (sum / (t.length / 9));
    }
}
