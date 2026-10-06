package com.amynna.hololib.client.render.modules.dimension.planet1;

import com.amynna.hololib.client.render.holo.HoloObjectRenderers;
import com.amynna.hololib.modules.dimension.planet1.Planet1Planet;
import com.amynna.hololib.modules.dimension.planet1.Planet1Ring;

/** Enregistrement des renderers du module, appele par {@code ModuleRenderers} sur le fil de rendu. */
public final class Planet1Renderers
{
    private Planet1Renderers() {}

    public static void register()
    {
        HoloObjectRenderers.register(Planet1Planet.class, new Planet1PlanetRenderer());
        HoloObjectRenderers.register(Planet1Ring.class, new Planet1RingRenderer());
    }
}
