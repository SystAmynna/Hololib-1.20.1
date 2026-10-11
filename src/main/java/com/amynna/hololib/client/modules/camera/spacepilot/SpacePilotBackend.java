package com.amynna.hololib.client.modules.camera.spacepilot;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.camera.spacepilot.SpacePilotModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Backend client du module {@code hololib:space_pilot} ({@link SpacePilotModule.Backend}, T34) : regard de la camera
 * du joueur local, pour la cible de la vitesse de croisiere (corps le plus proche du centre de l'ecran). Installe au
 * setup client. Fil client, sans allocation.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpacePilotBackend implements SpacePilotModule.Backend
{
    private SpacePilotBackend() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> SpacePilotModule.install(new SpacePilotBackend()));
    }

    /**
     * Direction avant de la camera principale ({@code Camera.getLookVector}, axes du monde), telle que posee pour la
     * derniere frame dessinee ; faux tant que la camera n'a pas ete initialisee (aucune frame dans le niveau).
     */
    @Override
    public boolean cameraLook(Vector3d dest)
    {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (!camera.isInitialized()) return false;
        Vector3f look = camera.getLookVector();
        dest.set(look.x(), look.y(), look.z());
        return true;
    }
}
