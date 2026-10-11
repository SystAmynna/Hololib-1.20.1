package com.amynna.hololib.client.render.modules.dimension.space.warp;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

import java.util.Random;

/**
 * Particules de vitesse blanches du voyage (T35, livrable 3 ; modele des trainees de T23) : {@value #COUNT} segments
 * fins radiaux autour du point de fuite (direction de la cible), qui s'en eloignent en s'allongeant et defilent vers
 * l'arriere. Chaque trainee est un segment parallele a l'axe du voyage dans un tunnel (distance {@code r} a l'axe,
 * profondeur {@code z} le long de l'axe) qui avance vers l'oeil a vitesse constante et revient au loin en boucle ; sa
 * direction vue de l'oeil est {@code (z F + r e) / |.|} ({@code F} = point de fuite, {@code e} = direction radiale) :
 * petite et lente pres du point de fuite, longue et rapide sur les cotes. Tirages a graine fixe ({@link #SEED})
 * precalcules : memes particules a chaque saut et chez chaque joueur (au decalage d'horloge pres). Translucides
 * ({@link SpaceWarpTypes#BLEND}), tete opaque, queue transparente. Fil de rendu, sans allocation.
 */
final class SpaceWarpStreaks
{
    /** Nombre de trainees. */
    static final int COUNT = 240;
    /** Graine fixe des tirages. */
    static final long SEED = 0x5ACE_0723L;
    /** Tunnel : profondeur du retour au loin et de la sortie (derriere l'oeil), unites du tunnel. */
    private static final double Z_FAR = 80.0;
    private static final double Z_NEAR = -3.0;
    private static final double Z_RANGE = Z_FAR - Z_NEAR;
    /** Distance a l'axe du voyage : bornes. */
    private static final double MIN_R = 1.0;
    private static final double MAX_R = 3.0;
    /** Longueur d'une trainee le long de l'axe : bornes (scene entierement apparue). */
    private static final double MIN_LENGTH = 6.0;
    private static final double MAX_LENGTH = 16.0;
    /** Longueur relative au debut de l'apparition de la scene : les trainees s'allongent avec elle. */
    private static final double START_LENGTH = 0.2;
    /** Vitesse d'avance, unites du tunnel par tick, et ecart relatif d'une trainee a l'autre. */
    static final double SPEED = 2.5;
    private static final double SPEED_SPREAD = 0.3;
    /** Apparition au loin et disparition derriere l'oeil, en unites du tunnel. */
    private static final double FADE_IN_DEPTH = 20.0;
    private static final double FADE_OUT_DEPTH = 3.0;
    /**
     * Demi-largeur angulaire (radians) : {@code HALF_WIDTH x (WIDTH_BASE + sin theta)}, {@code theta} = angle au point
     * de fuite ; environ 1 pixel pres du point de fuite, 3 a 4 pixels sur les cotes (1 080 pixels, FOV 70).
     */
    private static final double HALF_WIDTH = 0.0015;
    private static final double WIDTH_BASE = 0.35;
    /** Opacite de la tete : bornes ; la queue est transparente. */
    private static final float MIN_ALPHA = 0.45f;
    private static final float MAX_ALPHA = 0.9f;
    /** Blanc des trainees, a peine bleute pour certaines. */
    private static final float RED_A = 1.0f;
    private static final float RED_B = 0.86f;
    private static final float GREEN_A = 1.0f;
    private static final float GREEN_B = 0.92f;
    private static final float BLUE = 1.0f;

    private static final float[] COS = new float[COUNT];
    private static final float[] SIN = new float[COUNT];
    private static final double[] R = new double[COUNT];
    private static final double[] PHASE = new double[COUNT];
    private static final double[] LENGTH = new double[COUNT];
    private static final double[] STREAK_SPEED = new double[COUNT];
    private static final float[] RED = new float[COUNT];
    private static final float[] GREEN = new float[COUNT];
    private static final float[] ALPHA = new float[COUNT];

