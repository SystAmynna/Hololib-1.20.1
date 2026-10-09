package com.amynna.hololib.client.render.modules.monde.spaceconfigurator;

import com.amynna.hololib.client.render.holo.HoloObjectRenderers;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceMapCube;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceMapHandle;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceOrbitHandle;

/**
 * Enregistrement des renderers du Space Configurator, appele par {@code ModuleRenderers} sur le fil de rendu. La
 * fenetre utilise le renderer generique des fenetres (heritage).
 */
public final class SpaceConfiguratorRenderers
{
    private SpaceConfiguratorRenderers() {}

    public static void register()
    {
        HoloObjectRenderers.register(SpaceMapCube.class, new SpaceMapCubeRenderer());
        HoloObjectRenderers.register(SpaceMapHandle.class, new SpaceHandleRenderer());
        HoloObjectRenderers.register(SpaceOrbitHandle.class, new SpaceOrbitHandleRenderer());
    }
}
