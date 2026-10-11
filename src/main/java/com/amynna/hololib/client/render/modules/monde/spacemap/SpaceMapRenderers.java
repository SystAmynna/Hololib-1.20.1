package com.amynna.hololib.client.render.modules.monde.spacemap;

import com.amynna.hololib.client.render.holo.HoloObjectRenderers;
import com.amynna.hololib.modules.monde.spacemap.SpaceMapCube;

/**
 * Enregistrement des renderers de la Space Map, appele par {@code ModuleRenderers} sur le fil de rendu : le cube. La
 * fenetre de navigation utilise le renderer generique des fenetres (heritage).
 */
public final class SpaceMapRenderers
{
    private SpaceMapRenderers() {}

    public static void register()
    {
        HoloObjectRenderers.register(SpaceMapCube.class, new SpaceMapCubeRenderer());
    }
}
