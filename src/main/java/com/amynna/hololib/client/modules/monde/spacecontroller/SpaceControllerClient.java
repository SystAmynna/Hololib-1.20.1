package com.amynna.hololib.client.modules.monde.spacecontroller;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.monde.spacecontroller.SpaceControllerModule;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.UUID;

/**
 * Backend client du module {@code hololib:space_controller} ({@link SpaceControllerModule.Backend}) : joueur local
 * (pilote local d'un poste, {@code shown()} des cercles du joystick) et son niveau de permission (pilotage reserve aux
 * ops). Installe au setup client. Fil client.
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

    @Override
    public UUID localPlayer()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getUUID();
    }

    /** Niveau de permission envoye par le serveur au joueur local ({@code LocalPlayer.hasPermissions}). */
    @Override
    public boolean localOp()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(SpaceControllerModule.OP_LEVEL);
    }
}
