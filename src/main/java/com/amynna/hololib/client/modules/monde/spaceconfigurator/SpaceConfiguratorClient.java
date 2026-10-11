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
 * permission du joueur local, qui decide de l'affichage de la fenetre et des poignees ({@code shown()}) ; import d'une
 * texture d'astre (T45, {@link SpaceTextureImport}). Installe au setup client. Fil client.
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

    /** T45 : choix du fichier, preparation et envoi ({@link SpaceTextureImport}), operateur seulement. */
    @Override
    public boolean importTexture(SpaceConfiguratorModule module, int systemId, int bodyId)
    {
        return localOp() && SpaceTextureImport.start(module, systemId, bodyId);
    }

    @Override
    public int importProgress() { return SpaceTextureImport.progress(); }
}
