package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.interact.HoloInteraction;

/**
 * Trait : element qui reagit aux clics bruts de la souris quand il est vise. Les trois clics sont ramenes a
 * {@link #onClick} / {@link #onClickRelease} avec le bouton en parametre ; seuls les boutons ou
 * {@link #clickableBy} est vrai sont interceptes (les autres vont au parent ou au jeu). Le relachement va a l'element
 * qui a pris l'appui, meme si le regard est parti ; entre les deux, {@code onDrag} est appele chaque frame.
 */
public interface HoloClickable extends HoloInteractible
{
    /** Vrai si ce bouton ({@code LEFT_CLICK}, {@code RIGHT_CLICK}, {@code MIDDLE_CLICK}) declenche l'element. */
    boolean clickableBy(HoloControl button);

    void onClick(HoloInteraction ctx, HoloControl button);

    default void onClickRelease(HoloInteraction ctx, HoloControl button) {}

    @Override
    default boolean interceptsClick(HoloControl button) { return clickableBy(button); }

    @Override
    default void onLeftClick(HoloInteraction ctx) { onClick(ctx, HoloControl.LEFT_CLICK); }

    @Override
    default void onLeftClickRelease(HoloInteraction ctx) { onClickRelease(ctx, HoloControl.LEFT_CLICK); }

    @Override
    default void onRightClick(HoloInteraction ctx) { onClick(ctx, HoloControl.RIGHT_CLICK); }

    @Override
    default void onRightClickRelease(HoloInteraction ctx) { onClickRelease(ctx, HoloControl.RIGHT_CLICK); }

    @Override
    default void onMiddleClick(HoloInteraction ctx) { onClick(ctx, HoloControl.MIDDLE_CLICK); }

    @Override
    default void onMiddleClickRelease(HoloInteraction ctx) { onClickRelease(ctx, HoloControl.MIDDLE_CLICK); }
}
