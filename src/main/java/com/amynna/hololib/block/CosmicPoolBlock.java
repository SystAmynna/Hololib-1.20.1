package com.amynna.hololib.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bassin cosmique d'une couleur : cube plein sans modele bake (comme le portail de l'End), ses faces sont dessinees par
 * le {@code CosmicPoolRenderer} client avec le shader {@code hololib:cosmic_pool}, dans les teintes de sa couleur.
 */
public class CosmicPoolBlock extends BaseEntityBlock
{
    private final DyeColor color;

    public CosmicPoolBlock(DyeColor color, Properties properties)
    {
        super(properties);
        this.color = color;
    }

    /** Couleur du bassin (teintes du shader, id envoye au rendu). */
    public DyeColor color()
    {
        return color;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new CosmicPoolBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }
}
