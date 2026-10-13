package com.amynna.hololib.block;

import com.amynna.hololib.client.render.block.CosmicPoolItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * Item du bassin cosmique, dessine avec le meme shader que le bloc ({@link CosmicPoolItemRenderer}, modele
 * {@code builtin/entity}). {@code initializeClient} n'est appele que cote client.
 */
public class CosmicPoolItem extends BlockItem
{
    public CosmicPoolItem(Block block, Properties properties)
    {
        super(block, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer)
    {
        consumer.accept(new IClientItemExtensions()
        {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer()
            {
                return CosmicPoolItemRenderer.instance();
            }
        });
    }
}
