package com.amynna.hololib.client.modules.monde.areadisplayer;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerModule;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Backend client du module {@code hololib:area_displayer} ({@link AreaDisplayerModule.Backend}) : abonnement du
 * miroir au terrain et desabonnement, delegues a {@link AreaTerrainClient}. Installe au setup client. Fil client.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AreaDisplayerClient implements AreaDisplayerModule.Backend
{
    private AreaDisplayerClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> AreaDisplayerModule.install(new AreaDisplayerClient()));
    }

    @Override
    public void subscribe(AreaDisplayerModule module) { AreaTerrainClient.subscribe(module); }

    @Override
    public void unsubscribe(AreaDisplayerModule module) { AreaTerrainClient.unsubscribe(module); }
}
