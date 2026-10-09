package com.amynna.hololib.client.render.modules.dimension.space;

import com.amynna.hololib.client.render.HoloRenderers;

/** Enregistrement du renderer du ciel spatial, appele par {@code ModuleRenderers} sur le fil de rendu. */
public final class SpaceRenderers
{
    private SpaceRenderers() {}

    public static void register()
    {
        HoloRenderers.register(new SpaceSkyRenderer());
    }
}
