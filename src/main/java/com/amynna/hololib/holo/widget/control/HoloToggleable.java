package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;

/**
 * Trait : interrupteur ON / OFF. Un clic gauche ou droit demande l'etat inverse ({@link #onToggle}) ; c'est a
 * l'implementation de decider de l'appliquer tout de suite ou d'attendre le serveur. A implementer : l'etat.
 */
public interface HoloToggleable extends HoloClickable
{
    boolean isOn();

    void setOn(boolean on);

    /** Le joueur demande l'etat {@code requested}. */
    void onToggle(HoloInteraction ctx, boolean requested);

    @Override
    default boolean clickableBy(HoloControl button)
    {
        return button == HoloControl.LEFT_CLICK || button == HoloControl.RIGHT_CLICK;
    }

    @Override
    default void onClick(HoloInteraction ctx, HoloControl button) { onToggle(ctx, !isOn()); }
}
