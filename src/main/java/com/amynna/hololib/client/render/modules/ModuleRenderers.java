package com.amynna.hololib.client.render.modules;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.modules.camera.attackguard.AttackGuardRenderers;
import com.amynna.hololib.client.render.modules.camera.editor.EditorRenderers;
import com.amynna.hololib.client.render.modules.camera.radar.RadarRenderers;
import com.amynna.hololib.client.render.modules.camera.spaceknowledge.SpaceKnowledgeRenderers;
import com.amynna.hololib.client.render.modules.camera.spacepilot.SpacePilotRenderers;
import com.amynna.hololib.client.render.modules.camera.targeting.TargetingRenderers;
import com.amynna.hololib.client.render.modules.dimension.planet1.Planet1Renderers;
import com.amynna.hololib.client.render.modules.dimension.space.SpaceRenderers;
import com.amynna.hololib.client.render.modules.monde.areadisplayer.AreaDisplayerRenderers;
import com.amynna.hololib.client.render.modules.monde.spaceconfigurator.SpaceConfiguratorRenderers;
import com.amynna.hololib.client.render.modules.monde.spacemap.SpaceMapRenderers;
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
            SpaceKnowledgeRenderers.register();        // apres SpaceRenderers : surcouche du ciel spatial
            SpacePilotRenderers.register();
            SpaceMapRenderers.register();
            SpaceConfiguratorRenderers.register();
            AreaDisplayerRenderers.register();
            AttackGuardRenderers.register();
            RadarRenderers.register();                 // composition plein ecran du radar, par-dessus les precedents
            TargetingRenderers.register();             // apres le radar : contour de la cible au-dessus du sien
        });
    }
}
