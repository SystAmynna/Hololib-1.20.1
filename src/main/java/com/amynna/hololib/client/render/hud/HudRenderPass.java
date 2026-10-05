package com.amynna.hololib.client.render.hud;

import com.amynna.hololib.HoloLib;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Passe des {@link HudRenderer}, appelee par {@code RenderDispatcher} depuis l'overlay du mod, enregistre sous tous
 * les overlays vanilla. Usage interne.
 * <p>
 * Si au moins un renderer a dessine, la profondeur est videe a la fin : le HUD du jeu, dessine ensuite, passe toujours
 * devant, meme un objet 3D epais.
 */
public final class HudRenderPass
{
    private static final HudRenderContext CONTEXT = new HudRenderContext();

    private HudRenderPass() {}

    public static void render(List<HudRenderer> renderers, GuiGraphics graphics, float partialTick, int width,
                              int height)
    {
        if (renderers.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        CONTEXT.set(graphics, partialTick, width, height, mc.getWindow().getGuiScale());
        boolean drawn = false;
        PoseStack pose = graphics.pose();
        MultiBufferSource.BufferSource buffers = graphics.bufferSource();
        try
        {
            for (HudRenderer renderer : renderers) {
                if (!renderer.shouldRender(CONTEXT)) continue;
                drawn = true;
                pose.pushPose();
                PoseStack.Pose entry = pose.last();
                try {
                    renderer.render(pose, buffers, CONTEXT);
                } finally {
                    buffers.endBatch();
                    if (pose.last() != entry) {
                        // Pile du GUI partagee : on ne corrige que l'exces de pushPose.
                        HoloLib.LOGGER.error("HudRenderer {} a laisse sa PoseStack desequilibree", renderer);
                        for (int i = 0; i < 64 && pose.last() != entry && !pose.clear(); i++) pose.popPose();
                    }
                    pose.popPose();
                }
            }
        }
        finally
        {
            if (drawn) RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }
    }
}
