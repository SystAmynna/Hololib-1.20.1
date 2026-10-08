package com.amynna.hololib.client.modules.camera.config;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.holo.ClientHoloContainer;
import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.client.holo.HoloModes;
import com.amynna.hololib.client.modules.camera.focus.FocusController;
import com.amynna.hololib.holo.mode.HoloMode;
import com.amynna.hololib.modules.camera.config.ConfigListWindow;
import com.amynna.hololib.modules.camera.config.ConfigModule;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Module Config du joueur local ({@link ConfigModule} de sa memoire). Fil client, fin de tick.
 * <ul>
 *     <li>Chaque tick : liste des modules {@code Configurable} ({@link ConfigListWindow#sync}).</li>
 *     <li>Ouverture du focus : chaque fenetre de Config (liste et fenetres de configuration) est ouverte si elle est
 *     epinglee, fermee sinon ; elle a deja ete placee devant le regard par le focus ({@code HoloRelativeToOpen}).</li>
 * </ul>
 * Fournit aussi le placement devant le regard des fenetres ({@link ConfigWindow.Backend}).
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class ConfigController
{
    private static boolean focused;

    private ConfigController() {}

    /** Branche le placement des fenetres, au setup client. */
    @Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup
    {
        private Setup() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            event.enqueueWork(() -> ConfigWindow.install(object -> {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) FocusController.placeRelative(object, player.getYRot(), player.getXRot());
            }));
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().player == null) {
            focused = false;
            return;
        }
        ClientHoloContainer local = ClientHoloWorld.local();
        ConfigModule config = HoloModes.find(local, ConfigModule.class);
        boolean focus = HoloModes.active(local, HoloMode.FOCUS);
        boolean opening = focus && !focused;
        focused = focus;
        if (config == null) return;
        ConfigListWindow list = config.list();
        list.sync(local.modules(), config);
        if (!opening) return;
        list.setOpen(list.pinned());
        for (ConfigWindow window : list.windows()) window.setOpen(window.pinned());
    }
}