    static
    {
        Random random = new Random(SEED);
        for (int i = 0; i < COUNT; i++) {
            double angle = 2.0 * Math.PI * random.nextDouble();
            COS[i] = (float) Math.cos(angle);
            SIN[i] = (float) Math.sin(angle);
            R[i] = MIN_R + (MAX_R - MIN_R) * random.nextDouble();
            PHASE[i] = random.nextDouble();
            LENGTH[i] = MIN_LENGTH + (MAX_LENGTH - MIN_LENGTH) * random.nextDouble();
            STREAK_SPEED[i] = SPEED * (1.0 + SPEED_SPREAD * (2.0 * random.nextDouble() - 1.0));
            float mix = random.nextFloat();
            RED[i] = RED_A + (RED_B - RED_A) * mix;
            GREEN[i] = GREEN_A + (GREEN_B - GREEN_A) * mix;
            ALPHA[i] = MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * random.nextFloat();
        }
    }

    /**
     * Trainees au temps {@code time} (ticks d'affichage), apparition {@code ramp}, sur la sphere de rayon
     * {@code radius} autour du point de fuite {@code f} (base {@code bu}, {@code bv}) : tete a la profondeur {@code z}
     * (bouclee de {@link #Z_FAR} a {@link #Z_NEAR}), queue a {@code z + longueur} (vers le point de fuite).
     */
    void draw(VertexConsumer vc, SpaceWarpVertices out, Matrix4f m, Vector3fc f, Vector3fc bu, Vector3fc bv,
              float radius, double time, float ramp)
    {
        if (!(ramp > 0f)) return;
        double lengthScale = START_LENGTH + (1.0 - START_LENGTH) * ramp;
        for (int i = 0; i < COUNT; i++) {
            double u = PHASE[i] + time * STREAK_SPEED[i] / Z_RANGE;
            u -= Math.floor(u);
            double head = Z_FAR - u * Z_RANGE;
            double tail = head + LENGTH[i] * lengthScale;
            double fade = Math.min(1.0, Math.min((Z_FAR - head) / FADE_IN_DEPTH, (head - Z_NEAR) / FADE_OUT_DEPTH));
            if (!(fade > 0.0)) continue;
            float alpha = ALPHA[i] * ramp * (float) fade;
            // Direction radiale e et largeur w (perpendiculaire) de la trainee, base (bu, bv) du point de fuite.
            float c = COS[i], s = SIN[i];
            float ex = c * bu.x() + s * bv.x(), ey = c * bu.y() + s * bv.y(), ez = c * bu.z() + s * bv.z();
            float wx = c * bv.x() - s * bu.x(), wy = c * bv.y() - s * bu.y(), wz = c * bv.z() - s * bu.z();
            double r = R[i];
            float red = RED[i], green = GREEN[i];
            end(vc, out, m, f, radius, tail, r, ex, ey, ez, wx, wy, wz, red, green, 0f, -1f);
            end(vc, out, m, f, radius, tail, r, ex, ey, ez, wx, wy, wz, red, green, 0f, 1f);
            end(vc, out, m, f, radius, head, r, ex, ey, ez, wx, wy, wz, red, green, alpha, 1f);
            end(vc, out, m, f, radius, head, r, ex, ey, ez, wx, wy, wz, red, green, alpha, -1f);
        }
    }

    /**
     * Sommet d'un bout de trainee : direction {@code (z F + r e) / |.|} au rayon {@code radius}, decale de
     * {@code side x demi-largeur} selon {@code w}.
     */
    private static void end(VertexConsumer vc, SpaceWarpVertices out, Matrix4f m, Vector3fc f, float radius, double z,
                            double r, float ex, float ey, float ez, float wx, float wy, float wz, float red,
                            float green, float alpha, float side)
    {
        double length = Math.sqrt(r * r + z * z);
        float cos = (float) (z / length);
        float sin = (float) (r / length);
        float half = side * radius * (float) (HALF_WIDTH * (WIDTH_BASE + sin));
        float dx = cos * f.x() + sin * ex;
        float dy = cos * f.y() + sin * ey;
        float dz = cos * f.z() + sin * ez;
        out.put(vc, m, dx * radius + wx * half, dy * radius + wy * half, dz * radius + wz * half, red, green, BLUE,
                alpha);
    }
}
