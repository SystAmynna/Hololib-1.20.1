package com.amynna.hololib.client.render.sky;

import com.amynna.hololib.HoloLib;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Passe des {@link SkyRenderer}, appelee par {@code RenderDispatcher} a {@code AFTER_SKY} (ciel dessine sans ecrire la
 * profondeur, terrain pas encore dessine). Usage interne.
 * <p>
 * Les objets du ciel ecrivent la profondeur entre eux ; si au moins un renderer a dessine, la profondeur est videe a la
 * fin pour que le terrain et le reste du monde passent devant.
 */
public final class SkyRenderPass
{
    private static final SkyRenderContext CONTEXT = new SkyRenderContext();
    private static PoseStack pose = new PoseStack();

    private SkyRenderPass() {}

    /** @param cameraRotation rotation camera de la frame (PoseStack de {@code AFTER_SKY}, non modifiee) */
    public static void render(List<SkyRenderer> renderers, Camera camera, float partialTick, Matrix4fc cameraRotation,
                              Matrix3fc cameraNormal)
    {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (renderers.isEmpty() || level == null) return;
        CONTEXT.set(level, camera, partialTick, skyVisible(mc, level, camera), mc.gameRenderer.getDepthFar());
        boolean drawn = false;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        try
        {
            for (int i = 0; i < renderers.size(); i++)
            {
                SkyRenderer renderer = renderers.get(i);
                if (renderer.hiddenWithSky() && !CONTEXT.skyVisible()) continue;
                if (!renderer.shouldRender(CONTEXT)) continue;

                if (!drawn)
                {
                    drawn = true;
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                }
                PoseStack.Pose last = pose.last();
                last.pose().set(cameraRotation);
                last.normal().set(cameraNormal);
                try
                {
                    renderer.render(pose, buffers, CONTEXT);
                }
                finally
                {
                    buffers.endBatch();
                    if (!pose.clear())
                    {
                        HoloLib.LOGGER.error("SkyRenderer {} a laisse sa PoseStack desequilibree", renderer);
                        pose = new PoseStack();
                    }
                }
            }
        }
        finally
        {
            if (drawn) RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }
    }

    /** Memes conditions que {@code LevelRenderer.renderSky} pour ne pas dessiner le ciel. */
    private static boolean skyVisible(Minecraft mc, ClientLevel level, Camera camera)
    {
        if (level.effects().skyType() == DimensionSpecialEffects.SkyType.NONE) return false;
        FogType fluid = camera.getFluidInCamera();
        if (fluid == FogType.LAVA || fluid == FogType.POWDER_SNOW) return false;
        if (camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) return false;
        Vec3 p = camera.getPosition();
        // Vanilla passe (x, y) a isFoggyAt, pas (x, z) : on reproduit son comportement.
        return !level.effects().isFoggyAt(Mth.floor(p.x), Mth.floor(p.y))
                && !mc.gui.getBossOverlay().shouldCreateWorldFog();
    }
}
