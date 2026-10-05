package com.amynna.hololib.client.render.demo;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.holo.HoloObjectRenderers;
import com.amynna.hololib.demo.DemoCube;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Enregistre les renderers des objets de demonstration, au setup client. */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DemoRenderers
{
    private DemoRenderers() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> HoloObjectRenderers.register(DemoCube.class, new DemoCubeRenderer()));
    }
}
