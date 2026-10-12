package com.amynna.hololib.client.render.modules.camera.targeting;

import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.modules.camera.targeting.TargetingClient;
import com.amynna.hololib.client.render.outline.HoloOutlineMask;
import com.amynna.hololib.client.render.outline.HoloOutlineShader;
import com.amynna.hololib.client.render.world.WorldRenderContext;
import com.amynna.hololib.client.render.world.WorldRenderable;
import com.amynna.hololib.client.render.world.WorldRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Contour vert facon glowing de la cible du joueur local ({@link TargetingClient}) : une instance, placee sur la cible
 * (position interpolee de l'entite, coin inferieur du bloc). Masque ({@link HoloOutlineMask}) : l'entite redessinee,
 * ou les boites de la forme du bloc ({@code getShape}, bloc plein si elle est vide) ; puis contour plein ecran
 * ({@link HoloOutlineShader}), visible a travers les murs. Le lot en attente du tampon du mod est envoye avant.
 */
final class TargetOutlineRenderer extends WorldRenderer<TargetOutlineRenderer.Target>
{
    private static final HoloColor COLOR = HoloColor.selection(0xFF);
    private static final float ALPHA = 1f;

    private final Target target = new Target();
    private final List<Target> one = List.of(target);
    private final List<Entity> entities = new ArrayList<>(1);
    private final HoloOutlineMask mask = new HoloOutlineMask();
    /** Pile du bloc en cours, lue par {@link #boxes}. */
    private Matrix4f boxPose;
    private final Shapes.DoubleLineConsumer boxes = (x0, y0, z0, x1, y1, z1) -> mask.box(boxPose, x0, y0, z0, x1, y1,
            z1);

    @Override
    protected List<? extends Target> instances()
    {
        return TargetingClient.entity() != null || TargetingClient.block() != null ? one : List.of();
    }

    @Override
    protected double maxDistance(Target object) { return 160.0; }

    @Override
    protected int packedLight(Target object, Vec3 position, WorldRenderContext ctx) { return LightTexture.FULL_BRIGHT; }

    @Override
    protected void render(Target object, PoseStack pose, MultiBufferSource buffers, int packedLight,
                          WorldRenderContext ctx)
    {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = TargetingClient.entity();
        BlockPos block = TargetingClient.block();
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
        RenderTarget main = mc.getMainRenderTarget();
        try
        {
            mask.prepare(main);
            mask.clearMask();
            if (entity != null) {
                entities.add(entity);
                mask.renderEntities(mc.getEntityRenderDispatcher(), object.position(ctx.partialTick()), pose,
                        ctx.partialTick(), entities);
            } else if (block != null) {
                boxPose = pose.last().pose();
                VoxelShape shape = ctx.level().getBlockState(block).getShape(ctx.level(), block);
                if (shape.isEmpty()) shape = Shapes.block();
                shape.forAllBoxes(boxes);
                mask.endBatch();
            }
            HoloOutlineShader.compose(mask, COLOR.rgb(HoloThemes.clientStandard()), ALPHA);
        }
        catch (RuntimeException e)
        {
            HoloLib.LOGGER.error("Ciblage : echec du contour", e);
        }
        finally
        {
            entities.clear();
            main.bindWrite(false);
        }
    }

    /** Cible du joueur local, lue dans {@link TargetingClient} a chaque appel. */
    static final class Target implements WorldRenderable
    {
        @Override
        public Vec3 position(float partialTick)
        {
            Entity entity = TargetingClient.entity();
            if (entity != null) {
                return new Vec3(Mth.lerp(partialTick, entity.xOld, entity.getX()),
                        Mth.lerp(partialTick, entity.yOld, entity.getY()),
                        Mth.lerp(partialTick, entity.zOld, entity.getZ()));
            }
            BlockPos block = TargetingClient.block();
            return block != null ? Vec3.atLowerCornerOf(block) : Vec3.ZERO;
        }

        @Override
        public AABB renderBounds(float partialTick)
        {
            Entity entity = TargetingClient.entity();
            if (entity != null) return entity.getBoundingBoxForCulling().inflate(0.5);
            BlockPos block = TargetingClient.block();
            return block != null ? new AABB(block).inflate(0.5) : new AABB(Vec3.ZERO, Vec3.ZERO);
        }
    }
}
