package com.amynna.hololib;

import com.amynna.hololib.block.HoloLibBlocks;
import com.amynna.hololib.demo.DemoModules;
import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.module.HoloModuleTypes;
import com.amynna.hololib.holo.theme.HoloThemePatterns;
import com.amynna.hololib.holo.theme.pattern.HoloLibThemePatterns;
import com.amynna.hololib.modules.HoloLibModules;
import com.amynna.hololib.network.HoloNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(HoloLib.MODID)
public class HoloLib
{
    public static final String MODID = "hololib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HoloLib(FMLJavaModLoadingContext context)
    {
        IEventBus modBus = context.getModEventBus();
        HoloModuleTypes.REGISTER.register(modBus);
        HoloThemePatterns.REGISTER.register(modBus);
        HoloLibThemePatterns.REGISTER.register(modBus);
        DemoModules.REGISTER.register(modBus);
        HoloLibModules.REGISTER.register(modBus);
        HoloLibBlocks.register(modBus);
        modBus.addListener(HoloContainers::registerCapabilities);
        modBus.addListener(HoloLib::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event)
    {
        event.enqueueWork(HoloNetwork::register);
    }
}
