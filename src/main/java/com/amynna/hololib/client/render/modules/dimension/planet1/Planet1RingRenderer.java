package com.amynna.hololib.client.render.modules.dimension.planet1;

import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.modules.dimension.planet1.Planet1Ring;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/**
 * Dessine un {@link Planet1Ring} : une suite d'annelets carres translucides (double face) dans le plan local
 * {@code y = 0}, de largeurs et d'opacites variables, separes par des divisions vides. Chaque annelet est un cadre de
 * quatre trapezes.
 */
final class Planet1RingRenderer implements HoloObjectRenderer<Planet1Ring>
{
    /** Bords des annelets, en fraction de la bande {@code [inner, outer]} (paires debut, fin). */
    private static final float[] EDGES = {
            0.00f, 0.06f, 0.07f, 0.16f, 0.18f, 0.30f, 0.31f, 0.38f, 0.38f, 0.47f,
            // Grande division entre 0.47 et 0.53.
            0.53f, 0.60f, 0.60f, 0.68f, 0.69f, 0.78f, 0.80f, 0.86f, 0.88f, 0.93f, 0.95f, 0.97f, 0.985f, 1.00f,
    };
    /** Couleur {@code 0xAARRGGBB} de chaque annelet : poussieres beiges et grises. */
    private static final int[] COLORS = {
            0x30A89A88, 0x58C2B096, 0x80D8C4A2, 0x90E4D2B0, 0x78CDB592, 0x88D9C3A0,
            0x70BFA584, 0x60B39C80, 0x50A8957E, 0x48C8B8A0, 0x389E9282, 0x30B0A492,
    };

    @Override
    public void render(Planet1Ring ring, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        float width = ring.outer() - ring.inner();
        for (int k = 0; k < COLORS.length; k++) {
            float in = ring.inner() + EDGES[2 * k] * width;
            float out = ring.inner() + EDGES[2 * k + 1] * width;
            int c = COLORS[k];
            int a = c >>> 24, r = c >> 16 & 0xFF, g = c >> 8 & 0xFF, b = c & 0xFF;
            // Cotes +Z, -Z, +X, -X : coins exterieurs puis interieurs.
            quad(vc, m, packedLight, r, g, b, a, -out, out, out, out, in, in, -in, in);
            quad(vc, m, packedLight, r, g, b, a, out, -out, -out, -out, -in, -in, in, -in);
            quad(vc, m, packedLight, r, g, b, a, out, out, out, -out, in, -in, in, in);
            quad(vc, m, packedLight, r, g, b, a, -out, -out, -out, out, -in, in, -in, -in);
        }
    }

    /** Quad du plan {@code y = 0} aux coins {@code (x, z)} donnes. */
    private static void quad(VertexConsumer vc, Matrix4f m, int light, int r, int g, int b, int a,
                             float x0, float z0, float x1, float z1, float x2, float z2, float x3, float z3)
    {
        vc.vertex(m, x0, 0f, z0).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, 0f, z1).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x2, 0f, z2).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x3, 0f, z3).color(r, g, b, a).uv2(light).endVertex();
    }
}
