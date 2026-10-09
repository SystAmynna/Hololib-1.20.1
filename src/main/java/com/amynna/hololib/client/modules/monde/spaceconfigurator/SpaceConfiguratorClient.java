package com.amynna.hololib.client.modules.monde.spaceconfigurator;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceConfiguratorModule;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Backend client du module {@code hololib:space_configurator} ({@link SpaceConfiguratorModule.Backend}) : niveau de
 * permission du joueur local, qui decide de l'affichage du cube et de la fenetre ({@code shown()}). Installe au setup
 * client. Fil client.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpaceConfiguratorClient implements SpaceConfiguratorModule.Backend
{
    private SpaceConfiguratorClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> SpaceConfiguratorModule.install(new SpaceConfiguratorClient()));
    }

    /** Niveau de permission envoye par le serveur au joueur local ({@code LocalPlayer.hasPermissions}). */
    @Override
    public boolean localOp()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(SpaceConfiguratorModule.OP_LEVEL);
    }
}
