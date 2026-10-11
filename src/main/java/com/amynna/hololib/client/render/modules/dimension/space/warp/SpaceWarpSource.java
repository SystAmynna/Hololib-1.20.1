package com.amynna.hololib.client.render.modules.dimension.space.warp;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

/**
 * Source lumineuse de la cible pendant le voyage (T35, livrable 3) : grosse source lointaine au point de fuite, faite
 * d'un noyau blanc et de halos concentriques (disques au bord doux et anneaux fins), plus deux flares a quatre branches
 * un peu floues, superposees, qui tournent sur elles-memes en sens opposes. Tout est additif
 * ({@link SpaceWarpTypes#ADD}) et dessine dans le plan tangent a la sphere du ciel au point de fuite, a la distance
 * {@code radius} (coordonnees en tangentes d'angle : {@code p = radius (w + x bu + y bv)}).
 * <p>
 * Reglages ci-dessous (angles en degres) ; les opacites pulsent legerement ({@link #PULSE}) et suivent l'apparition de
 * la scene ({@code ramp}, sous le flash d'entree), les flares s'allongent avec elle. Tables precalculees, fil de rendu,
 * sans allocation.
 */
final class SpaceWarpSource
{
    // ---------------------------------------------------------------- profils radiaux (rayon, opacite : relatifs)

    private static final float[] CORE_RADII = {0f, 0.6f, 1f};
    private static final float[] CORE_ALPHAS = {1f, 1f, 0f};
    private static final float[] GLOW_RADII = {0f, 0.25f, 0.55f, 1f};
    private static final float[] GLOW_ALPHAS = {1f, 0.6f, 0.22f, 0f};
    private static final float[] RING_RADII = {0.84f, 0.93f, 1f};
    private static final float[] RING_ALPHAS = {0f, 1f, 0f};

    // ---------------------------------------------------------------- halos : rayon (degres), couleur, opacite, profil

    private static final float[] HALO_ANGLES = {0.7f, 2.5f, 6f, 13f, 26f, 9.5f, 19f};
    private static final float[][] HALO_COLORS = {
            {1.00f, 1.00f, 1.00f},
            {0.92f, 0.95f, 1.00f},
            {0.70f, 0.78f, 1.00f},
            {0.62f, 0.50f, 1.00f},
            {0.55f, 0.40f, 0.95f},
            {0.75f, 0.85f, 1.00f},
            {0.85f, 0.70f, 1.00f},
    };
    private static final float[] HALO_ALPHAS = {1f, 0.85f, 0.5f, 0.3f, 0.16f, 0.18f, 0.1f};
    private static final float[][] HALO_PROFILE_RADII = {
            CORE_RADII, GLOW_RADII, GLOW_RADII, GLOW_RADII, GLOW_RADII, RING_RADII, RING_RADII};
    private static final float[][] HALO_PROFILE_ALPHAS = {
            CORE_ALPHAS, GLOW_ALPHAS, GLOW_ALPHAS, GLOW_ALPHAS, GLOW_ALPHAS, RING_ALPHAS, RING_ALPHAS};
    /** Pulsation des opacites : amplitude relative, vitesse (radians par tick). */
    static final float PULSE = 0.07f;
    static final double PULSE_SPEED = 0.25;

    // ---------------------------------------------------------------- flares (deux etoiles a quatre branches)

    /** Longueur d'une branche (degres), demi-largeurs a la base : trait net et flou. */
    private static final float[] FLARE_LENGTH = {16f, 10f};
    private static final float[] FLARE_SHARP_WIDTH = {0.35f, 0.45f};
    private static final float[] FLARE_SOFT_WIDTH = {1.1f, 1.4f};
    /** Couleur, opacites du trait net et du flou. */
    private static final float[][] FLARE_COLORS = {{1f, 1f, 1f}, {0.78f, 0.82f, 1f}};
    private static final float[] FLARE_SHARP_ALPHA = {0.8f, 0.6f};
    private static final float[] FLARE_SOFT_ALPHA = {0.25f, 0.2f};
    /** Rotation propre (degres par tick, signes opposes) et angle de depart (degres). */
    static final float[] FLARE_SPIN = {0.9f, -1.4f};
    private static final float[] FLARE_PHASE = {0f, 45f};
    /** Branche : stations le long de la branche (fraction de la longueur), largeur et opacite relatives. */
    private static final float[] BRANCH_STATIONS = {0f, 0.3f, 1f};
    private static final float[] BRANCH_WIDTHS = {1f, 0.7f, 0.2f};
    private static final float[] BRANCH_ALPHAS = {1f, 0.4f, 0f};
    /** Longueur des flares au debut de l'apparition de la scene, relative. */
    private static final float START_LENGTH = 0.3f;

    // ---------------------------------------------------------------- maillage des disques

    private static final int SECTORS = 32;
    private static final float[] SECTOR_COS = new float[SECTORS + 1];
    private static final float[] SECTOR_SIN = new float[SECTORS + 1];
    /** Tangente de chaque rayon de halo. */
    private static final float[] HALO_TAN = new float[HALO_ANGLES.length];

    static
    {
        for (int j = 0; j <= SECTORS; j++) {
            double angle = 2.0 * Math.PI * j / SECTORS;
            SECTOR_COS[j] = (float) Math.cos(angle);
            SECTOR_SIN[j] = (float) Math.sin(angle);
        }
        for (int i = 0; i < HALO_ANGLES.length; i++) HALO_TAN[i] = (float) Math.tan(Math.toRadians(HALO_ANGLES[i]));
    }

