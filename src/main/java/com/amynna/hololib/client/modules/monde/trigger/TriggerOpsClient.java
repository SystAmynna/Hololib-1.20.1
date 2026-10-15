package com.amynna.hololib.client.modules.monde.trigger;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.monde.trigger.TriggerOps;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Backend client des modules Trigger ({@link TriggerOps.Backend}) : niveau de permission du joueur local, qui decide
 * de l'affichage des fenetres des deux Configurators ({@code shown()}). Installe au setup client. Fil client.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TriggerOpsClient implements TriggerOps.Backend
{
    private TriggerOpsClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> TriggerOps.install(new TriggerOpsClient()));
    }

    /** Niveau de permission envoye par le serveur au joueur local ({@code LocalPlayer.hasPermissions}). */
    @Override
    public boolean localOp()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(TriggerOps.OP_LEVEL);
    }
}
