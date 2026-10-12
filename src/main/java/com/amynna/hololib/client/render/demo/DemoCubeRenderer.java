package com.amynna.hololib.client.render.demo;

import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.demo.DemoCube;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Dessine un {@link DemoCube} : six faces translucides double face, centrees sur l'origine, tournant autour de l'axe
 * vertical avec une inclinaison fixe. Chaque face est plus ou moins assombrie pour lire le volume.
 */
final class DemoCubeRenderer implements HoloObjectRenderer<DemoCube>
{
    /** Inclinaison du cube, en radians, pour montrer le dessus. */
    private static final float TILT = 25f * Mth.DEG_TO_RAD;

    private final Quaternionf rotation = new Quaternionf();

    @Override
    public void render(DemoCube cube, PoseStack pose, MultiBufferSource buffers, int packedLight, HoloDrawContext ctx)
    {
        pose.pushPose();
        pose.mulPose(rotation.rotationY(cube.spin(ctx.partialTick()) * Mth.DEG_TO_RAD).rotateX(TILT));
        float h = cube.size() * 0.5f;
        int color = cube.color().resolve(HoloThemes.resolve(cube));
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        // Haut, bas, puis les quatre cotes.
        quad(vc, m, packedLight, color, 1.0f, -h, h, -h, -h, h, h, h, h, h, h, h, -h);
        quad(vc, m, packedLight, color, 0.5f, -h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h);
        quad(vc, m, packedLight, color, 0.8f, -h, -h, h, h, -h, h, h, h, h, -h, h, h);
        quad(vc, m, packedLight, color, 0.8f, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h);
        quad(vc, m, packedLight, color, 0.65f, h, -h, h, h, -h, -h, h, h, -h, h, h, h);
        quad(vc, m, packedLight, color, 0.65f, -h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h);
        pose.popPose();
    }

    /** Quad aux quatre coins donnes, couleur assombrie par {@code shade}. */
    private static void quad(VertexConsumer vc, Matrix4f m, int light, int argb, float shade,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3)
    {
        int a = argb >>> 24;
        int r = (int) ((argb >> 16 & 0xFF) * shade);
        int g = (int) ((argb >> 8 & 0xFF) * shade);
        int b = (int) ((argb & 0xFF) * shade);
        vc.vertex(m, x0, y0, z0).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y1, z1).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x2, y2, z2).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x3, y3, z3).color(r, g, b, a).uv2(light).endVertex();
    }
}
