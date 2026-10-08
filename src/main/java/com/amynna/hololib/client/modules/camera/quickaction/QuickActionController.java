package com.amynna.hololib.client.modules.camera.quickaction;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.holo.ClientHoloContainer;
import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.client.holo.HoloModes;
import com.amynna.hololib.client.modules.camera.focus.FocusController;
import com.amynna.hololib.holo.mode.HoloMode;
import com.amynna.hololib.modules.camera.quickaction.QuickActionModule;
import com.amynna.hololib.modules.camera.quickaction.QuickActionWheelWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Module Quick Action du joueur local ({@link QuickActionModule} de sa memoire). Fil client, fin de tick.
 * <ul>
 *     <li>Chaque tick : actions des modules {@code QuickActionProvider} ({@link QuickActionModule#sync}).</li>
 *     <li>Ouverture du focus : la roue (deja placee au centre du regard par le focus, {@code HoloRelativeToOpen})
 *     s'ouvre si elle est affichee et a au moins une action active.</li>
 *     <li>Roue ouverte : fermee avec le focus, si elle n'est plus affichee ou n'a plus d'action, et, si "Masquer si
 *     ignoree", quand le regard s'eloigne trop de son centre (hors ecran).</li>
 * </ul>
 * Fournit aussi la fermeture du focus apres une action ({@link QuickActionModule.Backend}).
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class QuickActionController
{
    private static boolean focused;

    private QuickActionController() {}

    /** Branche la fermeture du focus, au setup client. */
    @Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup
    {
        private Setup() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            event.enqueueWork(() -> QuickActionModule.install(FocusController::close));
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            focused = false;
            return;
        }
        ClientHoloContainer local = ClientHoloWorld.local();
        syncForeign(local);
        QuickActionModule module = HoloModes.find(local, QuickActionModule.class);
        boolean focus = HoloModes.active(local, HoloMode.FOCUS);
        boolean opening = focus && !focused;
        focused = focus;
        if (module == null) return;
        module.sync(local.modules());
        QuickActionWheelWindow wheel = module.wheel();
        boolean available = focus && module.wheelEnabled() && wheel.hasSectors();
        if (opening) {
            wheel.setOpen(available);
        } else if (!available) {
            wheel.setOpen(false);
        } else if (wheel.isOpen() && module.dismissIfIgnored() && mc.screen == null
                && wheel.gazeBeyond(player.getYRot(), player.getXRot())) {
            wheel.setOpen(false);
        }
    }

    /** Roues des autres memoires de joueur recues (joueurs inspectes) : actions de leurs modules partages. */
    private static void syncForeign(ClientHoloContainer local)
    {
        for (ClientHoloContainer container : ClientHoloWorld.containers()) {
            if (container == local || container.scope().owner() == null) continue;
            QuickActionModule module = HoloModes.find(container, QuickActionModule.class);
            if (module != null) module.sync(container.modules());
        }
    }
}
