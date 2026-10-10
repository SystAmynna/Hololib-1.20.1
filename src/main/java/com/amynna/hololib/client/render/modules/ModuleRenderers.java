package com.amynna.hololib.client.render.modules;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.modules.camera.editor.EditorRenderers;
import com.amynna.hololib.client.render.modules.camera.spacepilot.SpacePilotRenderers;
import com.amynna.hololib.client.render.modules.dimension.planet1.Planet1Renderers;
import com.amynna.hololib.client.render.modules.dimension.space.SpaceRenderers;
import com.amynna.hololib.client.render.modules.monde.spaceconfigurator.SpaceConfiguratorRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Enregistre les renderers des objets des modules, au setup client. */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModuleRenderers
{
    private ModuleRenderers() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> {
            Planet1Renderers.register();
            EditorRenderers.register();
            SpaceRenderers.register();
            SpacePilotRenderers.register();            // apres SpaceRenderers : surcouche du ciel spatial
            SpaceConfiguratorRenderers.register();
        });
    }
}
