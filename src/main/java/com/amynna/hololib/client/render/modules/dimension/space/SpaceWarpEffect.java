package com.amynna.hololib.client.render.modules.dimension.space;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceJump;
import com.amynna.hololib.modules.dimension.space.math.SpacePose;
import com.amynna.hololib.modules.dimension.space.math.SpaceSkyFrame;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Distorsion du saut interstellaire (T23, D14) : <b>effet provisoire, a remplacer</b> (l'utilisatrice le redessinera).
 * Seule classe responsable de l'effet : son intensite ({@link #strength}), sa couleur de brouillard
 * ({@link #fogColor}) et son dessin ({@link #render}). Pour le remplacer, il suffit de reecrire cette classe en gardant
 * ces trois points d'entree ; {@code SpaceSkyRenderer} l'appelle et la surcouche de Space Pilot lit seulement
 * {@link #strength}.
 * <p>
 * <b>Intensite</b> {@code [0, 1]} au temps d'affichage du client ({@code SpaceEnvironmentModule.displayTicks}, jamais
 * le temps de jeu de Minecraft, C20) :
 * <ul>
 *     <li>phase {@code TRAVEL} du saut affiche ({@code jumpAt(partialTick)}) : fondu d'entree lisse sur les
 *     {@value #ENTRY_TICKS} premiers ticks du voyage (compte du saut, donc identique chez tous les joueurs ; un joueur
 *     qui arrive pendant le voyage voit tout de suite l'effet entier), puis 1 ;</li>
 *     <li>sortie : l'affichage passe directement du voyage au nouveau systeme (T21) ; le fondu de sortie est donc
 *     minute ici, sur {@value #EXIT_TICKS} ticks d'affichage depuis la derniere frame vue en voyage (meme chose si le
 *     voyage est annule ou abandonne).</li>
 * </ul>
 * L'etat du fondu de sortie (dernier instant vu en voyage) est partage par tous les appelants de la frame (brouillard,
 * ciel, surcouche) : memes arguments, meme resultat. Fil de rendu uniquement.
 * <p>
 * <b>Dessin</b> (dans le repere du {@code PoseStack} du ciel, apres le ciel spatial) : sphere blanc bleute
 * ({@link #SPHERE_RADIUS} blocs, plus blanche vers le point de fuite) d'opacite egale a l'intensite, puis
 * {@value #STREAK_COUNT} trainees : segments fins radiaux autour du point de fuite (direction de l'etoile cible), qui
 * s'en eloignent en s'allongeant. Modele : chaque trainee est un segment parallele a l'axe du voyage dans un tunnel
 * (distance {@code r} a l'axe, profondeur {@code z} le long de l'axe) qui avance vers l'oeil a vitesse constante et
 * revient au loin en boucle ; sa direction vue de l'oeil est {@code (z F + r e) / |.|} ({@code F} = point de fuite,
 * {@code e} = direction radiale de la trainee) : petite et lente pres du point de fuite, longue et rapide sur les
 * cotes. Tirages a graine fixe ({@link #SEED}) precalcules : meme ciel a chaque saut et chez chaque joueur (au decalage
 * d'horloge pres). A pleine intensite, {@code SpaceSkyRenderer} ne dessine plus le fond, les etoiles ni les corps.
 * <p>
 * Point de fuite : direction galactique origine -> cible du saut (figees dans l'etat, C58) vue depuis la pose affichee
 * ; pendant le fondu de sortie, la derniere direction vue en voyage (l'arrivee oriente le monde face a l'etoile sur le
 * meme axe, D10).
 * <p>
 * Un seul {@code RenderType} local, translucide, sans ecriture de profondeur ni elimination des faces : la sphere puis
 * les trainees sont dessinees dans l'ordre d'envoi, devant les corps du ciel (50 a 105 blocs). Aucune allocation par
 * frame, aucun etat GL modifie a la main.
 * <p>
 * Points a revoir au redessin : couleurs et opacites (constantes ci-dessous), forme des trainees (largeur, degrade),
 * nombre, vitesse, durees des fondus, absence de son, brouillard uniforme (il ne suit pas le point de fuite).
 */
public final class SpaceWarpEffect
{
    // ---------------------------------------------------------------- fondus (ticks d'affichage)

    /** Fondu d'entree, en ticks depuis le debut du voyage. */
    public static final int ENTRY_TICKS = 10;
    /** Fondu de sortie, en ticks d'affichage depuis la fin du voyage affiche. */
    public static final int EXIT_TICKS = 10;

    // ---------------------------------------------------------------- placement (blocs)

    /** Sphere blanche : devant le corps le plus proche (50 blocs). */
    static final float SPHERE_RADIUS = 45f;
    /** Trainees : devant la sphere. */
    static final float STREAK_RADIUS = 40f;
    private static final int SPHERE_SLICES = 32;
    private static final int SPHERE_STACKS = 16;

    // ---------------------------------------------------------------- trainees

    /** Nombre de trainees. */
    public static final int STREAK_COUNT = 240;
    /** Graine fixe des tirages. */
    public static final long SEED = 0x5ACE_0723L;
    /** Tunnel : profondeur du retour au loin et de la sortie (derriere l'oeil), unites du tunnel. */
    private static final double Z_FAR = 80.0;
    private static final double Z_NEAR = -3.0;
    private static final double Z_RANGE = Z_FAR - Z_NEAR;
    /** Distance a l'axe du voyage : bornes. */
    private static final double MIN_R = 1.0;
    private static final double MAX_R = 3.0;
    /** Longueur d'une trainee le long de l'axe : bornes (a pleine intensite). */
    private static final double MIN_LENGTH = 6.0;
    private static final double MAX_LENGTH = 16.0;
    /** Longueur relative au debut du fondu d'entree : les trainees s'allongent avec l'intensite. */
    private static final double START_LENGTH = 0.2;
    /** Vitesse d'avance, unites du tunnel par tick, et ecart relatif d'une trainee a l'autre. */
    private static final double SPEED = 2.5;
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
    /** Opacite de la tete (bout le plus eloigne du point de fuite) : bornes ; la queue est transparente. */
    private static final float MIN_ALPHA = 0.5f;
    private static final float MAX_ALPHA = 0.9f;

    // ---------------------------------------------------------------- couleurs

    /** Sphere : vers le point de fuite et a l'oppose (blanc bleute). */
    private static final float FRONT_RED = 0.97f;
    private static final float FRONT_GREEN = 0.98f;
    private static final float FRONT_BLUE = 1.0f;
    private static final float BACK_RED = 0.80f;
    private static final float BACK_GREEN = 0.87f;
    private static final float BACK_BLUE = 1.0f;
    /** Trainees : deux bleus, melange tire par trainee. */
    private static final float STREAK_RED_A = 0.30f;
    private static final float STREAK_GREEN_A = 0.50f;
    private static final float STREAK_RED_B = 0.55f;
    private static final float STREAK_GREEN_B = 0.75f;
    private static final float STREAK_BLUE = 1.0f;
    /** Brouillard et fond de l'ecran a pleine intensite : milieu du degrade de la sphere (horizon). */
    private static final float FOG_RED = 0.5f * (FRONT_RED + BACK_RED);
    private static final float FOG_GREEN = 0.5f * (FRONT_GREEN + BACK_GREEN);
    private static final float FOG_BLUE = 0.5f * (FRONT_BLUE + BACK_BLUE);

    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    // ---------------------------------------------------------------- tables precalculees

    /** Sommets unitaires de la sphere, {@code (SLICES + 1) x (STACKS + 1)}. */
    private static final float[] SPHERE_X;
    private static final float[] SPHERE_Y;
    private static final float[] SPHERE_Z;
    /** Par trainee : direction radiale (angle autour du point de fuite), distance a l'axe, phase, longueur, vitesse. */
    private static final float[] STREAK_COS = new float[STREAK_COUNT];
    private static final float[] STREAK_SIN = new float[STREAK_COUNT];
    private static final double[] STREAK_R = new double[STREAK_COUNT];
    private static final double[] STREAK_PHASE = new double[STREAK_COUNT];
    private static final double[] STREAK_LENGTH = new double[STREAK_COUNT];
    private static final double[] STREAK_SPEED = new double[STREAK_COUNT];
    /** Par trainee : couleur et opacite de la tete. */
    private static final float[] STREAK_RED = new float[STREAK_COUNT];
    private static final float[] STREAK_GREEN = new float[STREAK_COUNT];
    private static final float[] STREAK_ALPHA = new float[STREAK_COUNT];

    static
    {
        int count = (SPHERE_SLICES + 1) * (SPHERE_STACKS + 1);
        SPHERE_X = new float[count];
        SPHERE_Y = new float[count];
        SPHERE_Z = new float[count];
        for (int j = 0; j <= SPHERE_STACKS; j++) {
            double latitude = -0.5 * Math.PI + Math.PI * j / SPHERE_STACKS;
            for (int i = 0; i <= SPHERE_SLICES; i++) {
                double longitude = 2.0 * Math.PI * i / SPHERE_SLICES;
                int index = j * (SPHERE_SLICES + 1) + i;
                SPHERE_X[index] = (float) (Math.cos(latitude) * Math.cos(longitude));
                SPHERE_Y[index] = (float) Math.sin(latitude);
                SPHERE_Z[index] = (float) (Math.cos(latitude) * Math.sin(longitude));
            }
        }
        Random random = new Random(SEED);
        for (int i = 0; i < STREAK_COUNT; i++) {
            double angle = 2.0 * Math.PI * random.nextDouble();
            STREAK_COS[i] = (float) Math.cos(angle);
            STREAK_SIN[i] = (float) Math.sin(angle);
            STREAK_R[i] = MIN_R + (MAX_R - MIN_R) * random.nextDouble();
            STREAK_PHASE[i] = random.nextDouble();
            STREAK_LENGTH[i] = MIN_LENGTH + (MAX_LENGTH - MIN_LENGTH) * random.nextDouble();
            STREAK_SPEED[i] = SPEED * (1.0 + SPEED_SPREAD * (2.0 * random.nextDouble() - 1.0));
            float mix = random.nextFloat();
            STREAK_RED[i] = STREAK_RED_A + (STREAK_RED_B - STREAK_RED_A) * mix;
            STREAK_GREEN[i] = STREAK_GREEN_A + (STREAK_GREEN_B - STREAK_GREEN_A) * mix;
            STREAK_ALPHA[i] = MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * random.nextFloat();
        }
    }

    // ---------------------------------------------------------------- intensite (partagee, fil de rendu)

    /** Environment suivi : un autre (changement de dimension, reinstallation) remet le suivi a zero. */
    private static SpaceEnvironmentModule tracked;
    /** Dernier temps d'affichage vu en voyage, et intensite a cet instant (depart du fondu de sortie). */
    private static double lastTravelTime = Double.NEGATIVE_INFINITY;
    private static float lastTravelStrength;

    // ---------------------------------------------------------------- etat du dessin (reutilise)

    private final SpacePose pose = new SpacePose();
    private final SpaceSkyFrame frame = new SpaceSkyFrame();
    /** Point de fuite (axes du monde, unitaire), garde pour le fondu de sortie. */
    private final Vector3f vanishing = new Vector3f(0f, 0f, -1f);
    /** Base perpendiculaire au point de fuite. */
    private final Vector3f bu = new Vector3f();
    private final Vector3f bv = new Vector3f();
    private final Vector3f transformed = new Vector3f();

    SpaceWarpEffect() {}

    // ---------------------------------------------------------------- intensite

    /**
     * Intensite de la distorsion pour la frame, {@code [0, 1]} ({@code 0} : rien a dessiner, {@code 1} : le ciel est
     * entierement remplace). Fil de rendu ; appels repetes avec le meme {@code partialTick} dans une frame : meme
     * resultat. {@code env} nul : {@code 0}.
     */
    public static float strength(SpaceEnvironmentModule env, float partialTick)
    {
        if (env == null) return 0f;
        if (env != tracked) {
            tracked = env;
            lastTravelTime = Double.NEGATIVE_INFINITY;
            lastTravelStrength = 0f;
        }
        double now = env.displayTicks(partialTick);
        SpaceJump jump = env.jumpAt(partialTick);
        if (jump.phase() == SpaceJump.Phase.TRAVEL) {
            // Temps depuis le debut du voyage : l'intervalle qui mene a l'etat de compte 0 est le premier.
            double elapsed = jump.ticks() + env.jumpFraction(partialTick);
            float s = (float) smoothstep(elapsed / ENTRY_TICKS);
            lastTravelTime = now;
            lastTravelStrength = s;
            return s;
        }
        double since = now - lastTravelTime;
        if (!(since < EXIT_TICKS)) return 0f;
        if (since < -EXIT_TICKS) {
            // Horloge revenue loin en arriere (resynchronisation) : fondu abandonne.
            lastTravelTime = Double.NEGATIVE_INFINITY;
            return 0f;
        }
        return lastTravelStrength * (float) (1.0 - smoothstep(Math.max(0.0, since) / EXIT_TICKS));
    }

    /**
     * Brouillard et fond de l'ecran : {@code rgb} (couleur spatiale deja posee) melangee vers le blanc bleute de la
     * distorsion selon {@code strength} (hors fluides : a l'appelant de ne pas appeler sous l'eau, C23).
     */
    public static void fogColor(float strength, Vector3f rgb)
    {
        if (!(strength > 0f)) return;
        float s = Math.min(1f, strength);
        rgb.set(rgb.x + (FOG_RED - rgb.x) * s, rgb.y + (FOG_GREEN - rgb.y) * s, rgb.z + (FOG_BLUE - rgb.z) * s);
    }

    // ---------------------------------------------------------------- dessin

    /**
     * Dessine l'effet d'intensite {@code strength} ({@code > 0}) par-dessus ce qui a deja ete dessine du ciel.
     *
     * @param m     matrice du {@code PoseStack} du ciel (rotation camera)
     * @param scale reduction des rayons si le plan lointain est court (meme facteur que le ciel)
     */
    void render(MultiBufferSource buffers, Matrix4f m, SpaceEnvironmentModule env, float partialTick, float strength,
                float scale)
    {
        if (!(strength > 0f)) return;
        float s = Math.min(1f, strength);
        updateVanishingPoint(env, partialTick);
        basis(vanishing);
        VertexConsumer vc = buffers.getBuffer(WarpType.WARP_QUADS);
        drawSphere(vc, m, SPHERE_RADIUS * scale, s);
        drawStreaks(vc, m, STREAK_RADIUS * scale, s, env.displayTicks(partialTick));
    }

    /**
     * Pendant le voyage : direction galactique origine -> cible du saut vue depuis la pose affichee ; sinon (fondu de
     * sortie) : la derniere direction vue.
     */
    private void updateVanishingPoint(SpaceEnvironmentModule env, float partialTick)
    {
        SpaceJump jump = env.jumpAt(partialTick);
        if (jump.phase() != SpaceJump.Phase.TRAVEL) return;
        frame.set(env.pose(partialTick, pose));
        Vector3dc from = jump.origin();
        Vector3dc to = jump.destination();
        float x = vanishing.x, y = vanishing.y, z = vanishing.z;
        if (!(frame.galacticDirection(from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), vanishing) > 0.0)) {
            vanishing.set(x, y, z);
        }
    }

    /** Sphere blanc bleute, plus blanche vers le point de fuite, opacite {@code alpha}. */
    private void drawSphere(VertexConsumer vc, Matrix4f m, float radius, float alpha)
    {
        int row = SPHERE_SLICES + 1;
        for (int j = 0; j < SPHERE_STACKS; j++) {
            for (int i = 0; i < SPHERE_SLICES; i++) {
                sphereVertex(vc, m, j * row + i, radius, alpha);
                sphereVertex(vc, m, j * row + i + 1, radius, alpha);
                sphereVertex(vc, m, (j + 1) * row + i + 1, radius, alpha);
                sphereVertex(vc, m, (j + 1) * row + i, radius, alpha);
            }
        }
    }

    private void sphereVertex(VertexConsumer vc, Matrix4f m, int index, float radius, float alpha)
    {
        float x = SPHERE_X[index], y = SPHERE_Y[index], z = SPHERE_Z[index];
        float t = 0.5f * (1f + x * vanishing.x + y * vanishing.y + z * vanishing.z);
        vertex(vc, m, x * radius, y * radius, z * radius, BACK_RED + (FRONT_RED - BACK_RED) * t,
                BACK_GREEN + (FRONT_GREEN - BACK_GREEN) * t, BACK_BLUE + (FRONT_BLUE - BACK_BLUE) * t, alpha);
    }

    /**
     * Trainees au temps d'affichage {@code time} (ticks) : tete a la profondeur {@code z} (bouclee de {@link #Z_FAR}
     * a {@link #Z_NEAR}), queue a {@code z + longueur} (vers le point de fuite) ; quad de la queue (transparente) a la
     * tete, largeur perpendiculaire au plan (point de fuite, direction radiale).
     */
    private void drawStreaks(VertexConsumer vc, Matrix4f m, float radius, float strength, double time)
    {
        double lengthScale = START_LENGTH + (1.0 - START_LENGTH) * strength;
        for (int i = 0; i < STREAK_COUNT; i++) {
            double u = STREAK_PHASE[i] + time * STREAK_SPEED[i] / Z_RANGE;
            u -= Math.floor(u);
            double head = Z_FAR - u * Z_RANGE;
            double tail = head + STREAK_LENGTH[i] * lengthScale;
            double fade = Math.min(1.0, Math.min((Z_FAR - head) / FADE_IN_DEPTH, (head - Z_NEAR) / FADE_OUT_DEPTH));
            if (!(fade > 0.0)) continue;
            float alpha = STREAK_ALPHA[i] * strength * (float) fade;
            // Direction radiale e et largeur w (perpendiculaire) de la trainee, base (bu, bv) du point de fuite.
            float c = STREAK_COS[i], s = STREAK_SIN[i];
            float ex = c * bu.x + s * bv.x, ey = c * bu.y + s * bv.y, ez = c * bu.z + s * bv.z;
            float wx = c * bv.x - s * bu.x, wy = c * bv.y - s * bu.y, wz = c * bv.z - s * bu.z;
            double r = STREAK_R[i];
            float red = STREAK_RED[i], green = STREAK_GREEN[i];
            streakEnd(vc, m, radius, tail, r, ex, ey, ez, wx, wy, wz, red, green, 0f, -1f);
            streakEnd(vc, m, radius, tail, r, ex, ey, ez, wx, wy, wz, red, green, 0f, 1f);
            streakEnd(vc, m, radius, head, r, ex, ey, ez, wx, wy, wz, red, green, alpha, 1f);
            streakEnd(vc, m, radius, head, r, ex, ey, ez, wx, wy, wz, red, green, alpha, -1f);
        }
    }

    /**
     * Sommet d'un bout de trainee : direction {@code (z F + r e) / |.|} au rayon {@code radius}, decale de
     * {@code side x demi-largeur} selon {@code w}.
     */
    private void streakEnd(VertexConsumer vc, Matrix4f m, float radius, double z, double r, float ex, float ey,
                           float ez, float wx, float wy, float wz, float red, float green, float alpha, float side)
    {
        double length = Math.sqrt(r * r + z * z);
        float cos = (float) (z / length);
        float sin = (float) (r / length);
        float half = side * radius * (float) (HALF_WIDTH * (WIDTH_BASE + sin));
        float dx = cos * vanishing.x + sin * ex;
        float dy = cos * vanishing.y + sin * ey;
        float dz = cos * vanishing.z + sin * ez;
        vertex(vc, m, dx * radius + wx * half, dy * radius + wy * half, dz * radius + wz * half, red, green,
                STREAK_BLUE, alpha);
    }

    // ---------------------------------------------------------------- primitives

    /**
     * Base perpendiculaire a {@code w} : {@code bu = normalize(w x a)}, {@code bv = bu x w} ; {@code a} = haut du
     * monde, ou est si {@code w} est presque vertical.
     */
    private void basis(Vector3f w)
    {
        if (Math.abs(w.y) < 0.9f) {
            bu.set(w).cross(0f, 1f, 0f);
        } else {
            bu.set(w).cross(1f, 0f, 0f);
        }
        bu.normalize();
        bv.set(bu).cross(w);
    }

    /** Sommet transforme sans allocation ({@code VertexConsumer.vertex(Matrix4f, ...)} cree un vecteur, C38). */
    private void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a)
    {
        m.transformPosition(x, y, z, transformed);
        vc.vertex(transformed.x, transformed.y, transformed.z).color(r, g, b, a).uv2(LIGHT).endVertex();
    }

    /** {@code u^2 (3 - 2u)} sur {@code u} borne a {@code [0, 1]}. */
    private static double smoothstep(double u)
    {
        double x = u < 0.0 ? 0.0 : Math.min(1.0, u);
        return x * x * (3.0 - 2.0 * x);
    }

    /**
     * {@code RenderType} local (sous-classe pour acceder aux etats proteges, jamais instanciee) : quads translucides
     * double face, <b>sans ecriture de profondeur</b> ni tri (ordre d'envoi : sphere puis trainees), test de
     * profondeur normal (devant les corps du ciel).
     */
    private static final class WarpType extends RenderType
    {
        static final RenderType WARP_QUADS = create(HoloLib.MODID + ":space_warp_quads",
                DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_LIGHTMAP_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private WarpType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                         boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear)
        {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
