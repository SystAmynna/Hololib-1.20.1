package com.amynna.hololib.client.render.modules.monde.areadisplayer;

import com.amynna.hololib.client.render.holo.HoloObjectRenderers;
import com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerCube;

/**
 * Enregistrement des renderers de l'Area Displayer, appele par {@code ModuleRenderers} sur le fil de rendu : le pave.
 * La fenetre de l'Area Displayer Configurator utilise le renderer generique des fenetres (heritage).
 */
public final class AreaDisplayerRenderers
{
    private AreaDisplayerRenderers() {}

    public static void register()
    {
        AreaTileGpu.install();
        HoloObjectRenderers.register(AreaDisplayerCube.class, new AreaDisplayerCubeRenderer());
    }
}
