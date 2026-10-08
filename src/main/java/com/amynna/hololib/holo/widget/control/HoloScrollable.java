package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.interact.HoloInteraction;

/**
 * Trait : element qui prend la molette quand il est vise (le changement d'emplacement de la barre est bloque). Les
 * deltas sont convertis en crans entiers (au moins 1, signe conserve) pour {@link #onScrolled}.
 */
public interface HoloScrollable extends HoloInteractible
{
    /** Crans de molette : positif vers le haut. */
    void onScrolled(HoloInteraction ctx, int steps);

    /** Vrai (defaut) si un cran pris par l'element joue le son de molette (cote client). */
    default boolean scrollSound() { return true; }

    @Override
    default boolean interceptsScroll() { return true; }

    @Override
    default void onScroll(HoloInteraction ctx, double delta)
    {
        int steps = (int) Math.round(delta);
        if (steps == 0) steps = delta > 0 ? 1 : -1;
        onScrolled(ctx, steps);
    }
}
