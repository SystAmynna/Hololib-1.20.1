package com.amynna.hololib.modules.camera.crosshaircancel;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.render.HoloRenderCanceler;
import com.amynna.hololib.holo.render.HoloRenderElement;

/**
 * Annule le reticule du joueur proprietaire, tant que le module est dans sa memoire active. Aucun etat ni objet :
 * demonstration du RenderCanceler. Jamais envoye aux observateurs, et ignore chez eux de toute facon.
 */
public final class CrosshairCancelModule extends HoloModule
{
    @Override
    public void onLoad() { HoloRenderCanceler.cancel(this, HoloRenderElement.CROSSHAIR); }

    @Override
    public void onUnload() { HoloRenderCanceler.restoreAll(this); }
}
