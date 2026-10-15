package com.amynna.hololib.client.render.modules.monde.triggerarea;

import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.modules.monde.triggerarea.TriggerAreaZone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/**
 * Dessin de la zone de la Trigger Area ({@link TriggerAreaZone}) : cadre de douze aretes (deux bandes croisees chacune,
 * d'epaisseur constante en blocs quelle que soit l'echelle de chaque axe) et six faces tres transparentes, rognees pour
 * ne pas recouvrir les bandes (aucun scintillement). Couleurs : roles du theme, resolues au dessin. Le repere local est
 * le cube de demi-arete 1 : l'echelle du transform (la demi-arete reelle de chaque axe) est deja dans la pile.
 */
final class TriggerAreaZoneRenderer implements HoloObjectRenderer<TriggerAreaZone>
{
    private static final HoloColor EDGE = HoloColor.action(0xE0);
    private static final HoloColor FACE = HoloColor.action(0x26);
    /** Demi-largeur des aretes, en blocs. */
    private static final float EDGE_HALF_WIDTH = 0.025f;
    /** Rognage des faces par rapport au bord, en demi-largeurs d'arete. */
    private static final float FACE_INSET = 1.3f;
    private static final float[] SIGNS = {-1f, 1f};

    /** Epaisseur locale de chaque axe (demi-largeur / demi-arete reelle), reutilisee. */
    private final float[] thickness = new float[3];

    @Override
    public void render(TriggerAreaZone zone, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        HoloTransform t = zone.transform();
        thickness[0] = EDGE_HALF_WIDTH / Math.max(TriggerAreaZone.half(t.scaleX()), 0.001f);
        thickness[1] = EDGE_HALF_WIDTH / Math.max(TriggerAreaZone.half(t.scaleY()), 0.001f);
        thickness[2] = EDGE_HALF_WIDTH / Math.max(TriggerAreaZone.half(t.scaleZ()), 0.001f);
        HoloTheme theme = HoloThemes.resolve(zone);
        Matrix4f m = pose.last().pose();
        VertexConsumer quads = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        faces(quads, m, FACE.resolve(theme), packedLight);
        edges(quads, m, EDGE.resolve(theme), packedLight);
    }

    /** Six faces du cube, rognees le long de leurs bords. */
    private void faces(VertexConsumer c, Matrix4f m, int color, int light)
    {
        for (int axis = 0; axis < 3; axis++) {
            int ua = (axis + 1) % 3;
            int va = (axis + 2) % 3;
            float iu = 1f - FACE_INSET * thickness[ua];
            float iv = 1f - FACE_INSET * thickness[va];
            if (iu <= 0f || iv <= 0f) continue;
            for (float side : SIGNS) {
                vertex(c, m, axis, side, ua, -iu, va, -iv, color, light);
                vertex(c, m, axis, side, ua, iu, va, -iv, color, light);
                vertex(c, m, axis, side, ua, iu, va, iv, color, light);
                vertex(c, m, axis, side, ua, -iu, va, iv, color, light);
            }
        }
    }

    /** Douze aretes : pour chaque axe, quatre aretes en bandes croisees. */
    private void edges(VertexConsumer c, Matrix4f m, int color, int light)
    {
        for (int axis = 0; axis < 3; axis++) {
            int ua = (axis + 1) % 3;
            int va = (axis + 2) % 3;
            float wu = thickness[ua];
            float wv = thickness[va];
            for (float su : SIGNS) {
                for (float sv : SIGNS) {
                    // Bande dans le plan (axe, ua) puis dans le plan (axe, va), centrees sur l'arete.
                    band(c, m, axis, ua, su, va, sv, wu, 0f, color, light);
                    band(c, m, axis, ua, su, va, sv, 0f, wv, color, light);
                }
            }
        }
    }

    /** Bande le long de {@code axis}, centree sur l'arete {@code (su, sv)}, de demi-largeur {@code (wu, wv)}. */
    private static void band(VertexConsumer c, Matrix4f m, int axis, int ua, float su, int va, float sv, float wu,
                             float wv, int color, int light)
    {
        float u0 = su - wu;
        float u1 = su + wu;
        float v0 = sv - wv;
        float v1 = sv + wv;
        vertex(c, m, axis, -1f, ua, u0, va, v0, color, light);
        vertex(c, m, axis, 1f, ua, u0, va, v0, color, light);
        vertex(c, m, axis, 1f, ua, u1, va, v1, color, light);
        vertex(c, m, axis, -1f, ua, u1, va, v1, color, light);
    }

    /** Sommet dont l'axe {@code a} vaut {@code x}, {@code b} vaut {@code y} et {@code d} vaut {@code z}. */
    private static void vertex(VertexConsumer c, Matrix4f m, int a, float x, int b, float y, int d, float z, int color,
                               int light)
    {
        float px = 0f;
        float py = 0f;
        float pz = 0f;
        switch (a) {
            case 0 -> px = x;
            case 1 -> py = x;
            default -> pz = x;
        }
        switch (b) {
            case 0 -> px = y;
            case 1 -> py = y;
            default -> pz = y;
        }
        switch (d) {
            case 0 -> px = z;
            case 1 -> py = z;
            default -> pz = z;
        }
        c.vertex(m, px, py, pz).color(color).uv2(light).endVertex();
    }
}
