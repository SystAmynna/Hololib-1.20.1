package com.amynna.hololib.client.modules.camera.config;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.holo.ClientHoloContainer;
import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.client.holo.HoloModes;
import com.amynna.hololib.client.modules.camera.focus.FocusController;
import com.amynna.hololib.holo.edit.HoloEditMath;
import com.amynna.hololib.holo.mode.HoloMode;
import com.amynna.hololib.holo.object.HoloObject;
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
import org.joml.Quaternionf;
import org.joml.Vector2fc;

/**
 * Module Config du joueur local ({@link ConfigModule} de sa memoire). Fil client, fin de tick.
 * <ul>
 *     <li>Chaque tick : liste des modules {@code Configurable} ({@link ConfigListWindow#sync}), aussi pour les
 *     joueurs inspectes (leur liste est publique).</li>
 *     <li>Ouverture du focus : chaque fenetre de Config (liste et fenetres de configuration) est ouverte si elle est
 *     epinglee, fermee sinon ; elle a deja ete placee devant le regard par le focus ({@code HoloRelativeToOpen}).</li>
 * </ul>
 * Fournit aussi le placement devant le regard des fenetres et le recalcul de leur position relative apres un
 * deplacement au cadre ({@link ConfigWindow.Backend}).
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class ConfigController
{
    private static final HoloEditMath MATH = new HoloEditMath();
    private static final Quaternionf GAZE = new Quaternionf();

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
            event.enqueueWork(() -> ConfigWindow.install(new ConfigWindow.Backend()
            {
                @Override
                public void placeInFront(HoloObject object)
                {
                    LocalPlayer player = Minecraft.getInstance().player;
                    if (player != null) FocusController.placeRelative(object, player.getYRot(), player.getXRot());
                }

                @Override
                public void storeOffset(HoloObject object) { ConfigController.storeOffset(object); }
            }));
        }
    }

    /**
     * Position relative d'une fenetre deplacee : son placement {@code Local} fixe ramene au regard avec lequel elle a
     * ete placee ({@link FocusController#placement}, sinon le regard courant), selon son repere de rotation.
     */
    private static void storeOffset(HoloObject object)
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if (object.openOffset() == null || player == null) return;
        Vector2fc placed = FocusController.placement(object);
        float yaw = placed != null ? placed.x() : player.getYRot();
        float pitch = placed != null ? placed.y() : player.getXRot();
        HoloEditMath.yawPitch(GAZE, yaw, pitch);
        MATH.toOffset(object.transform(), GAZE, object.rotationFrame(), object.openOffset());
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
        syncForeign(local);
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

    /** Listes de Config des autres memoires de joueur recues (joueurs inspectes). */
    private static void syncForeign(ClientHoloContainer local)
    {
        for (ClientHoloContainer container : ClientHoloWorld.containers()) {
            if (container == local || container.scope().owner() == null) continue;
            ConfigModule config = HoloModes.find(container, ConfigModule.class);
            if (config != null) config.list().sync(container.modules(), config);
        }
    }
}
