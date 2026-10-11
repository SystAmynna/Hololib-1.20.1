package com.amynna.hololib.client.render.cancel;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.lwjgl.opengl.GL11;

/**
 * Ciel annule avec un shaderpack : le pack recalcule son ciel sur les pixels de profondeur 1, annuler
 * {@code renderSky} ne suffit donc pas. Appele par {@code RenderDispatcher} a {@code AFTER_LEVEL} (cible principale,
 * scene composee), avant les {@code SkyRenderer} : quad plein ecran opaque a la profondeur 1 (matrices identite, test
 * {@code LEQUAL} sans ecriture), dans la couleur du brouillard de la frame, comme le fond vide sans shaderpack. Terrain,
 * nuages et main restent devant. Etat restaure. Usage interne.
 */
public final class SkyCancelFill
{
    private static final Matrix4f IDENTITY = new Matrix4f();

    private SkyCancelFill() {}

    public static void render(Vector3fc fogColor)
    {
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(IDENTITY, VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try
        {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            float r = fogColor.x();
            float g = fogColor.y();
            float b = fogColor.z();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            builder.vertex(-1f, -1f, 1f).color(r, g, b, 1f).endVertex();
            builder.vertex(1f, -1f, 1f).color(r, g, b, 1f).endVertex();
            builder.vertex(1f, 1f, 1f).color(r, g, b, 1f).endVertex();
            builder.vertex(-1f, 1f, 1f).color(r, g, b, 1f).endVertex();
            BufferUploader.drawWithShader(builder.end());
        }
        finally
        {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }
}
