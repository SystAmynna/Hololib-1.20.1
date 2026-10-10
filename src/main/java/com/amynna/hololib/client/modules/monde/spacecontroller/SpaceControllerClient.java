package com.amynna.hololib.client.modules.monde.spacecontroller;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.modules.camera.spacepilot.SpacePilotClient;
import com.amynna.hololib.modules.monde.spacecontroller.SpaceControllerModule;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Backend client du module {@code hololib:space_controller} ({@link SpaceControllerModule.Backend}) : niveau de
 * permission du joueur local (pilotage reserve aux ops), presence de son module Space Pilot (pilotage refuse sans
 * lui, D11) et fraction de tick de la derniere frame (cible du saut cherchee dans le ciel affiche, T22). Installe au
 * setup client. Fil client.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpaceControllerClient implements SpaceControllerModule.Backend
{
    private SpaceControllerClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> SpaceControllerModule.install(new SpaceControllerClient()));
    }

    /** Module Space Pilot de la memoire active du joueur local ({@link SpacePilotClient#module()}, relu chaque tick). */
    @Override
    public boolean localSpacePilot() { return SpacePilotClient.module() != null; }

    /** Niveau de permission envoye par le serveur au joueur local ({@code LocalPlayer.hasPermissions}). */
    @Override
    public boolean localOp()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(SpaceControllerModule.OP_LEVEL);
    }

    /** Fraction de tick utilisee pour dessiner la derniere frame (figee en pause, {@code Minecraft.getPartialTick}). */
    @Override
    public float partialTick() { return Minecraft.getInstance().getPartialTick(); }
}
