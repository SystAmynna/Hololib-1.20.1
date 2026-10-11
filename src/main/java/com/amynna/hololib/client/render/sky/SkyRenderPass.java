package com.amynna.hololib.client.render.sky;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.HoloBuffers;
import com.amynna.hololib.client.render.StableProjection;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
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
 * Passe des {@link SkyRenderer}, appelee par {@code RenderDispatcher}. Usage interne.
 * <ul>
 *     <li>Sans shaderpack ({@link #render}) : a {@code AFTER_SKY} (ciel dessine sans ecrire la profondeur, terrain pas
 *     encore dessine). Les objets du ciel ecrivent la profondeur entre eux ; si au moins un renderer a dessine, la
 *     profondeur est videe a la fin pour que le terrain et le reste du monde passent devant.</li>
 *     <li>Avec un shaderpack ({@link #renderDeferred}) : a {@code AFTER_LEVEL}, apres la composition d'Oculus (dans
 *     les gbuffers, un shader vanilla non remplace n'ecrit rien, et le pack recalcule son ciel sur les pixels de
 *     profondeur 1). Les renderers dessinent dans une cible hors ecran (couleur + profondeur, videe a (0,0,0,0)),
 *     composee ensuite sur la cible principale par un quad plein ecran a la profondeur 1 (test {@code LEQUAL}) : seuls
 *     les pixels de ciel sont couverts, terrain, nuages et main restent devant.</li>
 * </ul>
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
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static PoseStack pose = new PoseStack();
    /** Cible hors ecran du mode differe, creee au premier besoin. */
    private static TextureTarget target;

    private SkyRenderPass() {}

    /**
     * Mode direct ({@code AFTER_SKY}, sans shaderpack).
     *
     * @param cameraRotation  rotation camera de la frame, sans bobbing
     * @param worldProjection projection du monde de la frame (bobbing compris)
     * @param fov             FOV effectif du monde pour la frame, en degres
     */
    public static void render(List<SkyRenderer> renderers, Camera camera, float partialTick, Matrix4fc cameraRotation,
                              Matrix3fc cameraNormal, Matrix4fc worldProjection, float fov)
    {
        draw(renderers, camera, partialTick, cameraRotation, cameraNormal, worldProjection, fov, false);
    }

    /** Mode differe ({@code AFTER_LEVEL}, shaderpack actif, cible principale liee) ; memes parametres. */
    public static void renderDeferred(List<SkyRenderer> renderers, Camera camera, float partialTick,
                                      Matrix4fc cameraRotation, Matrix3fc cameraNormal, Matrix4fc worldProjection,
                                      float fov)
    {
        draw(renderers, camera, partialTick, cameraRotation, cameraNormal, worldProjection, fov, true);
    }

    private static void draw(List<SkyRenderer> renderers, Camera camera, float partialTick, Matrix4fc cameraRotation,
                             Matrix3fc cameraNormal, Matrix4fc worldProjection, float fov, boolean deferred)
    {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (renderers.isEmpty() || level == null) return;
        CONTEXT.set(level, camera, partialTick, skyVisible(mc, level, camera), mc.gameRenderer.getDepthFar());
        boolean drawn = false;
        MultiBufferSource.BufferSource buffers = HoloBuffers.source();
        try
        {
            for (SkyRenderer renderer : renderers) {
                if (renderer.hiddenWithSky() && !CONTEXT.skyVisible()) continue;
                if (!renderer.shouldRender(CONTEXT)) continue;

                if (!drawn) {
                    drawn = true;
                    if (deferred) bindTarget(mc.getMainRenderTarget());
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
                if (deferred) {
                    mc.getMainRenderTarget().bindWrite(true);
                    composite();
                } else {
                    RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                }
            }
        }
    }

    /** Cible hors ecran a la taille de la cible principale, videe a (0,0,0,0) et liee. */
    private static void bindTarget(RenderTarget main)
    {
        if (target == null) {
            target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            target.setClearColor(0f, 0f, 0f, 0f);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
    }

    /**
     * Quad plein ecran a la profondeur 1 (matrices identite : z NDC = 1 exactement), test {@code LEQUAL} sans ecriture :
     * ne couvre que les pixels de ciel. Melange premultiplie : la cible a ete remplie par des {@code RenderType}
     * translucides sur un fond (0,0,0,0), sa couleur est deja multipliee par son alpha. Etat restaure.
     */
    private static void composite()
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
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, target.getColorTextureId());
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.vertex(-1f, -1f, 1f).uv(0f, 0f).endVertex();
            builder.vertex(1f, -1f, 1f).uv(1f, 0f).endVertex();
            builder.vertex(1f, 1f, 1f).uv(1f, 1f).endVertex();
            builder.vertex(-1f, 1f, 1f).uv(0f, 1f).endVertex();
            BufferUploader.drawWithShader(builder.end());
        }
        finally
        {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
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
