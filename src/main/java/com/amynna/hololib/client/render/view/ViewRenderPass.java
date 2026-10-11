package com.amynna.hololib.client.render.view;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.HoloBuffers;
import com.amynna.hololib.client.render.StableProjection;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Passe des {@link ViewRenderer}, appelee par {@code RenderDispatcher} a {@code AFTER_LEVEL} (cible principale liee,
 * monde compose, main et GUI pas encore dessines). Usage interne.
 * <p>
 * Au premier renderer actif : profondeur videe (comme vanilla avant la main), fog neutralise, model-view a l'identite ;
 * tout est restaure a la fin, y compris si un renderer leve une exception.
 */
public final class ViewRenderPass
{
    private static final ViewRenderContext CONTEXT = new ViewRenderContext();
    private static final Matrix4f STABLE_PROJECTION = new Matrix4f();
    private static PoseStack pose = new PoseStack();

    private ViewRenderPass() {}

    /**
     * @param cameraRotation  rotation camera capturee a {@code AFTER_SKY}
     * @param worldProjection projection du monde de la frame (bobbing compris)
     * @param fov             FOV effectif du monde pour la frame, en degres
     */
    public static void render(List<ViewRenderer> renderers, Camera camera, float partialTick, Matrix4fc cameraRotation,
                              Matrix3fc cameraNormal, Matrix4f worldProjection, float fov)
    {
        if (renderers.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float aspect = (float) mc.getWindow().getWidth() / (float) mc.getWindow().getHeight();
        CONTEXT.set(camera, partialTick, cameraRotation, cameraNormal, aspect);
        boolean prepared = false;
        float fogStart = 0f;
        float fogEnd = 0f;
        MultiBufferSource.BufferSource buffers = HoloBuffers.source();
        try
        {
            for (ViewRenderer renderer : renderers) {
                if (renderer.hiddenWithGui() && mc.options.hideGui) continue;
                boolean stable = renderer.projection() == ViewRenderer.Projection.STABLE;
                float rendererFov = stable ? StableProjection.configuredFov() : fov;
                Matrix4f projection = stable ? StableProjection.set(STABLE_PROJECTION, rendererFov) : worldProjection;
                CONTEXT.projection(projection, rendererFov);
                if (!renderer.shouldRender(CONTEXT)) continue;

                if (!prepared) {
                    prepared = true;
                    fogStart = RenderSystem.getShaderFogStart();
                    fogEnd = RenderSystem.getShaderFogEnd();
                    prepare();
                }
                RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
                PoseStack.Pose last = pose.last();
                last.pose().identity();
                last.normal().identity();
                try {
                    renderer.render(pose, buffers, CONTEXT);
                } finally {
                    buffers.endBatch();
                    if (!pose.clear()) {
                        HoloLib.LOGGER.error("ViewRenderer {} a laisse sa PoseStack desequilibree", renderer);
                        pose = new PoseStack();
                    }
                }
            }
        }
        finally
        {
            if (prepared) restore(fogStart, fogEnd);
        }
    }

    private static void prepare()
    {
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.backupProjectionMatrix();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
    }

    private static void restore(float fogStart, float fogEnd)
    {
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }
}
