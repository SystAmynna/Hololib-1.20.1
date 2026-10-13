package com.amynna.hololib.client.render.block;

import com.amynna.hololib.block.CosmicPoolBlock;
import com.amynna.hololib.block.CosmicPoolBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Dessine les faces visibles du bassin cosmique avec {@link CosmicPoolRenderType#COSMIC_POOL}. Donnees de sommet lues
 * par {@code cosmic_pool.vsh} : couleur = (masque des bords, masque des coins, id de la couleur du bassin, 255), UV =
 * coordonnees locales de la face (0..1, precises : le shader en derive le repere de la surface), UV2 = origine (u, v)
 * du bloc dans le plan de la face (entiers : coordonnees monde = origine + locales, motif continu d'un bloc a
 * l'autre).
 */
public class CosmicPoolRenderer implements BlockEntityRenderer<CosmicPoolBlockEntity>
{
    private static final Direction[] DIRECTIONS = Direction.values();

    @Override
    public void render(CosmicPoolBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light, int overlay)
    {
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer consumer = buffers.getBuffer(CosmicPoolRenderType.COSMIC_POOL);
        BlockPos pos = entity.getBlockPos();
        // UV2 = entiers 16 bits signes : coordonnees exactes entre -32768 et 32767 (motif continu, 0 compris), repliees
        // au-dela (cesure seulement a +-32768 ; plus de precision du float dans le shader de toute facon)
        int ox = (short) pos.getX();
        int oy = (short) pos.getY();
        int oz = (short) pos.getZ();
        int color = entity.getBlockState().getBlock() instanceof CosmicPoolBlock pool ? pool.color().getId() : 0;
        for (Direction direction : DIRECTIONS)
        {
            if (entity.shouldRenderFace(direction))
            {
                face(pose, consumer, direction, ox, oy, oz, entity.faceMask(direction), color);
            }
        }
    }

    /** Dessine les 6 faces du cube 0..1, origine de surface 0, avec le meme masque et la couleur {@code color}. */
    static void cube(Matrix4f pose, VertexConsumer consumer, int mask, int color)
    {
        for (Direction direction : DIRECTIONS)
        {
            face(pose, consumer, direction, 0, 0, 0, mask, color);
        }
    }

    /**
     * Face du cube 0..1 vue de l'exterieur (sommets de {@code TheEndPortalRenderer.renderCube}) ; {@code ox, oy, oz} :
     * origine du bloc pour les coordonnees de surface, {@code mask} : {@link CosmicPoolBlockEntity#faceMask},
     * {@code color} : id de la {@code DyeColor} du bassin.
     */
    static void face(Matrix4f pose, VertexConsumer consumer, Direction direction, int ox, int oy, int oz, int mask,
                     int color)
    {
        switch (direction)
        {
            case SOUTH -> quad(pose, consumer, direction, ox, oy, oz, mask, color, 0, 1, 0, 1, 1, 1, 1, 1);
            case NORTH -> quad(pose, consumer, direction, ox, oy, oz, mask, color, 0, 1, 1, 0, 0, 0, 0, 0);
            case EAST -> quad(pose, consumer, direction, ox, oy, oz, mask, color, 1, 1, 1, 0, 0, 1, 1, 0);
            case WEST -> quad(pose, consumer, direction, ox, oy, oz, mask, color, 0, 0, 0, 1, 0, 1, 1, 0);
            case DOWN -> quad(pose, consumer, direction, ox, oy, oz, mask, color, 0, 1, 0, 0, 0, 0, 1, 1);
            case UP -> quad(pose, consumer, direction, ox, oy, oz, mask, color, 0, 1, 1, 1, 1, 1, 0, 0);
        }
    }

    private static void quad(Matrix4f pose, VertexConsumer consumer, Direction face, int ox, int oy, int oz, int mask,
                             int color, float x0, float x1, float y0, float y1, float z0, float z1, float z2, float z3)
    {
        vertex(pose, consumer, face, ox, oy, oz, mask, color, x0, y0, z0);
        vertex(pose, consumer, face, ox, oy, oz, mask, color, x1, y0, z1);
        vertex(pose, consumer, face, ox, oy, oz, mask, color, x1, y1, z2);
        vertex(pose, consumer, face, ox, oy, oz, mask, color, x0, y1, z3);
    }

    private static void vertex(Matrix4f pose, VertexConsumer consumer, Direction face, int ox, int oy, int oz, int mask,
                               int color, float x, float y, float z)
    {
        // Axes de CosmicPoolBlockEntity.uAxis / vAxis : u = x (z pour EAST / WEST), v = y (z pour UP / DOWN)
        Direction.Axis axis = face.getAxis();
        boolean uz = axis == Direction.Axis.X;
        boolean vz = axis == Direction.Axis.Y;
        consumer.vertex(pose, x, y, z)
                .color(mask & 0x0F, (mask >> 4) & 0x0F, color, 255)
                .uv(uz ? z : x, vz ? z : y)
                .uv2(uz ? oz : ox, vz ? oz : oy)
                .endVertex();
    }
}
