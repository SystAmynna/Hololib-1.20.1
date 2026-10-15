package com.amynna.hololib.client.render.modules.camera.radar;

import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.modules.camera.radar.RadarClient;
import com.amynna.hololib.client.modules.camera.radar.RadarScan;
import com.amynna.hololib.client.render.outline.HoloOutlineMask;
import com.amynna.hololib.client.render.levelview.HoloLevelViews;
import com.amynna.hololib.client.render.world.WorldRenderContext;
import com.amynna.hololib.client.render.world.WorldRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Radar du joueur local ({@link RadarClient#scan()}) : une instance, placee a l'origine de l'onde, dessinee tant que
 * le radar est actif. Famille {@code WorldRenderer} : a {@code AFTER_TRANSLUCENT_BLOCKS} (cible principale, meme en
 * Fabulous), ou a {@code AFTER_LEVEL} sur l'image composee avec un shaderpack.
 * <ol>
 *     <li>copie de la profondeur de la scene ({@link HoloOutlineMask}, utilitaire partage du contour) ;</li>
 *     <li>masque : profondeur avant des entites reperees, redessinees par {@code EntityRenderDispatcher} ;</li>
 *     <li>composition plein ecran ({@code hololib:radar_composite}, sans test de profondeur) : trait de l'onde sur la
 *     scene au rayon (bord de la sphere seulement, interieur intact), interieur bleu des entites la ou la scene est
 *     devant elles, contour bleu autour du masque ;</li>
 *     <li>sphere de l'onde en triangles ({@link RadarSphere}), translucide, testee contre la profondeur du monde
 *     (cachee par les blocs) : ses segments s'amincissent avec le rayon jusqu'a disparaitre au rayon maximal, ses
 *     faces s'estompent au meme rythme.</li>
 * </ol>
 * Le lot en attente du tampon du mod est envoye avant la composition (ordre de dessin). Etat restaure : cible
 * principale liee, profondeur, melange, textures 0 et 1 du shader. Aucune allocation par frame (hors entites
 * dessinees par leurs propres renderers).
 */
final class RadarRenderer extends WorldRenderer<RadarScan>
{
    private static final List<RadarScan> ONE = List.of(RadarClient.scan());
    private static final List<RadarScan> NONE = List.of();

    /** Onde et sphere : couleur principale du theme standard du joueur ; entites reperees : secondaire. */
    private static final HoloColor WAVE = HoloColor.primary(0xFF);
    private static final HoloColor MARK = HoloColor.secondary(0xFF);
    private static final float FILL_ALPHA = 0.45f;
    /** Demi-largeur du trait de l'onde sur la scene, en blocs. */
    private static final float FRONT = 0.6f;
    /** Largeur des segments de la sphere au depart, en blocs ; nulle au rayon maximal. */
    private static final float EDGE_WIDTH = 0.4f;
    private static final int FACE_ALPHA = 0x14;
    private static final int EDGE_ALPHA = 0x60;

    private final HoloOutlineMask mask = new HoloOutlineMask();
    private final Matrix4f inverse = new Matrix4f();

    private static float red(int rgb) { return (rgb >> 16 & 0xFF) / 255f; }

    private static float green(int rgb) { return (rgb >> 8 & 0xFF) / 255f; }

    private static float blue(int rgb) { return (rgb & 0xFF) / 255f; }

    @Override
    protected List<? extends RadarScan> instances() { return RadarClient.scan().active() ? ONE : NONE; }

    @Override
    protected boolean shouldRender(RadarScan scan, Vec3 position, WorldRenderContext ctx)
    {
        // Effet personnel du joueur local : absent des vues secondaires.
        return HoloLevelViews.current() == null;
    }

    @Override
    protected int packedLight(RadarScan scan, Vec3 position, WorldRenderContext ctx)
    {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    protected void render(RadarScan scan, PoseStack pose, MultiBufferSource buffers, int packedLight,
                          WorldRenderContext ctx)
    {
        float partialTick = ctx.partialTick();
        float waveAlpha = scan.waveAlpha(partialTick);
        List<Entity> marks = scan.detected();
        if (waveAlpha <= 0f && marks.isEmpty()) return;
        float radius = scan.radius(partialTick);
        ShaderInstance shader = RadarShaders.composite();
        if (shader != null) {
            if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
            compose(shader, scan, pose, ctx, radius, waveAlpha, marks);
        }
        if (waveAlpha > 0f && radius > 0.5f) {
            // Segments amincis lineairement avec le rayon, faces estompees au meme rythme.
            float left = Math.max(0f, 1f - radius / Math.max(1, scan.maxRadius()));
            int sphere = WAVE.rgb(HoloThemes.clientStandard());
            int face = (int) (FACE_ALPHA * waveAlpha * left) << 24 | sphere;
            int edge = (int) (EDGE_ALPHA * waveAlpha) << 24 | sphere;
            RadarSphere.draw(pose.last().pose(), buffers.getBuffer(Types.SPHERE), radius, EDGE_WIDTH * left, face,
                    edge);
        }
    }

    /** Etapes 1 a 3 : profondeur, masque, composition. */
    private void compose(ShaderInstance shader, RadarScan scan, PoseStack pose, WorldRenderContext ctx, float radius,
                         float waveAlpha, List<Entity> marks)
    {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int texture0 = RenderSystem.getShaderTexture(0);
        int texture1 = RenderSystem.getShaderTexture(1);
        try
        {
            mask.prepare(main);
            mask.copySceneDepth(main);
            boolean hasMask = !marks.isEmpty();
            if (hasMask) {
                mask.clearMask();
                main.bindWrite(false);
                mask.renderEntities(mc.getEntityRenderDispatcher(), scan.origin(), pose, ctx.partialTick(), marks);
            }
            main.bindWrite(false);

            // Positions relatives a l'origine de l'onde : inverse de projection x model-view x pose (origine).
            inverse.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix())
                    .mul(pose.last().pose()).invert();
            Vec3 camera = ctx.cameraPosition();
            Vec3 origin = scan.origin();
            shader.safeGetUniform("InvViewProj").set(inverse);
            shader.safeGetUniform("CameraPos").set((float) (camera.x - origin.x), (float) (camera.y - origin.y),
                    (float) (camera.z - origin.z));
            shader.safeGetUniform("TexelSize").set(1f / mask.width(), 1f / mask.height());
            shader.safeGetUniform("Radius").set(radius);
            shader.safeGetUniform("Front").set(FRONT);
            shader.safeGetUniform("WaveAlpha").set(waveAlpha);
            int wave = WAVE.rgb(HoloThemes.clientStandard());
            shader.safeGetUniform("WaveColor").set(red(wave), green(wave), blue(wave));
            shader.safeGetUniform("HasMask").set(hasMask ? 1f : 0f);
            shader.safeGetUniform("FillAlpha").set(FILL_ALPHA);
            int mark = MARK.rgb(HoloThemes.clientStandard());
            shader.safeGetUniform("MarkColor").set(red(mark), green(mark), blue(mark));

            RenderSystem.setShader(RadarShaders::composite);
            RenderSystem.setShaderTexture(0, mask.sceneDepthTexture());
            RenderSystem.setShaderTexture(1, mask.maskDepthTexture());
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            builder.vertex(-1f, -1f, 0f).endVertex();
            builder.vertex(1f, -1f, 0f).endVertex();
            builder.vertex(1f, 1f, 0f).endVertex();
            builder.vertex(-1f, 1f, 0f).endVertex();
            BufferUploader.drawWithShader(builder.end());
        }
        catch (RuntimeException e)
        {
            HoloLib.LOGGER.error("Radar : echec du rendu", e);
        }
        finally
        {
            main.bindWrite(false);
            RenderSystem.setShaderTexture(0, texture0);
            RenderSystem.setShaderTexture(1, texture1);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }
    }

    /** Sphere : quads translucides, double face, testes contre la profondeur du monde sans l'ecrire. */
    private static final class Types extends RenderType
    {
        static final RenderType SPHERE = create(HoloLib.MODID + ":radar_sphere", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                      boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear)
        {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
