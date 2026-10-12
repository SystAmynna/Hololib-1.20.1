package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.theme.HoloColor;
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
    protected static final HoloColor HOVER_OVERLAY = HoloColor.special(0x30);
    protected static final HoloColor PRESSED_OVERLAY = HoloColor.special(0x60);

    private final Map<HoloControl, Consumer<? super HoloWidgetMultiButton>> actions = new LinkedHashMap<>();
    private Component text;
    private HoloColor background = HoloColor.secondary(0x60);
    private HoloColor border = HoloColor.primary(0xFF);
    private HoloColor textColor = HoloColor.special(0xFF);
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

    /** Couleurs fixes {@code 0xAARRGGBB}. */
    public HoloWidgetMultiButton setColors(int background, int border, int textColor)
    {
        return setColors(HoloColor.fixed(background), HoloColor.fixed(border), HoloColor.fixed(textColor));
    }

    /** Fond, bordure et texte (par defaut : secondaire, principale, speciale). */
    public HoloWidgetMultiButton setColors(HoloColor background, HoloColor border, HoloColor textColor)
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
        canvas.fill(0, 0, width(), height(), canvas.color(background));
        if (pressed() || hovered) {
            canvas.fill(0, 0, width(), height(), canvas.color(pressed() ? PRESSED_OVERLAY : HOVER_OVERLAY));
        }
        canvas.outline(0, 0, width(), height(), 1f, canvas.color(border));
        canvas.text(text, (width() - canvas.textWidth(text)) / 2f, (height() - canvas.lineHeight()) / 2f + 1f,
                canvas.color(textColor));
    }
}
