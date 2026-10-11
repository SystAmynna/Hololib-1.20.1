package com.amynna.hololib.client.render.world;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.HoloBuffers;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.List;

/**
 * Passe des {@link WorldRenderer}, appelee par {@code RenderDispatcher}. Usage interne. Les vertices vont dans
 * {@link HoloBuffers} (ordre d'emission respecte), vide a la fin de la passe.
 * <ul>
 *     <li>Sans shaderpack ({@link #render}) : a {@code AFTER_TRANSLUCENT_BLOCKS}, quand entites et block entities sont
 *     dessinees (avec Oculus, elles ne le sont qu'au profiler {@code "translucent"}, apres {@code AFTER_ENTITIES}). En
 *     Fabulous la cible translucide est liee a ce stage : on dessine dans la cible principale, et la composition de
 *     Fabulous passe l'eau devant ou derriere.</li>
 *     <li>Avec un shaderpack ({@link #renderOverlay}) : a {@code AFTER_LEVEL}, apres la composition d'Oculus (dans les
 *     gbuffers, un shader vanilla non remplace par Oculus n'ecrit ni couleur ni profondeur). Projection et pose du
 *     monde, test contre la profondeur de la scene (main et translucides compris), sans eclairage du pack.</li>
 * </ul>
 */
public final class WorldRenderPass
{
    private static final WorldRenderContext CONTEXT = new WorldRenderContext();
    private static PoseStack pose = new PoseStack();

    private WorldRenderPass() {}

    /** @param levelPose PoseStack de l'evenement (rotation camera), rendu dans l'etat recu */
    public static void render(List<WorldRenderer<?>> renderers, PoseStack levelPose, Camera camera, Frustum frustum,
                              float partialTick)
    {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (renderers.isEmpty() || level == null) return;
        CONTEXT.set(level, camera, frustum, partialTick);
        RenderTarget translucent = Minecraft.useShaderTransparency() ? mc.levelRenderer.getTranslucentTarget() : null;
        if (translucent != null) mc.getMainRenderTarget().bindWrite(false);
        try
        {
            draw(renderers, levelPose);
        }
        finally
        {
            if (translucent != null) translucent.bindWrite(false);
        }
    }

    /**
     * Mode superposition (shaderpack actif), cible principale liee.
     *
     * @param cameraRotation  rotation camera de la frame, sans bobbing
     * @param worldProjection projection du monde de la frame, bobbing compris
     */
    public static void renderOverlay(List<WorldRenderer<?>> renderers, Matrix4fc cameraRotation, Matrix3fc cameraNormal,
                                     Matrix4f worldProjection, Camera camera, Frustum frustum, float partialTick)
    {
        ClientLevel level = Minecraft.getInstance().level;
        if (renderers.isEmpty() || level == null) return;
        CONTEXT.set(level, camera, frustum, partialTick);
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(worldProjection, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        PoseStack.Pose last = pose.last();
        last.pose().set(cameraRotation);
        last.normal().set(cameraNormal);
        try
        {
            draw(renderers, pose);
        }
        finally
        {
            if (!pose.clear())
            {
                HoloLib.LOGGER.error("WorldRenderer : PoseStack desequilibree en mode superposition");
                pose = new PoseStack();
            }
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static void draw(List<WorldRenderer<?>> renderers, PoseStack pose)
    {
        MultiBufferSource.BufferSource buffers = HoloBuffers.source();
        try
        {
            for (WorldRenderer<?> renderer : renderers) renderer.renderAll(pose, buffers, CONTEXT);
        }
        finally
        {
            buffers.endBatch();
        }
    }
}
