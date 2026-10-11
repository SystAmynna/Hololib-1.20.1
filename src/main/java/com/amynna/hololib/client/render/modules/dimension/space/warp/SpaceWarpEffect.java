package com.amynna.hololib.client.render.modules.dimension.space.warp;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Dessin de la distorsion du saut (T35, D23, C73) par-dessus le ciel, dans la passe de {@code SpaceSkyRenderer}, selon
 * la chronologie {@link SpaceWarpTimeline} :
 * <ul>
 *     <li>scene du voyage (phase {@code WARP}, sauf blanc entier) : degrade arc-en-ciel opaque
 *     ({@link SpaceWarpSpectrum}, {@value #SPECTRUM_RADIUS} blocs), source lumineuse, halos et flares additifs
 *     ({@link SpaceWarpSource}, {@value #SOURCE_RADIUS}), particules de vitesse blanches ({@link SpaceWarpStreaks},
 *     {@value #STREAK_RADIUS}) ; le ciel normal n'est alors pas dessine ;</li>
 *     <li>flash : sphere blanche translucide d'opacite {@link SpaceWarpTimeline#whiteness()} a
 *     {@value #FLASH_RADIUS} blocs, devant tout le ciel (corps a 50 blocs au plus pres) : fin de la convergence,
 *     debut du voyage, fin du voyage et arrivee.</li>
 * </ul>
 * La convergence des etoiles est dessinee par le ciel lui-meme ({@link SpaceWarpConvergence}). Brouillard et fond de
 * l'ecran : {@link #fogColor}. Rayons multiplies par l'echelle du ciel (plan lointain court). Types locaux
 * {@link SpaceWarpTypes}, aucune allocation par frame, aucun etat GL modifie a la main. Fil de rendu.
 */
public final class SpaceWarpEffect
{
    // ---------------------------------------------------------------- placement (blocs)

    /** Degrade : derriere tout le reste de la scene, sous le plan lointain (128 blocs au moins). */
    static final float SPECTRUM_RADIUS = 100f;
    /** Plan tangent de la source et des flares (jusqu'a 1,16 x plus loin au bord du plus grand halo). */
    static final float SOURCE_RADIUS = 90f;
    /** Particules de vitesse. */
    static final float STREAK_RADIUS = 40f;
    /** Flash blanc : devant le corps le plus proche (50 blocs) et les particules. */
    static final float FLASH_RADIUS = 35f;
    private static final int FLASH_SLICES = 24;
    private static final int FLASH_STACKS = 12;

    // ---------------------------------------------------------------- brouillard

    /** Brouillard pendant le voyage : couleur du degrade dans le regard, attenuee. */
    static final float FOG_GAIN = 0.85f;

    // ---------------------------------------------------------------- tables precalculees

    private static final float[] FLASH_X;
    private static final float[] FLASH_Y;
    private static final float[] FLASH_Z;

    static
    {
        int count = (FLASH_SLICES + 1) * (FLASH_STACKS + 1);
        FLASH_X = new float[count];
        FLASH_Y = new float[count];
        FLASH_Z = new float[count];
        for (int j = 0; j <= FLASH_STACKS; j++) {
            double latitude = -0.5 * Math.PI + Math.PI * j / FLASH_STACKS;
            for (int i = 0; i <= FLASH_SLICES; i++) {
                double longitude = 2.0 * Math.PI * i / FLASH_SLICES;
                int index = j * (FLASH_SLICES + 1) + i;
                FLASH_X[index] = (float) (Math.cos(latitude) * Math.cos(longitude));
                FLASH_Y[index] = (float) Math.sin(latitude);
                FLASH_Z[index] = (float) (Math.cos(latitude) * Math.sin(longitude));
            }
        }
    }

    // ---------------------------------------------------------------- etat du dessin (reutilise)

    private final SpaceWarpVertices out = new SpaceWarpVertices();
    private final SpaceWarpSpectrum spectrum = new SpaceWarpSpectrum();
    private final SpaceWarpSource source = new SpaceWarpSource();
    private final SpaceWarpStreaks streaks = new SpaceWarpStreaks();
    /** Base perpendiculaire au point de fuite. */
    private final Vector3f bu = new Vector3f();
    private final Vector3f bv = new Vector3f();
    private final Vector3f fog = new Vector3f();

    public SpaceWarpEffect() {}

    // ---------------------------------------------------------------- dessin

    /**
     * Scene du voyage et flash de la chronologie {@code timeline} (lue pour la frame), par-dessus ce qui a deja ete
     * dessine du ciel.
     *
     * @param m     matrice du {@code PoseStack} du ciel (rotation camera)
     * @param scale reduction des rayons si le plan lointain est court (meme facteur que le ciel)
     */
    public void render(MultiBufferSource buffers, Matrix4f m, SpaceWarpTimeline timeline, float scale)
    {
        if (!timeline.active()) return;
        float white = timeline.whiteness();
        if (timeline.sceneVisible() && white < 1f) {
            Vector3fc w = timeline.vanishing();
            basis(w);
            double time = timeline.time();
            float ramp = timeline.sceneRamp();
            spectrum.draw(buffers.getBuffer(SpaceWarpTypes.BLEND), out, m, w, bu, bv, SPECTRUM_RADIUS * scale, time);
            source.draw(buffers.getBuffer(SpaceWarpTypes.ADD), out, m, w, bu, bv, SOURCE_RADIUS * scale, time, ramp);
            streaks.draw(buffers.getBuffer(SpaceWarpTypes.BLEND), out, m, w, bu, bv, STREAK_RADIUS * scale, time,
                    ramp);
        }
        if (white > 0f) {
            drawFlash(buffers.getBuffer(SpaceWarpTypes.BLEND), m, FLASH_RADIUS * scale, Math.min(1f, white));
        }
    }

    /**
     * Brouillard et fond de l'ecran (hors fluides : a l'appelant de ne pas appeler sous l'eau, C23) : {@code rgb}
     * (couleur spatiale deja posee) remplacee pendant le voyage par la couleur du degrade dans le regard
     * {@code look} (axes du monde, unitaire), fois {@link #FOG_GAIN}, puis melangee vers le blanc des flashs.
     */
    public void fogColor(SpaceWarpTimeline timeline, Vector3fc look, Vector3f rgb)
    {
        if (!timeline.active()) return;
        if (timeline.sceneVisible()) {
            Vector3fc w = timeline.vanishing();
            spectrum.color(look.x() * w.x() + look.y() * w.y() + look.z() * w.z(), fog);
            rgb.set(fog.x * FOG_GAIN, fog.y * FOG_GAIN, fog.z * FOG_GAIN);
        }
        float s = Math.min(1f, timeline.whiteness());
        if (s > 0f) rgb.set(rgb.x + (1f - rgb.x) * s, rgb.y + (1f - rgb.y) * s, rgb.z + (1f - rgb.z) * s);
    }

    /** Sphere blanche d'opacite {@code alpha}. */
    private void drawFlash(VertexConsumer vc, Matrix4f m, float radius, float alpha)
    {
        int row = FLASH_SLICES + 1;
        for (int j = 0; j < FLASH_STACKS; j++) {
            for (int i = 0; i < FLASH_SLICES; i++) {
                flashVertex(vc, m, j * row + i, radius, alpha);
                flashVertex(vc, m, j * row + i + 1, radius, alpha);
                flashVertex(vc, m, (j + 1) * row + i + 1, radius, alpha);
                flashVertex(vc, m, (j + 1) * row + i, radius, alpha);
            }
        }
    }

    private void flashVertex(VertexConsumer vc, Matrix4f m, int index, float radius, float alpha)
    {
        out.put(vc, m, FLASH_X[index] * radius, FLASH_Y[index] * radius, FLASH_Z[index] * radius, 1f, 1f, 1f, alpha);
    }

    /**
     * Base perpendiculaire a {@code w} : {@code bu = normalize(w x a)}, {@code bv = bu x w} ; {@code a} = haut du
     * monde, ou est si {@code w} est presque vertical.
     */
    private void basis(Vector3fc w)
    {
        if (Math.abs(w.y()) < 0.9f) {
            bu.set(w).cross(0f, 1f, 0f);
        } else {
            bu.set(w).cross(1f, 0f, 0f);
        }
        bu.normalize();
        bv.set(bu).cross(w);
    }
}
