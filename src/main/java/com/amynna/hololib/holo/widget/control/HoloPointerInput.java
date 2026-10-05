package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.capture.HoloCapture;
import com.amynna.hololib.holo.capture.HoloCaptor;
import com.amynna.hololib.holo.capture.HoloCaptures;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;

/**
 * Trait : element qui capture les mouvements de la souris (la camera ne tourne plus ; deltas en degres dans
 * {@code onMouseMove}). La capture, liee a l'objet, demarre au clic gauche ou droit, ou des que le regard entre si
 * {@link #captureOnHover()} ; Echap ou Entree la terminent. Le regard restant fige sur l'element, il faut le quitter
 * puis y revenir pour une nouvelle capture au survol. A implementer : l'etat de survol et {@code onMouseMove}.
 */
public interface HoloPointerInput extends HoloClickable, HoloHoverable, HoloCaptor
{
    /** Vrai : capture des que le regard entre ; faux (defaut) : au clic. */
    default boolean captureOnHover() { return false; }

    default boolean capturing() { return HoloCaptures.isCapturing(this); }

    @Override
    default boolean clickableBy(HoloControl button)
    {
        return !captureOnHover() && (button == HoloControl.LEFT_CLICK || button == HoloControl.RIGHT_CLICK);
    }

    @Override
    default void onClick(HoloInteraction ctx, HoloControl button) { ctx.capture(this); }

    @Override
    default void onHoverChanged(HoloInteraction ctx, boolean hovered)
    {
        if (hovered && captureOnHover()) ctx.capture(this);
    }

    @Override
    default boolean captures(HoloControl control)
    {
        return control.equals(HoloControl.MOUSE_MOVE) || control.equals(HoloControl.ESCAPE)
                || control.equals(HoloControl.ENTER);
    }

    @Override
    default void onPress(HoloCapture ctx, HoloControl control)
    {
        if (control.equals(HoloControl.ESCAPE) || control.equals(HoloControl.ENTER)) ctx.release();
    }
}
