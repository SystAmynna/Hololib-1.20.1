package com.amynna.hololib.client.render.modules.camera.radar;

import com.amynna.hololib.client.render.HoloRenderers;

/**
 * Enregistrement du renderer du radar, appele par {@code ModuleRenderers} sur le fil de rendu, apres les autres
 * renderers des modules sauf le ciblage : le radar compose son image plein ecran par-dessus ce que les
 * {@code WorldRenderer} precedents ont dessine, et le contour de la cible passe au-dessus du sien. Les fenetres du
 * module sont des fenetres : rien a enregistrer.
 */
public final class RadarRenderers
{
    private RadarRenderers() {}

    public static void register()
    {
        HoloRenderers.register(new RadarRenderer());
    }
}
