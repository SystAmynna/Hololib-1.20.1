package com.amynna.hololib.modules.monde.triggerinterrupter;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.control.HoloScrollable;
import com.amynna.hololib.holo.widget.control.HoloWidgetMultiButton;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Bouton du Trigger Interrupter : prend les trois clics (gauche, droit, molette) et la molette, et signale a son
 * {@link Listener} l'appui, le relachement, le battement tant qu'un clic est maintenu (le serveur libere un maintien
 * qui se tait) et chaque cran de molette. Aucune logique de jeu : tout part en action au module, que le serveur valide.
 */
final class TriggerButton extends HoloWidgetMultiButton implements HoloScrollable
{
    /** Ticks entre deux battements d'un maintien (le serveur tolere {@code TriggerInterrupterModule.HOLD_TIMEOUT}). */
    static final int BEAT_TICKS = 10;

    /** Evenements du bouton ; {@code button} = {@link HoloControl#mouseButton()} (0 gauche, 1 droit, 2 molette). */
    interface Listener
    {
        void down(int button);

        void up(int button);

        /** Battement : boutons encore maintenus, un bit par bouton. */
        void beat(int heldMask);

        void scroll(boolean up);
    }

    private final Listener listener;
    private int held;
    private int beatTicks;

    TriggerButton(float x, float y, float width, float height, Listener listener)
    {
        super(x, y, width, height, Component.empty());
        this.listener = Objects.requireNonNull(listener);
        // Les clics ne sont interceptes que s'ils ont une action : les traitements sont dans onClick / onClickRelease.
        on(HoloControl.LEFT_CLICK, b -> {});
        on(HoloControl.RIGHT_CLICK, b -> {});
        on(HoloControl.MIDDLE_CLICK, b -> {});
    }

    @Override
    public void onClick(HoloInteraction ctx, HoloControl button)
    {
        super.onClick(ctx, button);
        int bit = 1 << button.mouseButton();
        if ((held & bit) != 0) return;
        held |= bit;
        beatTicks = 0;
        listener.down(button.mouseButton());
    }

    @Override
    public void onClickRelease(HoloInteraction ctx, HoloControl button)
    {
        super.onClickRelease(ctx, button);
        int bit = 1 << button.mouseButton();
        if ((held & bit) == 0) return;
        held &= ~bit;
        listener.up(button.mouseButton());
    }

    @Override
    public void onScrolled(HoloInteraction ctx, int steps)
    {
        listener.scroll(steps > 0);
    }

    @Override
    public void tick()
    {
        if (held == 0) return;
        if (++beatTicks >= BEAT_TICKS) {
            beatTicks = 0;
            listener.beat(held);
        }
    }
}
