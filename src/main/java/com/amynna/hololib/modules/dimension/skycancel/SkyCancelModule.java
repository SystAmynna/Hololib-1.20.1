package com.amynna.hololib.modules.dimension.skycancel;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.render.HoloRenderCanceler;
import com.amynna.hololib.holo.render.HoloRenderElement;

/**
 * Annule le ciel vanilla (fond, soleil, lune, etoiles...) pour tous les joueurs de la dimension, tant que le module
 * est installe. Aucun etat ni objet : demonstration du RenderCanceler.
 */
public final class SkyCancelModule extends HoloModule
{
    /** Ignore cote serveur ({@code HoloRenderCanceler} ne retient que les miroirs clients). */
    @Override
    public void onLoad() { HoloRenderCanceler.cancel(this, HoloRenderElement.SKY); }

    @Override
    public void onUnload() { HoloRenderCanceler.restoreAll(this); }

    /** Sans objet public, le module ne serait jamais envoye aux joueurs de la dimension. */
    @Override
    public boolean sharedWithObservers() { return true; }
}
