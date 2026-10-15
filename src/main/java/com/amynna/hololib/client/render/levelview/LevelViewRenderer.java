package com.amynna.hololib.client.render.levelview;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.ShaderPacks;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;

import java.lang.reflect.Field;

/**
 * Rendu d'une {@link HoloLevelView} par {@code GameRenderer.renderLevel}, au debut de la frame (voir le package pour
 * les deux chemins, reduit et fenetre). Usage interne. Tout l'etat touche est restaure dans un {@code finally} ; une
 * exception pendant le rendu coupe toutes les vues (journalisee une fois) : l'etat de rendu vanilla n'est plus sur.
 */
final class LevelViewRenderer
{
    private static final Matrix4f IDENTITY = new Matrix4f();
    /** Plan lointain minimal d'une vue a distance reduite : soleil, lune et etoiles (~100 blocs) restent visibles. */
    private static final float MIN_FAR_PLANE = 112f;

    private static Field mainTarget;
    private static Field eyeHeight;
    private static Field eyeHeightOld;
    private static Field renderHand;
    private static Field renderBlockOutline;
    private static Field renderDistance;
    private static boolean resolved;
    private static boolean broken;

    /** Pile passee a {@code renderLevel}, remise a l'identite a chaque vue. */
    private static PoseStack pose = new PoseStack();
    /** Camera des vues a position fixe : entite client jamais ajoutee au niveau. */
    private static Marker marker;
    /** FOV vertical de la vue en cours (elargi pour le recadrage), lu par {@code RenderDispatcher}. */
    static float renderFov = 70f;
    /** Cible de la vue en cours dans le chemin reduit, {@code null} sinon (viewport a retablir). */
    static TextureTarget reducedTarget;
    /** Distance de vue de la vue en cours, en blocs ; 0 = celle du joueur (rien a imposer). */
    private static float viewDistance;

    private LevelViewRenderer() {}

