package com.amynna.hololib.client.render.modules.camera.attackguard;

import com.amynna.hololib.client.render.HoloRenderers;

/** Enregistrement de la croix rouge d'Attack Guard, appele par {@code ModuleRenderers} sur le fil de rendu. */
public final class AttackGuardRenderers
{
    private AttackGuardRenderers() {}

    public static void register()
    {
        HoloRenderers.register(new AttackGuardCrossRenderer());
    }
}
