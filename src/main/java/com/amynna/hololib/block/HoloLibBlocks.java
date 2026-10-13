package com.amynna.hololib.block;

import com.amynna.hololib.HoloLib;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/** Blocs du mod, leurs items et leurs block entities. */
public final class HoloLibBlocks
{
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HoloLib.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HoloLib.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HoloLib.MODID);

    /** Bassins cosmiques, un par couleur ({@code hololib:<couleur>_cosmic_pool}), dans l'ordre de {@link DyeColor}. */
    public static final Map<DyeColor, RegistryObject<Block>> COSMIC_POOLS = new EnumMap<>(DyeColor.class);
    /** Items des bassins cosmiques, par couleur. */
    public static final Map<DyeColor, RegistryObject<BlockItem>> COSMIC_POOL_ITEMS = new EnumMap<>(DyeColor.class);

    static
    {
        for (DyeColor color : DyeColor.values())
        {
            String name = color.getName() + "_cosmic_pool";
            RegistryObject<Block> block = BLOCKS.register(name,
                    () -> new CosmicPoolBlock(color, BlockBehaviour.Properties.of()
                            .mapColor(color.getMapColor())
                            .strength(1.5F, 6.0F)
                            .sound(SoundType.AMETHYST)));
            COSMIC_POOLS.put(color, block);
            COSMIC_POOL_ITEMS.put(color,
                    ITEMS.register(name, () -> new CosmicPoolItem(block.get(), new Item.Properties())));
        }
    }

    /** Block entity commun aux bassins de toutes les couleurs. */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<CosmicPoolBlockEntity>> COSMIC_POOL_ENTITY =
            BLOCK_ENTITIES.register("cosmic_pool",
                    () -> BlockEntityType.Builder.of(CosmicPoolBlockEntity::new,
                            COSMIC_POOLS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    private HoloLibBlocks() {}

    public static void register(IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(HoloLibBlocks::onBuildCreativeTab);
    }

    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS)
        {
            COSMIC_POOL_ITEMS.values().forEach(event::accept);
        }
    }
}
