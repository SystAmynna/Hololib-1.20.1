package com.amynna.hololib.client.render.cancel;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.render.HoloRenderCanceler;
import com.amynna.hololib.holo.render.HoloRenderElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Etat client du RenderCanceler : demandeurs de chaque {@link HoloRenderElement} (par identite), branche sur
 * {@link HoloRenderCanceler} au setup client. Fil client.
 * <p>
 * Ciel, nuages et meteo passent par les effets de la dimension : tant que l'un d'eux est annule, {@link #beginFrame}
 * remplace {@code ClientLevel.effects} (champ final, SRG {@code f_104564_}, par reflexion) par un
 * {@link CancelingEffects}, puis remet l'original quand plus rien n'est annule. Un nouveau niveau (respawn, dimension)
 * est detecte par reference. Si la reflexion echoue, ces trois elements restent inertes (logue une fois). Main et
 * overlays du HUD (reticule, bloc d'inventaire : {@link #overlayCancelled}) sont annules par {@code RenderDispatcher}
 * (evenements Forge).
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class RenderCancels
{
    private static final Map<HoloRenderElement, Set<Object>> REQUESTERS = new EnumMap<>(HoloRenderElement.class);
    /** Overlays vanilla du HUD par element qui les annule. */
    private static final Map<ResourceLocation, HoloRenderElement> OVERLAYS = new HashMap<>();
    private static final Field EFFECTS = effectsField();
    /** Reflexion indisponible (champ introuvable ou ecriture refusee) : plus aucun essai. */
    private static boolean effectsBroken = EFFECTS == null;

    /** Niveau dont les effets sont enveloppes, et l'enveloppe ; {@code null} si rien n'est installe. */
    private static ClientLevel wrappedLevel;
    private static CancelingEffects wrapper;

    static
    {
        for (HoloRenderElement element : HoloRenderElement.values()) {
            REQUESTERS.put(element, Collections.newSetFromMap(new IdentityHashMap<>()));
        }
        OVERLAYS.put(VanillaGuiOverlay.CROSSHAIR.id(), HoloRenderElement.CROSSHAIR);
        for (VanillaGuiOverlay overlay : new VanillaGuiOverlay[] {VanillaGuiOverlay.HOTBAR, VanillaGuiOverlay.ITEM_NAME,
                VanillaGuiOverlay.PLAYER_HEALTH, VanillaGuiOverlay.ARMOR_LEVEL, VanillaGuiOverlay.FOOD_LEVEL,
                VanillaGuiOverlay.AIR_LEVEL, VanillaGuiOverlay.EXPERIENCE_BAR, VanillaGuiOverlay.MOUNT_HEALTH,
                VanillaGuiOverlay.JUMP_BAR}) {
            OVERLAYS.put(overlay.id(), HoloRenderElement.INVENTORY_HUD);
        }
    }

    private RenderCancels() {}

    public static boolean cancelled(HoloRenderElement element) { return !REQUESTERS.get(element).isEmpty(); }

    /** Vrai si cet overlay du HUD appartient a un element annule. */
    public static boolean overlayCancelled(ResourceLocation overlay)
    {
        HoloRenderElement element = OVERLAYS.get(overlay);
        return element != null && cancelled(element);
    }

    /**
     * Debut de frame, avant {@code LevelRenderer.renderLevel} : installe ou retire l'enveloppe des effets du niveau
     * courant selon l'etat. Aucune allocation hors installation.
     */
    public static void beginFrame()
    {
        if (effectsBroken) return;
        ClientLevel level = Minecraft.getInstance().level;
        // Ancien niveau abandonne : son enveloppe part avec lui.
        if (wrappedLevel != null && wrappedLevel != level) {
            wrappedLevel = null;
            wrapper = null;
        }
        boolean wanted = level != null && (cancelled(HoloRenderElement.SKY) || cancelled(HoloRenderElement.CLOUDS)
                || cancelled(HoloRenderElement.WEATHER));
        if (wanted && wrapper == null) {
            CancelingEffects effects = new CancelingEffects(level.effects());
            if (setEffects(level, effects)) {
                wrappedLevel = level;
                wrapper = effects;
            }
        } else if (!wanted && wrapper != null) {
            // Un autre mod a pu remplacer les effets entre-temps : on ne remet l'original que sur notre enveloppe.
            if (level.effects() == wrapper) setEffects(level, wrapper.original());
            wrappedLevel = null;
            wrapper = null;
        }
    }

    private static void set(Object requester, HoloRenderElement element, boolean cancel)
    {
        if (cancel) REQUESTERS.get(element).add(requester);
        else REQUESTERS.get(element).remove(requester);
    }

    private static void releaseAll(Object requester)
    {
        for (Set<Object> requesters : REQUESTERS.values()) requesters.remove(requester);
    }

    private static boolean setEffects(ClientLevel level, DimensionSpecialEffects effects)
    {
        try {
            EFFECTS.set(level, effects);
            return true;
        } catch (IllegalAccessException | RuntimeException e) {
            effectsBroken = true;
            HoloLib.LOGGER.error("Impossible de remplacer les effets de la dimension : ciel, nuages et meteo non "
                    + "annulables", e);
            return false;
        }
    }

    private static Field effectsField()
    {
        try {
            return ObfuscationReflectionHelper.findField(ClientLevel.class, "f_104564_");
        } catch (RuntimeException e) {
            HoloLib.LOGGER.error("Champ effects de ClientLevel introuvable : ciel, nuages et meteo non annulables", e);
            return null;
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        for (Set<Object> requesters : REQUESTERS.values()) requesters.clear();
        wrappedLevel = null;
        wrapper = null;
    }

    /** Mise en place au setup client : branche {@link HoloRenderCanceler} sur cet etat. */
    @Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup
    {
        private Setup() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            event.enqueueWork(() -> HoloRenderCanceler.install(new HoloRenderCanceler.Backend()
            {
                @Override
                public void set(Object requester, HoloRenderElement element, boolean cancel)
                {
                    RenderCancels.set(requester, element, cancel);
                }

                @Override
                public void releaseAll(Object requester) { RenderCancels.releaseAll(requester); }

                @Override
                public boolean cancelled(HoloRenderElement element) { return RenderCancels.cancelled(element); }
            }));
        }
    }
}
