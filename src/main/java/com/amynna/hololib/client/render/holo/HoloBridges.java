package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.HoloRenderers;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Enregistre les quatre ponts holographiques et les renderers d'objets par defaut, au setup client. */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HoloBridges
{
    private HoloBridges() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> {
            HoloRenderers.register(new HoloSkyBridge());
            HoloRenderers.register(new HoloWorldBridge());
            HoloRenderers.register(new HoloViewBridge());
            HoloRenderers.register(new HoloHudBridge());
            HoloObjectRenderers.register(HoloWindow.class, new HoloWindowRenderer());
        });
    }
}
