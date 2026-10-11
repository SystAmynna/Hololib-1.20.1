package com.amynna.hololib.client.render;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.cancel.RenderCancels;
import com.amynna.hololib.client.render.cancel.SkyCancelFill;
import com.amynna.hololib.client.render.hud.HudRenderPass;
import com.amynna.hololib.client.render.sky.SkyRenderPass;
import com.amynna.hololib.client.render.sky.SkyRenderer;
import com.amynna.hololib.client.render.view.ViewRenderPass;
import com.amynna.hololib.client.render.world.WorldRenderPass;
import com.amynna.hololib.holo.render.HoloRenderElement;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Seul point d'entree du rendu du mod : route les stages de {@link RenderLevelStageEvent} vers les passes (voir
 * l'ordre de rendu dans {@code package-info}), selon qu'un shaderpack est actif ({@link ShaderPacks}, fige par frame
 * a {@code AFTER_SKY}). Priorite {@code LOWEST} : les autres mods dessinent avant nous, en particulier avant que la
 * passe de vue ne vide la profondeur. Le HUD passe par un overlay Forge enregistre sous tous les overlays vanilla
 * ({@link ModBus}). Il releve aussi le FOV ({@code ComputeFov}) et le roulis ({@code ComputeCameraAngles}), et relaie
 * la couleur du brouillard aux renderers du ciel ({@code ComputeFogColor}), et applique les annulations du
 * RenderCanceler ({@code client.render.cancel} : debut de frame, ciel avec shaderpack, main, overlays du HUD).
 * <p>
 * Toutes les passes recoivent la <b>rotation camera sans bobbing</b> et la <b>projection du monde bobbing compris</b>.
 * Sans shaderpack, vanilla met le bobbing dans la projection : la pose de {@code AFTER_SKY} est la rotation. Avec un
 * shaderpack, Oculus le deplace dans le model-view (pose = bobbing x rotation) : la rotation est reconstruite depuis
 * la camera et le roulis, et le bobbing est remis dans la projection.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class RenderDispatcher
{
    /**
     * Rotation camera de la frame sans bobbing, capturee a {@code AFTER_SKY} (Forge ne la fournit plus a
     * {@code AFTER_LEVEL}).
     */
    private static final Matrix4f CAMERA_ROTATION = new Matrix4f();
    private static final Matrix3f CAMERA_NORMAL = new Matrix3f();
    /** Partie bobbing de la pose de {@code AFTER_SKY} (identite sans shaderpack) : pose = BOB x rotation. */
    private static final Matrix4f BOB = new Matrix4f();
    /** Projection du monde de la frame, bobbing compris : projection de l'evenement x {@link #BOB}. */
    private static final Matrix4f WORLD_PROJECTION = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();
    /** Roulis de la camera de la frame, en degres (apres tous les mods). */
    private static float roll;
    /** Shaderpack actif pour la frame, fige a {@code AFTER_SKY}. */
    private static boolean shaderPack;
    /** FOV effectif de la derniere frame du monde, en degres. */
    private static float fov = 70f;
    /** Couleur du brouillard en cours de calcul, et celle recue par le renderer courant : reutilisees. */
    private static final Vector3f FOG = new Vector3f();
    private static final Vector3f FOG_BEFORE = new Vector3f();
    /** Couleur finale du brouillard de la frame (fond de l'ecran), apres tous les mods. */
    private static final Vector3f FRAME_FOG = new Vector3f();
    /** Renderers dont une exception dans {@code fogColor} a deja ete loguee (cles faibles : pas de fuite). */
    private static final Set<SkyRenderer> FOG_FAILED = Collections.newSetFromMap(new WeakHashMap<>());

    private RenderDispatcher() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeFov(ViewportEvent.ComputeFov event)
    {
        // Faux pour le FOV de la main (70 fixe) : seul celui du monde nous interesse.
        if (event.usedConfiguredFov()) fov = (float) event.getFOV();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event)
    {
        roll = event.getRoll();
    }

    /**
     * Couleur du brouillard et du fond de l'ecran : relayee aux {@link SkyRenderer#fogColor}. Postee par
     * {@code FogRenderer.setupColor}, une fois par frame, avant {@code AFTER_SKY}. Priorite {@code LOWEST} : les
     * renderers recoivent la couleur des autres mods. Sans renderer du ciel, ou si aucun ne la change, l'evenement
     * n'est pas modifie.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event)
    {
        List<SkyRenderer> renderers = HoloRenderers.sky();
        if (renderers.isEmpty()) {
            FRAME_FOG.set(event.getRed(), event.getGreen(), event.getBlue());
            return;
        }
        float red = event.getRed();
        float green = event.getGreen();
        float blue = event.getBlue();
        FOG.set(red, green, blue);
        float partialTick = (float) event.getPartialTick();
        for (SkyRenderer renderer : renderers)
        {
            FOG_BEFORE.set(FOG);
            try
            {
                renderer.fogColor(event.getCamera(), partialTick, FOG);
            }
            catch (RuntimeException e)
            {
                // Aucun etat a restaurer : on garde la couleur recue plutot que de perdre celle de la frame.
                FOG.set(FOG_BEFORE);
                if (FOG_FAILED.add(renderer))
                {
                    HoloLib.LOGGER.error("SkyRenderer {} : exception dans fogColor (loguee une seule fois)", renderer, e);
                }
            }
        }
        if (FOG.x != red || FOG.y != green || FOG.z != blue)
        {
            event.setRed(FOG.x);
            event.setGreen(FOG.y);
            event.setBlue(FOG.z);
        }
        FRAME_FOG.set(FOG);
    }

    /** Debut de frame, avant le rendu du niveau : effets de dimension du RenderCanceler (ciel, nuages, meteo). */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event)
    {
        if (event.phase == TickEvent.Phase.START) RenderCancels.beginFrame();
    }

    /** Main en premiere personne annulee (chaque main, aussi pour le rendu de la main d'Oculus). */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event)
    {
        if (RenderCancels.cancelled(HoloRenderElement.HAND)) event.setCanceled(true);
    }

    /** Overlays vanilla annules (reticule, bloc d'inventaire). */
    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event)
    {
        if (RenderCancels.overlayCancelled(event.getOverlay().id())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY)
        {
            shaderPack = ShaderPacks.inUse();
            captureCamera(event.getPoseStack().last().pose(), event.getCamera());
            event.getProjectionMatrix().mul(BOB, WORLD_PROJECTION);
            if (shaderPack) return;
            SkyRenderPass.render(HoloRenderers.sky(), event.getCamera(), event.getPartialTick(), CAMERA_ROTATION,
                    CAMERA_NORMAL, WORLD_PROJECTION, fov);
        }
        else if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
        {
            // Avec Oculus, les entites ne sont dessinees qu'au profiler "translucent", apres AFTER_ENTITIES.
            if (shaderPack) return;
            WorldRenderPass.render(HoloRenderers.world(), event.getPoseStack(), event.getCamera(), event.getFrustum(),
                    event.getPartialTick());
        }
        else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL)
        {
            event.getProjectionMatrix().mul(BOB, WORLD_PROJECTION);
            if (shaderPack)
            {
                // Monde compose par Oculus dans la cible principale : ciel puis monde par-dessus, selon la profondeur.
                // Ciel annule : le ciel du pack est recouvert avant les SkyRenderer.
                if (RenderCancels.cancelled(HoloRenderElement.SKY)) SkyCancelFill.render(FRAME_FOG);
                SkyRenderPass.renderDeferred(HoloRenderers.sky(), event.getCamera(), event.getPartialTick(),
                        CAMERA_ROTATION, CAMERA_NORMAL, WORLD_PROJECTION, fov);
                WorldRenderPass.renderOverlay(HoloRenderers.world(), CAMERA_ROTATION, CAMERA_NORMAL, WORLD_PROJECTION,
                        event.getCamera(), event.getFrustum(), event.getPartialTick());
            }
            ViewRenderPass.render(HoloRenderers.view(), event.getCamera(), event.getPartialTick(), CAMERA_ROTATION,
                    CAMERA_NORMAL, WORLD_PROJECTION, fov);
        }
    }

    /** Rotation sans bobbing et partie bobbing de la pose de {@code AFTER_SKY}. */
    private static void captureCamera(Matrix4fc pose, Camera camera)
    {
        if (!shaderPack)
        {
            CAMERA_ROTATION.set(pose);
            BOB.identity();
        }
        else
        {
            // Meme rotation que GameRenderer.renderLevel : roulis, tangage, lacet + 180.
            CAMERA_ROTATION.rotationZ(roll * Mth.DEG_TO_RAD)
                    .rotateX(camera.getXRot() * Mth.DEG_TO_RAD)
                    .rotateY((camera.getYRot() + 180f) * Mth.DEG_TO_RAD);
            // Rotation orthonormee : son inverse est sa transposee.
            pose.mul(CAMERA_ROTATION.transpose(INVERSE), BOB);
        }
        CAMERA_NORMAL.set(CAMERA_ROTATION);
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
