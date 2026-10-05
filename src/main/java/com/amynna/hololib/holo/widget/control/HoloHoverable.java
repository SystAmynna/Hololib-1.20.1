package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.interact.HoloInteraction;

/**
 * Trait : element qui sait s'il est regarde. Les methodes par defaut tiennent l'etat ({@link #setHovered}) et
 * signalent le changement a {@link #onHoverChanged}. A implementer : l'etat ({@code private boolean hovered;}).
 */
public interface HoloHoverable extends HoloInteractible
{
    boolean hovered();

    void setHovered(boolean hovered);

    /** Le survol commence ou se termine (marge de sortie comprise, voir {@link #hoverMargin()}). */
    default void onHoverChanged(HoloInteraction ctx, boolean hovered) {}

    @Override
    default void onHover(HoloInteraction ctx)
    {
        setHovered(true);
        onHoverChanged(ctx, true);
    }

    @Override
    default void onHoverRelease(HoloInteraction ctx)
    {
        setHovered(false);
        onHoverChanged(ctx, false);
    }
}
