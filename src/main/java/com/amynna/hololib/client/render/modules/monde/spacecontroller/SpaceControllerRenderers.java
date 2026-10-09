package com.amynna.hololib.client.render.modules.monde.spacecontroller;

import com.amynna.hololib.client.render.holo.HoloObjectRenderers;
import com.amynna.hololib.modules.monde.spacecontroller.SpaceJoystickAim;
import com.amynna.hololib.modules.monde.spacecontroller.SpaceJoystickForward;

/**
 * Enregistrement des renderers du Space Controller, appele par {@code ModuleRenderers} sur le fil de rendu. La fenetre
 * de controle utilise le renderer generique des fenetres (heritage).
 */
public final class SpaceControllerRenderers
{
    private SpaceControllerRenderers() {}

    public static void register()
    {
        HoloObjectRenderers.register(SpaceJoystickForward.class, new SpaceJoystickRenderer.Forward());
        HoloObjectRenderers.register(SpaceJoystickAim.class, new SpaceJoystickRenderer.Aim());
    }
}
