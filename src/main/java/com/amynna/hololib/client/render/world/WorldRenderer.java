package com.amynna.hololib.client.render.world;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Dessine des objets {@code T} ancres dans le monde, comme des entites : apres les entites ({@code AFTER_ENTITIES}),
 * avec le test de profondeur du monde, donc caches par les blocs opaques et les entites places devant ; l'eau et le
 * verre, dessines ensuite, passent correctement par-dessus. API calquee sur {@code EntityRenderer}.
 * <p>
 * Pour chaque instance visible, {@link WorldRenderPass} place la pile a la position de l'objet (relative a la camera,
 * pour garder la precision des flottants loin de l'origine) puis appelle {@link #render}. Les vertices vont dans le
 * {@code BufferSource} du jeu, vide par vanilla avec les entites : utiliser des {@code RenderType} d'entite ou ceux de
 * {@code HoloRenderTypes}, sans vider les buffers soi-meme.
 * <p>
 * Pour un objet qui existe cote serveur, preferer une vraie {@code Entity} et son {@code EntityRenderer}.
 */
public abstract class WorldRenderer<T extends WorldRenderable>
{
    private final BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();

    /** Instances a dessiner, lues pendant la frame (fil de rendu) ; la liste n'est pas copiee. */
    protected abstract List<? extends T> instances();

    /**
     * Dessine une instance. La pile est deja placee a {@link WorldRenderable#position} et orientee comme le monde.
     *
     * @param packedLight lumiere du bloc a la position de l'objet (voir {@link #packedLight})
     */
    protected abstract void render(T object, PoseStack pose, MultiBufferSource buffers, int packedLight,
                                   WorldRenderContext ctx);

    /** Distance maximale de dessin, en blocs. */
    protected double maxDistance(T object) { return 64.0; }

    /** Culling : a portee de la camera et boite dans le frustum. */
    protected boolean shouldRender(T object, Vec3 position, WorldRenderContext ctx)
    {
        double max = maxDistance(object);
        return position.distanceToSqr(ctx.cameraPosition()) <= max * max
                && ctx.frustum().isVisible(object.renderBounds(ctx.partialTick()));
    }

    /** Lumiere du bloc a la position de l'objet ; renvoyer {@code LightTexture.FULL_BRIGHT} pour un objet emissif. */
    protected int packedLight(T object, Vec3 position, WorldRenderContext ctx)
    {
        return LevelRenderer.getLightColor(ctx.level(), lightPos.set(position.x, position.y, position.z));
    }

    /** Dessine toutes les instances visibles ; appele par {@link WorldRenderPass}. */
    final void renderAll(PoseStack pose, MultiBufferSource buffers, WorldRenderContext ctx)
    {
        List<? extends T> objects = instances();
        Vec3 camera = ctx.cameraPosition();
        for (T object : objects) {
            Vec3 p = object.position(ctx.partialTick());
            if (!shouldRender(object, p, ctx)) continue;
            pose.pushPose();
            pose.translate(p.x - camera.x, p.y - camera.y, p.z - camera.z);
            render(object, pose, buffers, packedLight(object, p, ctx), ctx);
            pose.popPose();
        }
    }
}
