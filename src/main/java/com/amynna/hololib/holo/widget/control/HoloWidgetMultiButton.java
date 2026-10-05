package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Bouton a plusieurs actions : une par clic ({@link #on}, gauche, droit et / ou molette). Seuls les clics associes a
 * une action sont interceptes. L'action s'execute cote client a l'appui : pour agir sur le jeu, elle envoie une action
 * au module ({@code HoloModule.sendAction(source, ...)}), que le serveur valide. Eclairci au survol, plus encore tant
 * qu'un clic est maintenu. Heritable : {@link #draw} et les couleurs se redefinissent.
 */
public class HoloWidgetMultiButton extends HoloWidget implements HoloClickable, HoloHoverable
{
    protected static final int HOVER_OVERLAY = 0x30FFFFFF;
    protected static final int PRESSED_OVERLAY = 0x60FFFFFF;

    private final Map<HoloControl, Consumer<? super HoloWidgetMultiButton>> actions = new LinkedHashMap<>();
    private Component text;
    private int background = 0x6020A0FF;
    private int border = 0xFF40C0FF;
    private int textColor = 0xFFFFFFFF;
    private boolean hovered;
    /** Boutons maintenus, un bit par bouton ({@link HoloControl#mouseButton()}). */
    private int pressed;

    public HoloWidgetMultiButton(float x, float y, float width, float height, Component text)
    {
        super(x, y, width, height);
        this.text = Objects.requireNonNull(text);
    }

    /** Associe une action a un clic ({@code LEFT_CLICK}, {@code RIGHT_CLICK}, {@code MIDDLE_CLICK}). */
    public HoloWidgetMultiButton on(HoloControl button, Consumer<? super HoloWidgetMultiButton> action)
    {
        if (button.mouseButton() < 0) throw new IllegalArgumentException("Pas un bouton de souris : " + button);
        actions.put(button, Objects.requireNonNull(action));
        return this;
    }

    public HoloWidgetMultiButton off(HoloControl button)
    {
        actions.remove(button);
        return this;
    }

    /** Retire toutes les actions. */
    protected void clearActions() { actions.clear(); }

    public Component text() { return text; }

    public HoloWidgetMultiButton setText(Component text)
    {
        this.text = Objects.requireNonNull(text);
        return this;
    }

    public HoloWidgetMultiButton setColors(int background, int border, int textColor)
    {
        this.background = background;
        this.border = border;
        this.textColor = textColor;
        return this;
    }

    /** Vrai si un clic est maintenu sur le bouton. */
    public boolean pressed() { return pressed != 0; }

    @Override
    public boolean hovered() { return hovered; }

    @Override
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    @Override
    public boolean clickableBy(HoloControl button) { return actions.containsKey(button); }

    @Override
    public void onClick(HoloInteraction ctx, HoloControl button)
    {
        pressed |= 1 << button.mouseButton();
        Consumer<? super HoloWidgetMultiButton> action = actions.get(button);
        if (action != null) action.accept(this);
    }

    @Override
    public void onClickRelease(HoloInteraction ctx, HoloControl button) { pressed &= ~(1 << button.mouseButton()); }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        canvas.fill(0, 0, width(), height(), background);
        if (pressed() || hovered) canvas.fill(0, 0, width(), height(), pressed() ? PRESSED_OVERLAY : HOVER_OVERLAY);
        canvas.outline(0, 0, width(), height(), 1f, border);
        canvas.text(text, (width() - canvas.textWidth(text)) / 2f, (height() - canvas.lineHeight()) / 2f + 1f,
                textColor);
    }
}
