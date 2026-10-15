package com.amynna.hololib.client.modules.monde.screenconfigurator;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.monde.screenconfigurator.ScreenConfiguratorModule;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Backend client du module {@code hololib:screen_configurator} ({@link ScreenConfiguratorModule.Backend}) :
 * niveau de permission du joueur local, qui decide de l'affichage de la fenetre ({@code shown()}). Installe au setup
 * client. Fil client.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ScreenConfiguratorClient implements ScreenConfiguratorModule.Backend
{
    private ScreenConfiguratorClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> ScreenConfiguratorModule.install(new ScreenConfiguratorClient()));
    }

    /** Niveau de permission envoye par le serveur au joueur local ({@code LocalPlayer.hasPermissions}). */
    @Override
    public boolean localOp()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(ScreenConfiguratorModule.OP_LEVEL);
    }
}
