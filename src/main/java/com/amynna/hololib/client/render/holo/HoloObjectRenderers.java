package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.object.HoloObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Registre des {@link HoloObjectRenderer} par classe d'objet. Une classe sans renderer propre utilise celui de sa
 * superclasse la plus proche. Fil de rendu uniquement ; enregistrer au {@code FMLClientSetupEvent}
 * ({@code enqueueWork}).
 */
public final class HoloObjectRenderers
{
    private static final HoloObjectRenderer<HoloObject> NONE = (object, pose, buffers, light, ctx) -> {};

    private static final Map<Class<?>, HoloObjectRenderer<?>> REGISTERED = new HashMap<>();
    /** Resolution par classe concrete, avec heritage ; videe a chaque enregistrement. */
    private static final Map<Class<?>, HoloObjectRenderer<?>> RESOLVED = new HashMap<>();

    private HoloObjectRenderers() {}

    public static <T extends HoloObject> void register(Class<T> type, HoloObjectRenderer<? super T> renderer)
    {
        RenderSystem.assertOnRenderThread();
        REGISTERED.put(type, renderer);
        RESOLVED.clear();
    }

    @SuppressWarnings("unchecked")
    static void render(HoloObject object, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        HoloObjectRenderer<HoloObject> renderer = (HoloObjectRenderer<HoloObject>) RESOLVED.get(object.getClass());
        if (renderer == null) {
            renderer = (HoloObjectRenderer<HoloObject>) resolve(object.getClass());
            RESOLVED.put(object.getClass(), renderer);
        }
        renderer.render(object, pose, buffers, packedLight, ctx);
    }

    private static HoloObjectRenderer<?> resolve(Class<?> type)
    {
        for (Class<?> c = type; c != null && HoloObject.class.isAssignableFrom(c); c = c.getSuperclass()) {
            HoloObjectRenderer<?> renderer = REGISTERED.get(c);
            if (renderer != null) return renderer;
        }
        HoloLib.LOGGER.warn("Aucun HoloObjectRenderer pour {}", type.getName());
        return NONE;
    }
}
