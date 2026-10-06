package com.amynna.hololib.client.render.sky;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.StableProjection;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
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
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Passe des {@link SkyRenderer}, appelee par {@code RenderDispatcher} a {@code AFTER_SKY} (ciel dessine sans ecrire la
 * profondeur, terrain pas encore dessine). Usage interne.
 * <p>
 * Les objets du ciel ecrivent la profondeur entre eux ; si au moins un renderer a dessine, la profondeur est videe a la
 * fin pour que le terrain et le reste du monde passent devant.
 * <p>
 * Projection : celle du monde <b>sans la translation du bobbing</b>. Vanilla deplace l'oeil d'environ un demi-bloc en
 * marchant : negligeable pour le soleil (quasi a l'infini) mais forte parallaxe pour un objet proche de l'oeil. Les
 * rotations (bobbing, secousse de degats, nausee) et le FOV effectif sont conserves : les objets du ciel suivent le
 * terrain comme le soleil et la lune. Restauree a la fin.
 */
public final class SkyRenderPass
{
    private static final SkyRenderContext CONTEXT = new SkyRenderContext();
    /** Perspective vanilla sans bobbing, partie bobbing de la projection du monde, projection du ciel. */
    private static final Matrix4f CLEAN = new Matrix4f();
    private static final Matrix4f BOB = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static PoseStack pose = new PoseStack();

    private SkyRenderPass() {}

    /**
     * @param cameraRotation rotation camera de la frame (PoseStack de {@code AFTER_SKY}, non modifiee)
     * @param worldProjection projection du monde de la frame (bobbing compris)
     * @param fov            FOV effectif du monde pour la frame, en degres
     */
    public static void render(List<SkyRenderer> renderers, Camera camera, float partialTick, Matrix4fc cameraRotation,
                              Matrix3fc cameraNormal, Matrix4fc worldProjection, float fov)
    {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (renderers.isEmpty() || level == null) return;
        CONTEXT.set(level, camera, partialTick, skyVisible(mc, level, camera), mc.gameRenderer.getDepthFar());
        boolean drawn = false;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        try
        {
            for (SkyRenderer renderer : renderers) {
                if (renderer.hiddenWithSky() && !CONTEXT.skyVisible()) continue;
                if (!renderer.shouldRender(CONTEXT)) continue;

                if (!drawn) {
                    drawn = true;
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    RenderSystem.backupProjectionMatrix();
                    RenderSystem.setProjectionMatrix(infiniteProjection(worldProjection, fov),
                            VertexSorting.DISTANCE_TO_ORIGIN);
                }
                PoseStack.Pose last = pose.last();
                last.pose().set(cameraRotation);
                last.normal().set(cameraNormal);
                try {
                    renderer.render(pose, buffers, CONTEXT);
                } finally {
                    buffers.endBatch();
                    if (!pose.clear()) {
                        HoloLib.LOGGER.error("SkyRenderer {} a laisse sa PoseStack desequilibree", renderer);
                        pose = new PoseStack();
                    }
                }
            }
        }
        finally
        {
            if (drawn) {
                RenderSystem.restoreProjectionMatrix();
                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            }
        }
    }

    /**
     * Projection du monde privee de la translation du bobbing : {@code monde = P * T * R}, d'ou
     * {@code P * R = P * (P^-1 * monde sans translation)}.
     */
    private static Matrix4f infiniteProjection(Matrix4fc worldProjection, float fov)
    {
        StableProjection.set(CLEAN, fov);
        CLEAN.invertPerspective(BOB).mul(worldProjection).setTranslation(0f, 0f, 0f);
        return CLEAN.mul(BOB, PROJECTION);
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
