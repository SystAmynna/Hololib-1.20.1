package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;

/**
 * Trait : element qu'on deplace au regard. Le clic ({@link #dragButton()}, gauche par defaut) prend l'element au point
 * vise ; tant qu'il reste enfonce, chaque frame donne l'ecart entre le regard (projete sur le plan de l'objet, meme
 * hors de l'element) et ce point de prise : {@link #onDragMove} doit deplacer l'element d'autant pour que le point de
 * prise suive le regard. Au relachement, {@link #onDrop}. A implementer : l'etat ({@code new HoloDragState()}) et le
 * deplacement.
 */
public interface HoloDraggable extends HoloClickable
{
    HoloDragState dragState();

    /** Deplacer l'element de {@code (dx, dy)} pixels (repere du parent), en le bornant si besoin. */
    void onDragMove(HoloInteraction ctx, float dx, float dy);

    /** Fin du glisser : typiquement, envoyer la position au serveur. */
    default void onDrop(HoloInteraction ctx) {}

    default HoloControl dragButton() { return HoloControl.LEFT_CLICK; }

    default boolean dragging() { return dragState().dragging(); }

    @Override
    default boolean clickableBy(HoloControl button) { return button == dragButton(); }

    @Override
    default void onClick(HoloInteraction ctx, HoloControl button) { dragState().grab(ctx.x(), ctx.y()); }

    @Override
    default void onDrag(HoloInteraction ctx, HoloControl button)
    {
        HoloDragState state = dragState();
        if (!state.dragging()) return;
        float dx = ctx.x() - state.grabX();
        float dy = ctx.y() - state.grabY();
        if (dx != 0f || dy != 0f) onDragMove(ctx, dx, dy);
    }

    @Override
    default void onClickRelease(HoloInteraction ctx, HoloControl button)
    {
        if (!dragState().dragging()) return;
        dragState().release();
        onDrop(ctx);
    }
}