    /** Base de la frame. */
    private Vector3fc w;
    private Vector3fc bu;
    private Vector3fc bv;
    private float radius;

    // ---------------------------------------------------------------- dessin

    /**
     * Halos puis flares, au point de fuite {@code w} (base {@code bu}, {@code bv}), a la distance {@code radius}, au
     * temps {@code time} (ticks d'affichage), apparition {@code ramp}. {@code vc} : {@link SpaceWarpTypes#ADD}.
     */
    void draw(VertexConsumer vc, SpaceWarpVertices out, Matrix4f m, Vector3fc w, Vector3fc bu, Vector3fc bv,
              float radius, double time, float ramp)
    {
        if (!(ramp > 0f)) return;
        this.w = w;
        this.bu = bu;
        this.bv = bv;
        this.radius = radius;
        for (int i = 0; i < HALO_ANGLES.length; i++) {
            float pulse = 1f + PULSE * (float) Math.sin(PULSE_SPEED * time + 1.7 * i);
            float[] c = HALO_COLORS[i];
            radial(vc, out, m, HALO_TAN[i], HALO_PROFILE_RADII[i], HALO_PROFILE_ALPHAS[i], c[0], c[1], c[2],
                    HALO_ALPHAS[i] * pulse * ramp);
        }
        float length = START_LENGTH + (1f - START_LENGTH) * ramp;
        for (int f = 0; f < FLARE_LENGTH.length; f++) {
            double spin = Math.toRadians(FLARE_PHASE[f] + FLARE_SPIN[f] * (time % 36000.0));
            float tip = (float) Math.tan(Math.toRadians(FLARE_LENGTH[f])) * length;
            float[] c = FLARE_COLORS[f];
            float soft = (float) Math.tan(Math.toRadians(FLARE_SOFT_WIDTH[f]));
            float sharp = (float) Math.tan(Math.toRadians(FLARE_SHARP_WIDTH[f]));
            for (int b = 0; b < 4; b++) {
                double angle = spin + 0.5 * Math.PI * b;
                float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
                branch(vc, out, m, dx, dy, tip, soft, c[0], c[1], c[2], FLARE_SOFT_ALPHA[f] * ramp);
                branch(vc, out, m, dx, dy, tip, sharp, c[0], c[1], c[2], FLARE_SHARP_ALPHA[f] * ramp);
            }
        }
    }

    /**
     * Disque ou anneau de rayon {@code tan} (tangente) : couronnes entre les rayons relatifs {@code radii}, opacite
     * {@code alpha x alphas[k]} interpolee entre elles.
     */
    private void radial(VertexConsumer vc, SpaceWarpVertices out, Matrix4f m, float tan, float[] radii,
                        float[] alphas, float r, float g, float b, float alpha)
    {
        for (int k = 0; k + 1 < radii.length; k++) {
            float inner = radii[k] * tan, outer = radii[k + 1] * tan;
            float a0 = alpha * alphas[k], a1 = alpha * alphas[k + 1];
            if (!(a0 > 0f) && !(a1 > 0f)) continue;
            for (int j = 0; j < SECTORS; j++) {
                plane(vc, out, m, inner * SECTOR_COS[j], inner * SECTOR_SIN[j], r, g, b, a0);
                plane(vc, out, m, outer * SECTOR_COS[j], outer * SECTOR_SIN[j], r, g, b, a1);
                plane(vc, out, m, outer * SECTOR_COS[j + 1], outer * SECTOR_SIN[j + 1], r, g, b, a1);
                plane(vc, out, m, inner * SECTOR_COS[j + 1], inner * SECTOR_SIN[j + 1], r, g, b, a0);
            }
        }
    }

    /**
     * Branche de flare dans la direction {@code (dx, dy)} du plan tangent, longueur {@code tip}, demi-largeur
     * {@code width} a la base : deux quads par troncon (bord transparent, axe opaque), largeur et opacite aux
     * stations {@link #BRANCH_STATIONS}.
     */
    private void branch(VertexConsumer vc, SpaceWarpVertices out, Matrix4f m, float dx, float dy, float tip,
                        float width, float r, float g, float b, float alpha)
    {
        // Perpendiculaire (-dy, dx).
        for (int k = 0; k + 1 < BRANCH_STATIONS.length; k++) {
            float s0 = BRANCH_STATIONS[k] * tip, s1 = BRANCH_STATIONS[k + 1] * tip;
            float w0 = BRANCH_WIDTHS[k] * width, w1 = BRANCH_WIDTHS[k + 1] * width;
            float a0 = BRANCH_ALPHAS[k] * alpha, a1 = BRANCH_ALPHAS[k + 1] * alpha;
            for (int side = -1; side <= 1; side += 2) {
                plane(vc, out, m, dx * s0, dy * s0, r, g, b, a0);
                plane(vc, out, m, dx * s0 - side * dy * w0, dy * s0 + side * dx * w0, r, g, b, 0f);
                plane(vc, out, m, dx * s1 - side * dy * w1, dy * s1 + side * dx * w1, r, g, b, 0f);
                plane(vc, out, m, dx * s1, dy * s1, r, g, b, a1);
            }
        }
    }

    /** Sommet du plan tangent : {@code radius (w + x bu + y bv)}. */
    private void plane(VertexConsumer vc, SpaceWarpVertices out, Matrix4f m, float x, float y, float r, float g,
                       float b, float a)
    {
        out.put(vc, m, radius * (w.x() + x * bu.x() + y * bv.x()), radius * (w.y() + x * bu.y() + y * bv.y()),
                radius * (w.z() + x * bu.z() + y * bv.z()), r, g, b, a);
    }
}
