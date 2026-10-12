package com.amynna.hololib.client.render.edit;

import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.client.edit.HoloEditTools;
import com.amynna.hololib.client.render.holo.ResolvedHolo;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.client.render.world.WorldRenderContext;
import com.amynna.hololib.client.render.world.WorldRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Gizmos de l'outil d'edition actif sur un objet ancre {@code World} : trois segments X / Y / Z (translation) ou trois
 * cercles (rotation), alignes sur les axes du monde, au centre de l'objet. L'axe tenu ou vise est surligne. Lit
 * l'etat de {@link HoloEditTools} ; aucune allocation par frame.
 */
public final class HoloGizmoRenderer extends WorldRenderer<ResolvedHolo>
{
    private static final int SEGMENTS = 48;
    private static final float THICKNESS = 0.012f;
    /** Axes X, Y, Z : couleurs critique, selection et principale du theme. */
    private static final HoloColor[] COLORS = {HoloColor.critical(0xE0), HoloColor.selection(0xE0),
            HoloColor.primary(0xE0)};
    private static final HoloColor HIGHLIGHT = HoloColor.action(0xFF).lighter(0.03f);
    /** Composantes des axes : X, Y, Z. */
    private static final float[][] AXES = {{1f, 0f, 0f}, {0f, 1f, 0f}, {0f, 0f, 1f}};

    @Override
    protected List<? extends ResolvedHolo> instances() { return HoloEditTools.gizmo(); }

    @Override
    protected boolean shouldRender(ResolvedHolo object, Vec3 position, WorldRenderContext ctx)
    {
        return object.available();
    }

    @Override
    protected int packedLight(ResolvedHolo object, Vec3 position, WorldRenderContext ctx)
    {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    protected void render(ResolvedHolo object, PoseStack pose, MultiBufferSource buffers, int packedLight,
                          WorldRenderContext ctx)
    {
        VertexConsumer consumer = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        Matrix4f matrix = pose.last().pose();
        float size = HoloEditTools.gizmoSize(object.object());
        int highlighted = HoloEditTools.highlightedAxis();
        boolean translate = HoloEditTools.gizmoTranslate();
        HoloTheme theme = HoloThemes.resolve(object.object());
        for (int i = 0; i < 3; i++) {
            int color = (i == highlighted ? HIGHLIGHT : COLORS[i]).resolve(theme);
            float[] a = AXES[i];
            float[] u = AXES[(i + 1) % 3];
            float[] v = AXES[(i + 2) % 3];
            if (translate) {
                segment(consumer, matrix, a, u, size, color, packedLight);
                segment(consumer, matrix, a, v, size, color, packedLight);
            } else {
                ring(consumer, matrix, a, u, v, size, color, packedLight);
            }
        }
    }

    /** Bande fine le long de {@code a}, de {@code -size} a {@code size}, epaisse selon {@code w}. */
    private static void segment(VertexConsumer consumer, Matrix4f matrix, float[] a, float[] w, float size, int color,
                                int light)
    {
        float t = THICKNESS * Math.max(1f, size);
        vertex(consumer, matrix, -size * a[0] - t * w[0], -size * a[1] - t * w[1], -size * a[2] - t * w[2], color,
                light);
        vertex(consumer, matrix, size * a[0] - t * w[0], size * a[1] - t * w[1], size * a[2] - t * w[2], color, light);
        vertex(consumer, matrix, size * a[0] + t * w[0], size * a[1] + t * w[1], size * a[2] + t * w[2], color, light);
        vertex(consumer, matrix, -size * a[0] + t * w[0], -size * a[1] + t * w[1], -size * a[2] + t * w[2], color,
                light);
    }

    /** Cercle de rayon {@code size} dans le plan {@code (u, v)} : anneau plat et bande le long de l'axe {@code n}. */
    private static void ring(VertexConsumer consumer, Matrix4f matrix, float[] n, float[] u, float[] v, float size,
                             int color, int light)
    {
        float t = THICKNESS * Math.max(1f, size);
        for (int s = 0; s < SEGMENTS; s++) {
            float a0 = s * Mth.TWO_PI / SEGMENTS;
            float a1 = (s + 1) * Mth.TWO_PI / SEGMENTS;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            // Anneau dans le plan.
            ringVertex(consumer, matrix, u, v, n, c0, s0, size - t, 0f, color, light);
            ringVertex(consumer, matrix, u, v, n, c1, s1, size - t, 0f, color, light);
            ringVertex(consumer, matrix, u, v, n, c1, s1, size + t, 0f, color, light);
            ringVertex(consumer, matrix, u, v, n, c0, s0, size + t, 0f, color, light);
            // Bande le long de l'axe (visible de profil).
            ringVertex(consumer, matrix, u, v, n, c0, s0, size, -t, color, light);
            ringVertex(consumer, matrix, u, v, n, c1, s1, size, -t, color, light);
            ringVertex(consumer, matrix, u, v, n, c1, s1, size, t, color, light);
            ringVertex(consumer, matrix, u, v, n, c0, s0, size, t, color, light);
        }
    }

    private static void ringVertex(VertexConsumer consumer, Matrix4f matrix, float[] u, float[] v, float[] n, float c,
                                   float s, float radius, float along, int color, int light)
    {
        float cu = c * radius;
        float sv = s * radius;
        vertex(consumer, matrix, cu * u[0] + sv * v[0] + along * n[0], cu * u[1] + sv * v[1] + along * n[1],
                cu * u[2] + sv * v[2] + along * n[2], color, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, int color,
                               int light)
    {
        consumer.vertex(matrix, x, y, z).color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >>> 24)
                .uv2(light).endVertex();
    }
}
