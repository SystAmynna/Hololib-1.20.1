package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.capture.HoloCapture;
import com.amynna.hololib.holo.capture.HoloCaptor;
import com.amynna.hololib.holo.capture.HoloCaptures;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;

/**
 * Trait : element pilote par les controles de deplacement. Un clic gauche ou droit demarre une capture (liee a
 * l'objet) des quatre directions ({@link HoloDirections}) et d'Echap / Entree, qui la terminent ; le joueur ne bouge
 * plus pendant ce temps. L'etat des directions est tenu dans {@link #directions()} et remis a zero a la fin. Une
 * implementation qui surcharge {@code onCaptureEnd} appelle {@code HoloDirectionalInput.super.onCaptureEnd(ctx)}.
 */
public interface HoloDirectionalInput extends HoloClickable, HoloCaptor
{
    HoloDirections directions();

    /** Vrai pendant la capture. */
    default boolean controlling() { return HoloCaptures.isCapturing(this); }

    @Override
    default boolean clickableBy(HoloControl button)
    {
        return button == HoloControl.LEFT_CLICK || button == HoloControl.RIGHT_CLICK;
    }

    @Override
    default void onClick(HoloInteraction ctx, HoloControl button) { ctx.capture(this); }

    @Override
    default boolean captures(HoloControl control)
    {
        return HoloDirections.isDirection(control) || control.equals(HoloControl.ESCAPE)
                || control.equals(HoloControl.ENTER);
    }

    @Override
    default void onPress(HoloCapture ctx, HoloControl control)
    {
        if (!directions().set(control, true)) ctx.release();
    }

    @Override
    default void onRelease(HoloCapture ctx, HoloControl control) { directions().set(control, false); }

    @Override
    default void onCaptureEnd(HoloCapture ctx) { directions().clear(); }
}
