package com.amynna.hololib.client.render.modules.camera.attackguard;

import com.amynna.hololib.client.modules.camera.attackguard.AttackGuardClient;
import com.amynna.hololib.client.render.hud.HudRenderContext;
import com.amynna.hololib.client.render.hud.HudRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Croix rouge d'Attack Guard au centre de l'ecran, a la place du reticule vanilla (annule par
 * {@code AttackGuardClient}) tant que le joueur local vise directement un joueur protege ; premiere personne seulement,
 * comme le reticule. Deux quads diagonaux ({@code RenderType.gui()}), {@value #HALF} px de demi-cote.
 */
final class AttackGuardCrossRenderer implements HudRenderer
{
    private static final float HALF = 5f;
    private static final float THICKNESS = 1.5f;
    private static final int COLOR = 0xFFFF3030;

    @Override
    public boolean shouldRender(HudRenderContext ctx)
    {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && mc.options.getCameraType().isFirstPerson() && AttackGuardClient.protectedInSight();
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, HudRenderContext ctx)
    {
        Matrix4f matrix = pose.last().pose();
        VertexConsumer c = buffers.getBuffer(RenderType.gui());
        float cx = ctx.width() / 2f;
        float cy = ctx.height() / 2f;
        // Decalage perpendiculaire a une diagonale : (t / 2) / sqrt(2) sur chaque axe.
        float o = THICKNESS * 0.35355f;
        quad(c, matrix, cx - HALF - o, cy - HALF + o, cx + HALF - o, cy + HALF + o, cx + HALF + o, cy + HALF - o,
                cx - HALF + o, cy - HALF - o);
        quad(c, matrix, cx + HALF - o, cy - HALF - o, cx - HALF - o, cy + HALF - o, cx - HALF + o, cy + HALF + o,
                cx + HALF + o, cy - HALF + o);
    }

    private static void quad(VertexConsumer c, Matrix4f matrix, float ax, float ay, float bx, float by, float cx,
                             float cy, float dx, float dy)
    {
        vertex(c, matrix, ax, ay);
        vertex(c, matrix, bx, by);
        vertex(c, matrix, cx, cy);
        vertex(c, matrix, dx, dy);
    }

    private static void vertex(VertexConsumer c, Matrix4f matrix, float x, float y)
    {
        c.vertex(matrix, x, y, 0f).color(COLOR >> 16 & 0xFF, COLOR >> 8 & 0xFF, COLOR & 0xFF, COLOR >>> 24)
                .endVertex();
    }
}
