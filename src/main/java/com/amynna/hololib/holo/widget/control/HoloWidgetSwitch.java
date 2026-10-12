package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Interrupteur ON / OFF avec un libelle : un clic gauche ou droit demande l'etat inverse a l'ecouteur. En mode
 * <b>predictif</b> (defaut), l'interrupteur bascule aussitot ; sinon il n'affiche que l'etat pose par {@link #setOn}
 * (typiquement depuis l'etat synchronise du module), pour un reglage que le serveur peut refuser.
 */
public class HoloWidgetSwitch extends HoloWidget implements HoloToggleable, HoloHoverable
{
    private static final float TRACK_WIDTH = 22f;
    private static final float TRACK_HEIGHT = 10f;

    private Component label;
    private BiConsumer<? super HoloWidgetSwitch, Boolean> onToggle;
    private boolean predictive = true;
    private boolean on;
    private boolean hovered;
    private HoloColor onColor = HoloColor.selection(0xFF).darker(0.19f);
    private HoloColor offColor = HoloColor.special(0xFF).darker(0.62f);
    private HoloColor textColor = HoloColor.special(0xFF);
    private static final HoloColor HOVER = HoloColor.special(0x20);
    private static final HoloColor KNOB = HoloColor.special(0xFF);

    public HoloWidgetSwitch(float x, float y, float width, float height, Component label,
                            BiConsumer<? super HoloWidgetSwitch, Boolean> onToggle)
    {
        super(x, y, width, height);
        this.label = Objects.requireNonNull(label);
        this.onToggle = Objects.requireNonNull(onToggle);
    }

    public HoloWidgetSwitch setLabel(Component label)
    {
        this.label = Objects.requireNonNull(label);
        return this;
    }

    /** Faux : l'etat affiche ne change que par {@link #setOn} (etat confirme par le serveur). */
    public HoloWidgetSwitch setPredictive(boolean predictive)
    {
        this.predictive = predictive;
        return this;
    }

    public HoloWidgetSwitch setColors(int onColor, int offColor, int textColor)
    {
        return setColors(HoloColor.fixed(onColor), HoloColor.fixed(offColor), HoloColor.fixed(textColor));
    }

    /** Piste allumee, piste eteinte, libelle (par defaut : selection, gris, speciale). */
    public HoloWidgetSwitch setColors(HoloColor onColor, HoloColor offColor, HoloColor textColor)
    {
        this.onColor = onColor;
        this.offColor = offColor;
        this.textColor = textColor;
        return this;
    }

    @Override
    public boolean isOn() { return on; }

    @Override
    public void setOn(boolean on) { this.on = on; }

    @Override
    public void onToggle(HoloInteraction ctx, boolean requested)
    {
        if (predictive) on = requested;
        onToggle.accept(this, requested);
    }

    @Override
    public boolean hovered() { return hovered; }

    @Override
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        if (hovered) canvas.fill(0, 0, width(), height(), canvas.color(HOVER));
        canvas.text(label, 2f, (height() - canvas.lineHeight()) / 2f + 1f, canvas.color(textColor));
        float trackX = width() - TRACK_WIDTH - 2f;
        float trackY = (height() - TRACK_HEIGHT) / 2f;
        canvas.fill(trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, canvas.color(on ? onColor : offColor));
        float knob = TRACK_HEIGHT - 2f;
        canvas.fill(on ? trackX + TRACK_WIDTH - knob - 1f : trackX + 1f, trackY + 1f, knob, knob, canvas.color(KNOB));
    }
}
