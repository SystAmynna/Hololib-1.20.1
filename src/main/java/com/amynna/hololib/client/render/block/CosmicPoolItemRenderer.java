package com.amynna.hololib.client.render.block;

import com.amynna.hololib.block.CosmicPoolBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Items des bassins cosmiques : cube entier dessine avec le shader du bloc, dans la couleur du bassin (modele
 * {@code builtin/entity}, transforms d'affichage dans {@code models/item/<couleur>_cosmic_pool.json}).
 */
public class CosmicPoolItemRenderer extends BlockEntityWithoutLevelRenderer
{
    private static CosmicPoolItemRenderer instance;

    private CosmicPoolItemRenderer()
    {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    /** Instance unique, creee au premier dessin (les dispatchers n'existent pas a l'enregistrement des items). */
    public static CosmicPoolItemRenderer instance()
    {
        if (instance == null)
        {
            instance = new CosmicPoolItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay)
    {
        int color = stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CosmicPoolBlock pool
                ? pool.color().getId() : 0;
        // GUI et premiere personne : pas de bobbing dans la projection ; ailleurs (au sol, cadre, main d'une entite)
        // l'item est dans le monde
        RenderType type = context == ItemDisplayContext.GUI || context.firstPerson()
                ? CosmicPoolRenderType.COSMIC_POOL_STILL : CosmicPoolRenderType.COSMIC_POOL;
        // Chaque face encadree : les 4 bords lumineux, sans coin
        CosmicPoolRenderer.cube(poseStack.last().pose(), buffers.getBuffer(type), 0x0F, color);
    }
}
