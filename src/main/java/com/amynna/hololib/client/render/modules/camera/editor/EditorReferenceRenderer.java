package com.amynna.hololib.client.render.modules.camera.editor;

import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.modules.camera.editor.EditorReference;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Repere de l'editor : cadre sur les bords de l'ecran et cercle autour du reticule, traits fins translucides. */
final class EditorReferenceRenderer implements HoloObjectRenderer<EditorReference>
{
    private static final int FRAME_COLOR = 0xA0FFFFFF;
    private static final int CIRCLE_COLOR = 0xC040C0FF;
    private static final int SEGMENTS = 48;
    /** Epaisseur des traits, en fraction de la demi-hauteur. */
    private static final float THICKNESS = 0.006f;

    @Override
    public void render(EditorReference object, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        VertexConsumer consumer = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        Matrix4f matrix = pose.last().pose();
        float w = object.halfWidth();
        float h = object.halfHeight();
        float t = h * THICKNESS;
        rect(consumer, matrix, -w, h - t, w, h, FRAME_COLOR, packedLight);
        rect(consumer, matrix, -w, -h, w, -h + t, FRAME_COLOR, packedLight);
        rect(consumer, matrix, -w, -h + t, -w + t, h - t, FRAME_COLOR, packedLight);
        rect(consumer, matrix, w - t, -h + t, w, h - t, FRAME_COLOR, packedLight);
        float r = object.circleRadius();
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = i * Mth.TWO_PI / SEGMENTS;
            float a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            vertex(consumer, matrix, c0 * (r - t), s0 * (r - t), CIRCLE_COLOR, packedLight);
            vertex(consumer, matrix, c1 * (r - t), s1 * (r - t), CIRCLE_COLOR, packedLight);
            vertex(consumer, matrix, c1 * (r + t), s1 * (r + t), CIRCLE_COLOR, packedLight);
            vertex(consumer, matrix, c0 * (r + t), s0 * (r + t), CIRCLE_COLOR, packedLight);
        }
    }

    private static void rect(VertexConsumer consumer, Matrix4f matrix, float x0, float y0, float x1, float y1,
                             int color, int light)
    {
        vertex(consumer, matrix, x0, y0, color, light);
        vertex(consumer, matrix, x1, y0, color, light);
        vertex(consumer, matrix, x1, y1, color, light);
        vertex(consumer, matrix, x0, y1, color, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, int color, int light)
    {
        consumer.vertex(matrix, x, y, 0f).color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >>> 24)
                .uv2(light).endVertex();
    }
}
