package com.amynna.hololib.client.render.modules.dimension.space.warp;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Degrade arc-en-ciel du voyage (T35, livrable 3) : sphere opaque autour de l'oeil dont la couleur ne depend que de
 * l'angle {@code theta} a la cible (spectre etire autour du vaisseau, comme au-dela de la vitesse de la lumiere) :
 * <ul>
 *     <li>{@code theta <= }{@link #SPECTRUM_START} : violet (la source lumineuse s'y ajoute) ;</li>
 *     <li>de {@link #SPECTRUM_START} a {@link #SPECTRUM_END} : violet, bleu, cyan, vert, jaune, orange, rouge ;</li>
 *     <li>au-dela, vers l'arriere : le rouge s'assombrit jusqu'a {@link #BACK_RED} a 180 degres.</li>
 * </ul>
 * Des bandes de lumiere ({@link #BAND_AMPLITUDE}) defilent de la cible vers l'arriere : effet de vitesse. Maillage
 * polaire autour de la direction de la cible ({@value #RINGS} anneaux de {@code 180 / RINGS} degres x {@value #SECTORS}
 * secteurs), couleurs des anneaux recalculees a chaque frame dans des tableaux reutilises. La meme couleur (sans les
 * bandes) donne le brouillard ({@link #color}). Fil de rendu, sans allocation.
 */
final class SpaceWarpSpectrum
{
    // ---------------------------------------------------------------- reglages

    /** Angle a la cible (radians) ou commence le spectre (violet), et ou il finit (rouge) : parametrables. */
    static final double SPECTRUM_START = Math.toRadians(6.0);
    static final double SPECTRUM_END = Math.toRadians(150.0);
    /** Eclat general du degrade (la source et les flares sont plus lumineuses). */
    static final float BRIGHTNESS = 0.8f;
    /** Rouge sombre a l'arriere (180 degres). */
    private static final float[] BACK_RED = {0.22f, 0.02f, 0.03f};
    /**
     * Couleurs du spectre, reparties regulierement de {@link #SPECTRUM_START} a {@link #SPECTRUM_END} : violet, bleu,
     * cyan, vert, jaune, orange, rouge.
     */
    private static final float[][] STOPS = {
            {0.50f, 0.18f, 0.95f},
            {0.18f, 0.30f, 1.00f},
            {0.10f, 0.72f, 0.92f},
            {0.20f, 0.85f, 0.32f},
            {0.95f, 0.88f, 0.20f},
            {1.00f, 0.50f, 0.10f},
            {0.88f, 0.08f, 0.06f},
    };
    /** Bandes : amplitude relative de l'eclat, nombre sur 180 degres, vitesse (radians de phase par tick). */
    static final float BAND_AMPLITUDE = 0.12f;
    static final double BAND_COUNT = 6.0;
    static final double BAND_SPEED = 0.35;

    // ---------------------------------------------------------------- maillage

    static final int RINGS = 45;
    static final int SECTORS = 48;
    private static final float[] RING_COS = new float[RINGS + 1];
    private static final float[] RING_SIN = new float[RINGS + 1];
    private static final float[] SECTOR_COS = new float[SECTORS + 1];
    private static final float[] SECTOR_SIN = new float[SECTORS + 1];
    /** Couleur de base de chaque anneau (sans bandes). */
    private static final float[] BASE_R = new float[RINGS + 1];
    private static final float[] BASE_G = new float[RINGS + 1];
    private static final float[] BASE_B = new float[RINGS + 1];

    static
    {
        float[] rgb = new float[3];
        for (int k = 0; k <= RINGS; k++) {
            double theta = Math.PI * k / RINGS;
            RING_COS[k] = (float) Math.cos(theta);
            RING_SIN[k] = (float) Math.sin(theta);
            base(theta, rgb);
            BASE_R[k] = rgb[0];
            BASE_G[k] = rgb[1];
            BASE_B[k] = rgb[2];
        }
        for (int j = 0; j <= SECTORS; j++) {
            double angle = 2.0 * Math.PI * j / SECTORS;
            SECTOR_COS[j] = (float) Math.cos(angle);
            SECTOR_SIN[j] = (float) Math.sin(angle);
        }
    }

    /** Couleur des anneaux pour la frame (bandes comprises). */
    private final float[] ringR = new float[RINGS + 1];
    private final float[] ringG = new float[RINGS + 1];
    private final float[] ringB = new float[RINGS + 1];
    private final float[] work = new float[3];

    // ---------------------------------------------------------------- couleur

    /**
     * Couleur du degrade (sans bandes, eclat {@link #BRIGHTNESS} compris) dans la direction dont le cosinus de l'angle
     * a la cible est {@code cos} : brouillard accorde au degrade.
     */
    void color(float cos, Vector3f dest)
    {
        base(Math.acos(Math.max(-1f, Math.min(1f, cos))), work);
        dest.set(work[0], work[1], work[2]);
    }

    /** Couleur de base a l'angle {@code theta} (radians) de la cible, eclat compris. */
    private static void base(double theta, float[] dest)
    {
        float r, g, b;
        if (theta <= SPECTRUM_START) {
            float[] c = STOPS[0];
            r = c[0];
            g = c[1];
            b = c[2];
        } else if (theta < SPECTRUM_END) {
            double u = (theta - SPECTRUM_START) / (SPECTRUM_END - SPECTRUM_START) * (STOPS.length - 1);
            int i = Math.min(STOPS.length - 2, (int) u);
            float t = (float) (u - i);
            float[] a = STOPS[i], c = STOPS[i + 1];
            r = a[0] + (c[0] - a[0]) * t;
            g = a[1] + (c[1] - a[1]) * t;
            b = a[2] + (c[2] - a[2]) * t;
        } else {
            float[] a = STOPS[STOPS.length - 1];
            float t = (float) ((theta - SPECTRUM_END) / (Math.PI - SPECTRUM_END));
            t = t * (2f - t);
            r = a[0] + (BACK_RED[0] - a[0]) * t;
            g = a[1] + (BACK_RED[1] - a[1]) * t;
            b = a[2] + (BACK_RED[2] - a[2]) * t;
        }
        dest[0] = r * BRIGHTNESS;
        dest[1] = g * BRIGHTNESS;
        dest[2] = b * BRIGHTNESS;
    }

    // ---------------------------------------------------------------- dessin

    /**
     * Sphere du degrade de rayon {@code radius} centree sur l'oeil, pole sur la cible {@code w} (base {@code bu},
     * {@code bv} perpendiculaire), opaque, au temps {@code time} (ticks d'affichage, bandes).
     */
    void draw(VertexConsumer vc, SpaceWarpVertices out, Matrix4f m, Vector3fc w, Vector3fc bu, Vector3fc bv,
              float radius, double time)
    {
        for (int k = 0; k <= RINGS; k++) {
            double theta = Math.PI * k / RINGS;
            float band = 1f + BAND_AMPLITUDE * (float) Math.sin(BAND_COUNT * 2.0 * theta - BAND_SPEED * time);
            ringR[k] = Math.min(1f, BASE_R[k] * band);
            ringG[k] = Math.min(1f, BASE_G[k] * band);
            ringB[k] = Math.min(1f, BASE_B[k] * band);
        }
        for (int k = 0; k < RINGS; k++) {
            for (int j = 0; j < SECTORS; j++) {
                vertex(vc, out, m, w, bu, bv, radius, k, j);
                vertex(vc, out, m, w, bu, bv, radius, k + 1, j);
                vertex(vc, out, m, w, bu, bv, radius, k + 1, j + 1);
                vertex(vc, out, m, w, bu, bv, radius, k, j + 1);
            }
        }
    }

    private void vertex(VertexConsumer vc, SpaceWarpVertices out, Matrix4f m, Vector3fc w, Vector3fc bu,
                        Vector3fc bv, float radius, int ring, int sector)
    {
        float cos = RING_COS[ring];
        float c = SECTOR_COS[sector] * RING_SIN[ring];
        float s = SECTOR_SIN[sector] * RING_SIN[ring];
        out.put(vc, m, radius * (cos * w.x() + c * bu.x() + s * bv.x()),
                radius * (cos * w.y() + c * bu.y() + s * bv.y()),
                radius * (cos * w.z() + c * bu.z() + s * bv.z()), ringR[ring], ringG[ring], ringB[ring], 1f);
    }
}
