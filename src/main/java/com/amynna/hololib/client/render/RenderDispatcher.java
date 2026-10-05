package com.amynna.hololib.client.render;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.hud.HudRenderPass;
import com.amynna.hololib.client.render.sky.SkyRenderPass;
import com.amynna.hololib.client.render.view.ViewRenderPass;
import com.amynna.hololib.client.render.world.WorldRenderPass;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Seul point d'entree du rendu du mod : route les stages de {@link RenderLevelStageEvent} vers les passes (voir
 * l'ordre de rendu dans {@code package-info}). Priorite {@code LOWEST} : les autres mods dessinent avant nous, en
 * particulier avant que la passe de vue ne vide la profondeur. Le HUD passe par un overlay Forge enregistre sous tous
 * les overlays vanilla ({@link ModBus}).
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class RenderDispatcher
{
    /** Rotation camera de la frame, capturee a {@code AFTER_SKY} (Forge ne la fournit plus a {@code AFTER_LEVEL}). */
    private static final Matrix4f CAMERA_ROTATION = new Matrix4f();
    private static final Matrix3f CAMERA_NORMAL = new Matrix3f();
    /** FOV effectif de la derniere frame du monde, en degres. */
    private static float fov = 70f;

    private RenderDispatcher() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeFov(ViewportEvent.ComputeFov event)
    {
        // Faux pour le FOV de la main (70 fixe) : seul celui du monde nous interesse.
        if (event.usedConfiguredFov()) fov = (float) event.getFOV();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY)
        {
            PoseStack.Pose camera = event.getPoseStack().last();
            CAMERA_ROTATION.set(camera.pose());
            CAMERA_NORMAL.set(camera.normal());
            SkyRenderPass.render(HoloRenderers.sky(), event.getCamera(), event.getPartialTick(), CAMERA_ROTATION,
                    CAMERA_NORMAL);
        }
        else if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES)
        {
            WorldRenderPass.render(HoloRenderers.world(), event.getPoseStack(), event.getCamera(), event.getFrustum(),
                    event.getPartialTick());
        }
        else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL)
        {
            ViewRenderPass.render(HoloRenderers.view(), event.getCamera(), event.getPartialTick(), CAMERA_ROTATION,
                    CAMERA_NORMAL, event.getProjectionMatrix(), fov);
        }
    }

    /** Abonnements au bus du mod. */
    @Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus
    {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event)
        {
            event.registerBelowAll("holo_hud", (gui, graphics, partialTick, width, height) ->
                    HudRenderPass.render(HoloRenderers.hud(), graphics, partialTick, width, height));
        }
    }
}
