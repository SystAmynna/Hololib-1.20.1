package com.amynna.hololib.client.render.modules.camera.targeting;

import com.amynna.hololib.client.render.HoloRenderers;

/**
 * Enregistrement des renderers du ciblage, appele par {@code ModuleRenderers} sur le fil de rendu, <b>apres le
 * radar</b> : le contour vert de la cible est dessine au-dessus de la composition du radar (contour et interieur
 * bleus d'une entite reperee et ciblee).
 */
public final class TargetingRenderers
{
    private TargetingRenderers() {}

    public static void register()
    {
        HoloRenderers.register(new TargetOutlineRenderer());
        HoloRenderers.register(new TargetLinkRenderer());
    }
}
