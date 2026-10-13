package com.amynna.hololib.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity des bassins cosmiques (toutes couleurs) : aucune donnee sauvegardee, sert de support au renderer.
 * Calcule (cote client) les bords de chaque face ou la surface du bassin s'arrete, dessines lumineux par le shader.
 */
public class CosmicPoolBlockEntity extends BlockEntity
{
    /** Bits du masque d'une face : bords u-, u+, v-, v+ (0-3), coins interieurs u-v-, u+v-, u-v+, u+v+ (4-7). */
    public static final int EDGE_U_NEG = 1, EDGE_U_POS = 2, EDGE_V_NEG = 4, EDGE_V_POS = 8;

    private static final Direction[] DIRECTIONS = Direction.values();

    private final int[] masks = new int[6];
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
    private long masksTick = Long.MIN_VALUE;

    public CosmicPoolBlockEntity(BlockPos pos, BlockState state)
    {
        super(HoloLibBlocks.COSMIC_POOL_ENTITY.get(), pos, state);
    }

    /** Vrai si la face n'est pas cachee par le voisin (bloc opaque, autre bassin cosmique). */
    public boolean shouldRenderFace(Direction direction)
    {
        return level == null
                || Block.shouldRenderFace(getBlockState(), level, worldPosition, direction, worldPosition.relative(direction));
    }

    /**
     * Masque des bords lumineux de la face (bits {@code EDGE_*}, puis coins en bits 4-7), recalcule au plus une fois
     * par tick. Axes de la face : {@link #uAxis} et {@link #vAxis}.
     */
    public int faceMask(Direction face)
    {
        if (level == null)
        {
            return EDGE_U_NEG | EDGE_U_POS | EDGE_V_NEG | EDGE_V_POS;
        }
        long tick = level.getGameTime();
        if (tick != masksTick)
        {
            masksTick = tick;
            for (Direction direction : DIRECTIONS)
            {
                masks[direction.ordinal()] = computeMask(direction);
            }
        }
        return masks[face.ordinal()];
    }

    /** Axe u d'une face : x, sauf EAST / WEST (z). */
    public static Direction.Axis uAxis(Direction face)
    {
        return face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    /** Axe v d'une face : y, sauf UP / DOWN (z). */
    public static Direction.Axis vAxis(Direction face)
    {
        return face.getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
    }

    private int computeMask(Direction face)
    {
        Direction uNeg = Direction.fromAxisAndDirection(uAxis(face), Direction.AxisDirection.NEGATIVE);
        Direction vNeg = Direction.fromAxisAndDirection(vAxis(face), Direction.AxisDirection.NEGATIVE);
        Direction uPos = uNeg.getOpposite();
        Direction vPos = vNeg.getOpposite();
        boolean un = continues(face, uNeg, null);
        boolean up = continues(face, uPos, null);
        boolean vn = continues(face, vNeg, null);
        boolean vp = continues(face, vPos, null);
        int mask = (un ? 0 : EDGE_U_NEG) | (up ? 0 : EDGE_U_POS) | (vn ? 0 : EDGE_V_NEG) | (vp ? 0 : EDGE_V_POS);
        if (un && vn && !continues(face, uNeg, vNeg)) mask |= 16;
        if (up && vn && !continues(face, uPos, vNeg)) mask |= 32;
        if (un && vp && !continues(face, uNeg, vPos)) mask |= 64;
        if (up && vp && !continues(face, uPos, vPos)) mask |= 128;
        return mask;
    }

    /**
     * Vrai si la surface {@code face} du bassin continue sur le voisin (decale de {@code a}, puis de {@code b}) : bassin
     * de la meme couleur seulement (deux couleurs voisines ont chacune leur bord).
     */
    private boolean continues(Direction face, Direction a, Direction b)
    {
        cursor.setWithOffset(worldPosition, a);
        if (b != null)
        {
            cursor.move(b);
        }
        BlockState state = level.getBlockState(cursor);
        if (!state.is(getBlockState().getBlock()))
        {
            return false;
        }
        return Block.shouldRenderFace(state, level, cursor, face, neighbor.setWithOffset(cursor, face));
    }
}
