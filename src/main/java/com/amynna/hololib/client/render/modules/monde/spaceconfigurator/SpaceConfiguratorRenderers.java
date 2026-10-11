package com.amynna.hololib.client.render.modules.monde.spaceconfigurator;

import com.amynna.hololib.client.render.holo.HoloObjectRenderers;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceConfiguratorWindow;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceMapHandle;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceOrbitHandle;

/**
 * Enregistrement des renderers du Space Configurator (les deux poignees, et la fenetre depuis T44 : renderer generique
 * des fenetres plus l'apercu de texture), appele par {@code ModuleRenderers} sur le fil de rendu ; la carte est celle de
 * la Space Map du meme marker ({@code SpaceMapRenderers}, T42).
 */
public final class SpaceConfiguratorRenderers
{
    private SpaceConfiguratorRenderers() {}

    public static void register()
    {
        HoloObjectRenderers.register(SpaceMapHandle.class, new SpaceHandleRenderer());
        HoloObjectRenderers.register(SpaceOrbitHandle.class, new SpaceOrbitHandleRenderer());
        HoloObjectRenderers.register(SpaceConfiguratorWindow.class, new SpaceConfiguratorWindowRenderer());
    }
}
