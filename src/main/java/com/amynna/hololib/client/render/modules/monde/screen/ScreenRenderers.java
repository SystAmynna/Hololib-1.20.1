package com.amynna.hololib.client.render.modules.monde.screen;

import com.amynna.hololib.client.render.holo.HoloObjectRenderers;
import com.amynna.hololib.modules.monde.screen.ScreenDisplay;

/**
 * Enregistrement des renderers du Screen, appele par {@code ModuleRenderers} sur le fil de rendu : l'ecran. La
 * fenetre du Screen Configurator utilise le renderer generique des fenetres (heritage).
 */
public final class ScreenRenderers
{
    private ScreenRenderers() {}

    public static void register()
    {
        HoloObjectRenderers.register(ScreenDisplay.class, new ScreenDisplayRenderer());
    }
}
