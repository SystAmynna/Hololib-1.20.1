package com.amynna.hololib.client.modules.camera.editor;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.edit.HoloEditSession;
import com.amynna.hololib.client.edit.HoloEditTools;
import com.amynna.hololib.client.holo.ClientHoloContainer;
import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.client.holo.HoloModes;
import com.amynna.hololib.client.key.HoloKeyMappings;
import com.amynna.hololib.client.modules.camera.focus.FocusController;
import com.amynna.hololib.client.render.holo.HoloPicking;
import com.amynna.hololib.holo.edit.HoloEditRules;
import com.amynna.hololib.holo.edit.HoloEditorModule;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.modules.camera.editor.EditorHudFrame;
import com.amynna.hololib.modules.camera.editor.EditorPanelModule;
import com.amynna.hololib.modules.camera.editor.OwnEditorModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Mode edition du joueur local : les modules editeurs ({@link HoloEditorModule}) de sa memoire. Fil client, fin de
 * tick.
 * <ul>
 *     <li>Touche d'edition (G) : ouvre (en fermant le focus) si au moins un editeur est equipe, ou ferme.</li>
 *     <li>Ouverture : avec l'Own Editor, son repere de l'ecran ({@code EditorReference}) prend le champ de vision et
 *     les proportions de l'ecran, son cadre HUD aussi ; la session d'edition s'ouvre avec le regard courant comme
 *     repere de reference.</li>
 *     <li>Chaque tick : liste du panneau des editeurs ({@link EditorPanelModule}) ; hote du cadre HUD = fenetre de
 *     l'Own Editor s'il est actif, sinon aucun (les objets HUD restent au HUD). Plus aucun editeur : fermeture.</li>
 * </ul>
 * Fournit aussi la partie client du cadre HUD ({@link EditorHudFrame.Backend}).
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class EditorController
{
    private static boolean opened;

    private EditorController() {}

    /** Branche le cadre HUD sur la session, au setup client. */
    @Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup
    {
        private Setup() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            event.enqueueWork(() -> EditorHudFrame.install(new FrameBackend()));
        }
    }

    /** Ferme le mode edition du joueur local s'il est ouvert (ouverture du focus). */
    public static void close()
    {
        HoloEditSession.close();
        opened = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            if (opened) close();
            return;
        }
        ClientHoloContainer local = ClientHoloWorld.local();
        EditorPanelModule panel = HoloModes.find(local, EditorPanelModule.class);
        if (panel != null) panel.window().sync(local.modules());
        while (HoloKeyMappings.EDITOR.consumeClick()) toggle(mc, local);
        if (!opened) return;
        // Plus d'editeur (modules retires, memoire changee), ou session fermee ailleurs.
        if (!hasEditor(local) || !HoloEditSession.active()) {
            close();
            return;
        }
        HoloEditSession.setHudHost(hudHost(HoloModes.find(local, OwnEditorModule.class)));
    }

    private static void toggle(Minecraft mc, ClientHoloContainer local)
    {
        if (opened) {
            close();
            return;
        }
        if (!hasEditor(local)) return;
        FocusController.close();
        LocalPlayer player = mc.player;
        OwnEditorModule own = HoloModes.find(local, OwnEditorModule.class);
        if (own != null) {
            float aspect = (float) mc.getWindow().getGuiScaledWidth()
                    / Math.max(1, mc.getWindow().getGuiScaledHeight());
            own.reference().capture(mc.options.fov().get(), aspect);
            own.window().fitFrame(aspect);
        }
        opened = true;
        HoloEditSession.open(player.getYRot(), player.getXRot(), hudHost(own));
    }

    /** Fenetre du cadre HUD de l'Own Editor s'il est actif, sinon {@code null}. */
    private static HoloObject hudHost(OwnEditorModule own)
    {
        return own != null && own.editorEnabled() ? own.window() : null;
    }

    private static boolean hasEditor(ClientHoloContainer local)
    {
        if (local == null) return false;
        for (HoloModule module : local.modules()) if (module instanceof HoloEditorModule) return true;
        return false;
    }

    /** Partie client du cadre HUD : objets HUD de la session. */
    private static final class FrameBackend implements EditorHudFrame.Backend
    {
        @Override
        public float guiWidth() { return Minecraft.getInstance().getWindow().getGuiScaledWidth(); }

        @Override
        public float guiHeight() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }

        @Override
        public HoloObject objectAt(float guiX, float guiY)
        {
            HoloObject object = HoloPicking.hudPreviewAt(guiX, guiY, guiWidth(), guiHeight());
            if (object == null || object.module() == null || !HoloEditRules.hasTools(object)) return null;
            return HoloEditSession.mayEdit(object, object.module().scope()) ? object : null;
        }

        @Override
        public HoloObject selected() { return HoloEditSession.selectedHud(); }

        @Override
        public void select(HoloObject object) { HoloEditSession.selectHud(object); }

        @Override
        public void grab(HoloObject object) { HoloEditTools.grabHud(object); }

        @Override
        public void scroll(HoloObject object, int steps) { HoloEditTools.scrollHud(object, steps); }
    }
}
