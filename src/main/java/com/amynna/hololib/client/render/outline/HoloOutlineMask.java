package com.amynna.hololib.client.render.outline;

import com.amynna.hololib.HoloLib;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Masque hors ecran pour dessiner des contours facon glowing (sans Mixin ni glowing vanilla, qui ne peut etre force
 * cote client). Deux cibles a la taille de la cible principale :
 * <ul>
 *     <li>{@code sceneDepth} : copie de la profondeur de la cible principale ({@link #copySceneDepth} ; on ne peut pas
 *     echantillonner la profondeur de la cible liee) ;</li>
 *     <li>{@code mask} : profondeur avant des formes du masque, videe a 1 par {@link #clearMask}. Entites redessinees
 *     par {@code EntityRenderDispatcher} ({@link #renderEntities}), boites pleines ({@link #box} : silhouette d'un
 *     bloc).</li>
 * </ul>
 * Ce {@code MultiBufferSource} remplace chaque {@code RenderType} qui a une variante de contour
 * ({@code RenderType.outline()} : modeles d'entites, objets tenus) par un type delegant, cree une fois par type de
 * contour : etat du contour vanilla (texture et shader {@code rendertype_outline}, qui jette les texels transparents),
 * puis cible {@code mask}, test {@code LEQUAL} et ecriture de profondeur. Couleur fixe, comme
 * {@code OutlineBufferSource} (seule la profondeur compte). Les autres types (ombre, nom, lignes, reflets) vont dans un
 * consommateur nul. Tampon immediat propre : un modele demande ses types les uns apres les autres.
 * <p>
 * Le masque se lit ensuite dans un shader (textures {@link #maskDepthTexture}, {@link #sceneDepthTexture}) :
 * {@link HoloOutlineShader#compose} dessine le contour seul ; un module peut aussi composer avec son propre shader
 * (radar). Cibles creees au premier usage (la copie de profondeur au premier {@link #copySceneDepth}), stencil compris
 * si la cible principale en a (la copie de profondeur est alors permise). Une instance par renderer. Fil de rendu.
 */
public final class HoloOutlineMask implements MultiBufferSource
{
    private final BufferSource source = MultiBufferSource.immediate(new BufferBuilder(2048));
    private final Map<RenderType, Entry> types = new IdentityHashMap<>();
    private final RenderType boxType;
    private TextureTarget sceneDepth;
    private TextureTarget mask;

    public HoloOutlineMask()
    {
        boxType = new MaskType(HoloLib.MODID + ":outline_mask_box", DefaultVertexFormat.POSITION_COLOR, 256, () -> {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            mask.bindWrite(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
        }, () -> {
            RenderSystem.enableCull();
            RenderSystem.disableDepthTest();
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        });
    }

    /** Masque a la taille de {@code main} (cree ou redimensionne). */
    public void prepare(RenderTarget main) { mask = fit(mask, main); }

    private static TextureTarget fit(TextureTarget target, RenderTarget main)
    {
        if (target == null) {
            target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            target.setClearColor(0f, 0f, 0f, 0f);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        if (main.isStencilEnabled() && !target.isStencilEnabled()) target.enableStencil();
        return target;
    }

    /** Copie la profondeur de {@code main} (cible creee au premier appel) ; aucune cible n'est liee ensuite. */
    public void copySceneDepth(RenderTarget main)
    {
        sceneDepth = fit(sceneDepth, main);
        sceneDepth.copyDepthFrom(main);
    }

    /** Vide le masque (profondeur 1) ; aucune cible n'est liee ensuite. */
    public void clearMask() { mask.clear(Minecraft.ON_OSX); }

    /** Envoie le dernier lot du masque. */
    public void endBatch() { source.endBatch(); }

    public int sceneDepthTexture() { return sceneDepth.getDepthTextureId(); }

    public int maskDepthTexture() { return mask.getDepthTextureId(); }

    public int width() { return mask.width; }

    public int height() { return mask.height; }

    /**
     * Ajoute des entites au masque, positions et lacet interpoles, relatives a {@code origin} (la pile est placee a
     * {@code origin}, en axes du monde). Le lot est envoye a la fin.
     */
    public void renderEntities(EntityRenderDispatcher dispatcher, Vec3 origin, PoseStack pose, float partialTick,
                               List<? extends Entity> entities)
    {
        try
        {
            for (Entity entity : entities) {
                double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - origin.x;
                double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) - origin.y;
                double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - origin.z;
                float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
                dispatcher.render(entity, x, y, z, yaw, partialTick, pose, this,
                        dispatcher.getPackedLightCoords(entity, partialTick));
            }
        }
        finally
        {
            endBatch();
        }
    }

    /**
     * Ajoute une boite pleine au masque (six faces), coordonnees dans le repere de {@code pose} ; le lot part au
     * prochain {@link #endBatch} (ou au prochain type demande). Signature de {@code VoxelShape.forAllBoxes}.
     */
    public void box(Matrix4f pose, double minX, double minY, double minZ, double maxX, double maxY, double maxZ)
    {
        VertexConsumer c = source.getBuffer(boxType);
        float x0 = (float) minX;
        float y0 = (float) minY;
        float z0 = (float) minZ;
        float x1 = (float) maxX;
        float y1 = (float) maxY;
        float z1 = (float) maxZ;
        quad(c, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(c, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(c, pose, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(c, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(c, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(c, pose, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(VertexConsumer c, Matrix4f pose, float ax, float ay, float az, float bx, float by,
                             float bz, float cx, float cy, float cz, float dx, float dy, float dz)
    {
        c.vertex(pose, ax, ay, az).color(255, 255, 255, 255).endVertex();
        c.vertex(pose, bx, by, bz).color(255, 255, 255, 255).endVertex();
        c.vertex(pose, cx, cy, cz).color(255, 255, 255, 255).endVertex();
        c.vertex(pose, dx, dy, dz).color(255, 255, 255, 255).endVertex();
    }

    @Override
    public VertexConsumer getBuffer(RenderType type)
    {
        if (type.mode() != VertexFormat.Mode.QUADS) return NullConsumer.INSTANCE;
        Optional<RenderType> outline = type.outline();
        // Sans cible de contour (post-effet vanilla non charge), l'etat du contour ne peut pas etre pose.
        if (outline.isEmpty() || Minecraft.getInstance().levelRenderer.entityTarget() == null) {
            return NullConsumer.INSTANCE;
        }
        Entry entry = types.get(outline.get());
        if (entry == null) {
            entry = new Entry(outline.get());
            types.put(outline.get(), entry);
        }
        entry.consumer.delegate = source.getBuffer(entry.type);
        return entry.consumer;
    }

    /** Type delegant et son consommateur, pour un type de contour vanilla. */
    private final class Entry
    {
        final RenderType type;
        final MaskConsumer consumer = new MaskConsumer();

        Entry(RenderType outline)
        {
            type = new MaskType(HoloLib.MODID + ":outline_mask_" + outline, DefaultVertexFormat.POSITION_COLOR_TEX,
                    outline.bufferSize(), () -> {
                outline.setupRenderState();
                mask.bindWrite(false);
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(true);
            }, () -> {
                // Le contour relie la cible principale a sa fin.
                outline.clearRenderState();
                RenderSystem.disableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
            });
        }
    }

    /** {@code RenderType} du masque, etat fourni par le createur. */
    private static final class MaskType extends RenderType
    {
        MaskType(String name, VertexFormat format, int bufferSize, Runnable setup, Runnable clear)
        {
            super(name, format, VertexFormat.Mode.QUADS, bufferSize, false, false, setup, clear);
        }
    }

    /**
     * Consommateur du masque : garde position et UV, couleur fixe (principe de
     * {@code OutlineBufferSource.EntityOutlineGenerator}) ; les modeles passent par la methode groupee.
     */
    private static final class MaskConsumer extends DefaultedVertexConsumer
    {
        VertexConsumer delegate;
        private double x;
        private double y;
        private double z;
        private float u;
        private float v;

        @Override
        public VertexConsumer vertex(double x, double y, double z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) { return this; }

        @Override
        public VertexConsumer uv(float u, float v)
        {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) { return this; }

        @Override
        public VertexConsumer uv2(int u, int v) { return this; }

        @Override
        public VertexConsumer normal(float x, float y, float z) { return this; }

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay,
                           int light, float nx, float ny, float nz)
        {
            delegate.vertex(x, y, z).color(255, 255, 255, 255).uv(u, v).endVertex();
        }

        @Override
        public void endVertex() { delegate.vertex(x, y, z).color(255, 255, 255, 255).uv(u, v).endVertex(); }

        @Override
        public void defaultColor(int r, int g, int b, int a) {}

        @Override
        public void unsetDefaultColor() {}
    }

    /** Consommateur qui jette tout (types sans contour). */
    private static final class NullConsumer extends DefaultedVertexConsumer
    {
        static final NullConsumer INSTANCE = new NullConsumer();

        @Override
        public VertexConsumer vertex(double x, double y, double z) { return this; }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) { return this; }

        @Override
        public VertexConsumer uv(float u, float v) { return this; }

        @Override
        public VertexConsumer overlayCoords(int u, int v) { return this; }

        @Override
        public VertexConsumer uv2(int u, int v) { return this; }

        @Override
        public VertexConsumer normal(float x, float y, float z) { return this; }

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay,
                           int light, float nx, float ny, float nz) {}

        @Override
        public void endVertex() {}

        @Override
        public void defaultColor(int r, int g, int b, int a) {}

        @Override
        public void unsetDefaultColor() {}
    }
}
