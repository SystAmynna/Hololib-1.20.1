package com.amynna.hololib.client.render;

import com.amynna.hololib.client.render.sky.SkyRenderer;
import com.amynna.hololib.client.render.view.ViewRenderer;
import com.amynna.hololib.client.render.world.WorldRenderer;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registre des renderers actifs. Fil de rendu uniquement (le fil client en 1.20.1) : les listes sont parcourues par
 * {@link RenderDispatcher} pendant la frame. Les renderers sont dessines dans l'ordre d'enregistrement.
 */
public final class HoloRenderers
{
    private static final List<SkyRenderer> SKY = new ArrayList<>();
    private static final List<ViewRenderer> VIEW = new ArrayList<>();
    private static final List<WorldRenderer<?>> WORLD = new ArrayList<>();
    private static final List<SkyRenderer> SKY_READ_ONLY = Collections.unmodifiableList(SKY);
    private static final List<ViewRenderer> VIEW_READ_ONLY = Collections.unmodifiableList(VIEW);
    private static final List<WorldRenderer<?>> WORLD_READ_ONLY = Collections.unmodifiableList(WORLD);

    private HoloRenderers() {}

    public static void register(SkyRenderer renderer)
    {
        RenderSystem.assertOnRenderThread();
        if (!SKY.contains(renderer)) SKY.add(renderer);
    }

    public static void register(ViewRenderer renderer)
    {
        RenderSystem.assertOnRenderThread();
        if (!VIEW.contains(renderer)) VIEW.add(renderer);
    }

    public static void register(WorldRenderer<?> renderer)
    {
        RenderSystem.assertOnRenderThread();
        if (!WORLD.contains(renderer)) WORLD.add(renderer);
    }

    public static void unregister(SkyRenderer renderer)
    {
        RenderSystem.assertOnRenderThread();
        SKY.remove(renderer);
    }

    public static void unregister(ViewRenderer renderer)
    {
        RenderSystem.assertOnRenderThread();
        VIEW.remove(renderer);
    }

    public static void unregister(WorldRenderer<?> renderer)
    {
        RenderSystem.assertOnRenderThread();
        WORLD.remove(renderer);
    }

    static List<SkyRenderer> sky() { return SKY_READ_ONLY; }

    static List<ViewRenderer> view() { return VIEW_READ_ONLY; }

    static List<WorldRenderer<?>> world() { return WORLD_READ_ONLY; }
}