    static void render(HoloLevelView view, float partialTick)
    {
        if (broken || !resolve()) {
            view.status = HoloLevelView.Status.NO_SIGNAL;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        int windowWidth = mc.getWindow().getWidth();
        int windowHeight = mc.getWindow().getHeight();
        if (level == null || windowWidth <= 0 || windowHeight <= 0) return;
        Entity cameraEntity = cameraEntity(view, level);
        if (cameraEntity == null) {
            view.status = HoloLevelView.Status.NO_SIGNAL;
            return;
        }

        RenderTarget main = mc.getMainRenderTarget();
        // Fabulous : la chaine de transparence est liee a la vraie cible principale.
        boolean reduced = !Minecraft.useShaderTransparency();
        int width = view.width();
        int height = view.height();
        float aspect = (float) width / height;
        float windowAspect = (float) windowWidth / windowHeight;
        // Fraction de l'image rendue gardee sur chaque axe (l'aspect de la projection est celui de la fenetre).
        float keepU;
        float keepV;
        if (reduced) {
            int targetWidth = width;
            int targetHeight = height;
            if (aspect <= windowAspect) targetWidth = Math.max(width, Math.round(height * windowAspect));
            else targetHeight = Math.max(height, Math.round(width / windowAspect));
            view.render = ensure(view.render, targetWidth, targetHeight, main);
            keepU = (float) width / targetWidth;
            keepV = (float) height / targetHeight;
        } else {
            view.render = ensure(view.render, width, height, main);
            keepU = aspect <= windowAspect ? aspect / windowAspect : 1f;
            keepV = aspect <= windowAspect ? 1f : windowAspect / aspect;
        }
        float halfFov = view.fov() * 0.5f * Mth.DEG_TO_RAD;
        renderFov = 2f * (float) Math.atan(Math.tan(halfFov) / keepV) * Mth.RAD_TO_DEG;

        GameRenderer gameRenderer = mc.gameRenderer;
        LevelRenderer levelRenderer = mc.levelRenderer;
        Camera camera = gameRenderer.getMainCamera();
        Entity savedCamera = mc.cameraEntity;
        HitResult savedHit = mc.hitResult;
        Entity savedPick = mc.crosshairPickEntity;
        CameraType savedType = mc.options.getCameraType();
        boolean ok = false;
        boolean swapped = false;
        boolean saved = false;
        float savedEye = 0f;
        float savedEyeOld = 0f;
        boolean savedHand = true;
        boolean savedOutline = true;
        float savedDistance = 0f;
        try
        {
            savedEye = eyeHeight.getFloat(camera);
            savedEyeOld = eyeHeightOld.getFloat(camera);
            savedHand = renderHand.getBoolean(gameRenderer);
            savedOutline = renderBlockOutline.getBoolean(gameRenderer);
            savedDistance = renderDistance.getFloat(gameRenderer);
            saved = true;

            float eye = view.entityMode() ? cameraEntity.getEyeHeight() : 0f;
            eyeHeight.setFloat(camera, eye);
            eyeHeightOld.setFloat(camera, eye);
            mc.cameraEntity = cameraEntity;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            gameRenderer.setRenderHand(false);
            gameRenderer.setRenderBlockOutline(false);
            if (reduced) {
                mainTarget.set(mc, view.render);
                swapped = true;
                reducedTarget = view.render;
                view.render.bindWrite(true);
            } else {
                main.bindWrite(true);
            }
            HoloLevelViews.setCurrent(view);
            viewDistance = viewDistanceBlocks(mc, view);
            float eyeOffset = view.entityMode() ? cameraEntity.getEyeHeight() : 0f;
            LevelViewCulling.begin(levelRenderer, Mth.lerp(partialTick, cameraEntity.xo, cameraEntity.getX()),
                    Mth.lerp(partialTick, cameraEntity.yo, cameraEntity.getY()) + eyeOffset,
                    Mth.lerp(partialTick, cameraEntity.zo, cameraEntity.getZ()));
            LevelViewSodium.begin();
            pose.setIdentity();
            // Roulis directement sur la pile : ComputeCameraAngles peut etre reecrit par un autre mod apres nous.
            // Vanilla y ajoute ensuite le roulis de l'evenement (rotations autour du meme axe : elles s'additionnent).
            if (!view.entityMode()) {
                float roll = view.roll() * Mth.DEG_TO_RAD;
                pose.last().pose().rotateZ(roll);
                pose.last().normal().rotateZ(roll);
            }
            mc.getProfiler().push("hololib_level_view");
            try {
                gameRenderer.renderLevel(partialTick, Util.getNanos(), pose);
            } finally {
                mc.getProfiler().pop();
            }
            ok = true;
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            broken = true;
            HoloLib.LOGGER.error("Vues secondaires : echec du rendu, vues coupees", e);
        }
        finally
        {
            LevelViewSodium.end();
            LevelViewCulling.end(levelRenderer);
            HoloLevelViews.setCurrent(null);
            viewDistance = 0f;
            reducedTarget = null;
            if (swapped) restoreMainTarget(mc, main);
            mc.cameraEntity = savedCamera;
            mc.hitResult = savedHit;
            mc.crosshairPickEntity = savedPick;
            mc.options.setCameraType(savedType);
            if (saved) restoreCamera(camera, gameRenderer, savedEye, savedEyeOld, savedHand, savedOutline,
                    savedDistance);
            if (!pose.clear()) pose = new PoseStack();
            main.bindWrite(true);
        }
        if (!ok) {
            view.status = HoloLevelView.Status.NO_SIGNAL;
            return;
        }

        float uMin = (1f - keepU) * 0.5f;
        float vMin = (1f - keepV) * 0.5f;
        if (!reduced) {
            copy(main, view.render, uMin, vMin, 1f - uMin, 1f - vMin);
            uMin = 0f;
            vMin = 0f;
        }
        TextureTarget shown = view.render;
        view.render = view.display;
        view.display = shown;
        // Origine des textures GL en bas : v0 (haut de l'image) = vMax.
        view.u0 = uMin;
        view.u1 = 1f - uMin;
        view.v0 = 1f - vMin;
        view.v1 = vMin;
        view.status = HoloLevelView.Status.OK;
    }

    /** Distance de vue en blocs a imposer, 0 si celle du joueur (vue sans distance, ou distance au moins egale). */
    private static float viewDistanceBlocks(Minecraft mc, HoloLevelView view)
    {
        int chunks = view.viewDistance();
        int player = mc.options.getEffectiveRenderDistance();
        return chunks <= 0 || chunks >= player ? 0f : chunks * 16f;
    }

    /**
     * {@code ComputeFov} du monde pendant une vue a distance reduite : {@code GameRenderer.renderDistance}, deja ecrit
     * par {@code renderLevel} et lu ensuite pour le plan lointain (4 x) et le brouillard. Plan lointain = max(distance,
     * {@value #MIN_FAR_PLANE}) blocs : sections, block entities et entites au-dela ne sont pas dessinees. Avec un
     * shaderpack, valeur = distance (brouillard du pack a la distance, plan lointain a 4 x).
     */
    static void applyViewDistance()
    {
        if (viewDistance <= 0f) return;
        float value = ShaderPacks.inUse() ? viewDistance : Math.max(viewDistance, MIN_FAR_PLANE) / 4f;
        try
        {
            renderDistance.setFloat(Minecraft.getInstance().gameRenderer, value);
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            broken = true;
            HoloLib.LOGGER.error("Vues secondaires : distance de vue non appliquee, vues coupees", e);
        }
    }

    /**
     * {@code RenderFog} pendant une vue a distance reduite, sans shaderpack : brouillard du terrain et du ciel calcule
     * comme vanilla a partir de la distance de vue (la distance de rendu du jeu a ete abaissee pour le plan lointain).
     * Brouillards speciaux (fluide, aveuglement, obscurite) laisses a vanilla.
     */
    static void applyFog(ViewportEvent.RenderFog event)
    {
        float d = viewDistance;
        if (d <= 0f || ShaderPacks.inUse() || event.getType() != FogType.NONE) return;
        Camera camera = event.getCamera();
        if (camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = camera.getPosition();
        boolean foggy = mc.level != null && mc.level.effects().isFoggyAt(Mth.floor(eye.x), Mth.floor(eye.y))
                || mc.gui.getBossOverlay().shouldCreateWorldFog();
        if (foggy) {
            event.setNearPlaneDistance(d * 0.05f);
            event.setFarPlaneDistance(Math.min(d, 192f) * 0.5f);
            event.setFogShape(FogShape.SPHERE);
        } else if (event.getMode() == FogRenderer.FogMode.FOG_SKY) {
            event.setNearPlaneDistance(0f);
            event.setFarPlaneDistance(d);
            event.setFogShape(FogShape.CYLINDER);
        } else {
            event.setNearPlaneDistance(d - Mth.clamp(d / 10f, 4f, 64f));
            event.setFarPlaneDistance(d);
            event.setFogShape(FogShape.CYLINDER);
        }
        event.setCanceled(true);
    }

    /** Viewport de la cible du chemin reduit (une passe post-effet le remet a la taille de la fenetre). */
    static void fixViewport()
    {
        TextureTarget target = reducedTarget;
        if (target != null) RenderSystem.viewport(0, 0, target.width, target.height);
    }

    /** Entite camera : celle de la vue (presente dans le niveau), ou le marker place sur la camera fixe. */
    private static Entity cameraEntity(HoloLevelView view, ClientLevel level)
    {
        if (view.entityMode()) {
            Entity entity = view.entity();
            if (valid(entity, level)) return entity;
            if (view.player() == null) return null;
            entity = level.getPlayerByUUID(view.player());
            view.resolvedEntity(entity);
            return valid(entity, level) ? entity : null;
        }
        if (marker == null || marker.level() != level) {
            marker = EntityType.MARKER.create(level);
            if (marker == null) return null;
        }
        marker.setPos(view.x(), view.y(), view.z());
        marker.xo = view.x();
        marker.yo = view.y();
        marker.zo = view.z();
        marker.setYRot(view.yaw());
        marker.yRotO = view.yaw();
        marker.setXRot(view.pitch());
        marker.xRotO = view.pitch();
        return marker;
    }

    private static boolean valid(Entity entity, ClientLevel level)
    {
        return entity != null && !entity.isRemoved() && entity.level() == level;
    }

    private static TextureTarget ensure(TextureTarget target, int width, int height, RenderTarget main)
    {
        if (target == null) {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            target.setClearColor(0f, 0f, 0f, 1f);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        }
        if (main.isStencilEnabled() && !target.isStencilEnabled()) target.enableStencil();
        return target;
    }

    /**
     * Chemin fenetre : rectangle {@code (u0, v0)-(u1, v1)} de la couleur de {@code source} (coordonnees GL, origine en
     * bas) etire sur toute la cible {@code dest}. Etat restaure, cible principale liee a la fin.
     */
    private static void copy(RenderTarget source, TextureTarget dest, float u0, float v0, float u1, float v1)
    {
        dest.bindWrite(true);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(IDENTITY, VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try
        {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, source.getColorTextureId());
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.vertex(-1f, -1f, 0f).uv(u0, v0).endVertex();
            builder.vertex(1f, -1f, 0f).uv(u1, v0).endVertex();
            builder.vertex(1f, 1f, 0f).uv(u1, v1).endVertex();
            builder.vertex(-1f, 1f, 0f).uv(u0, v1).endVertex();
            BufferUploader.drawWithShader(builder.end());
        }
        finally
        {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            source.bindWrite(true);
        }
    }

    private static void restoreMainTarget(Minecraft mc, RenderTarget main)
    {
        try
        {
            mainTarget.set(mc, main);
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            // Impossible en pratique (le meme champ vient d'etre ecrit) ; le jeu ne peut plus rien afficher.
            throw new IllegalStateException("Vues secondaires : cible principale non restauree", e);
        }
    }

    private static void restoreCamera(Camera camera, GameRenderer gameRenderer, float eye, float eyeOld,
                                      boolean hand, boolean outline, float distance)
    {
        try
        {
            eyeHeight.setFloat(camera, eye);
            eyeHeightOld.setFloat(camera, eyeOld);
            renderDistance.setFloat(gameRenderer, distance);
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            broken = true;
            HoloLib.LOGGER.error("Vues secondaires : camera non restauree, vues coupees", e);
        }
        gameRenderer.setRenderHand(hand);
        gameRenderer.setRenderBlockOutline(outline);
    }

    private static boolean resolve()
    {
        if (resolved) return !broken;
        resolved = true;
        try
        {
            mainTarget = ObfuscationReflectionHelper.findField(Minecraft.class, "f_91042_");
            eyeHeight = ObfuscationReflectionHelper.findField(Camera.class, "f_90562_");
            eyeHeightOld = ObfuscationReflectionHelper.findField(Camera.class, "f_90563_");
            renderHand = ObfuscationReflectionHelper.findField(GameRenderer.class, "f_109070_");
            renderBlockOutline = ObfuscationReflectionHelper.findField(GameRenderer.class, "f_109071_");
            renderDistance = ObfuscationReflectionHelper.findField(GameRenderer.class, "f_109062_");
        }
        catch (RuntimeException e)
        {
            broken = true;
            HoloLib.LOGGER.error("Vues secondaires : champs vanilla introuvables, vues coupees", e);
        }
        return !broken;
    }
}
