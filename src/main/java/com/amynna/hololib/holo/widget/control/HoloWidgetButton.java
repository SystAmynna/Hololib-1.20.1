package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.capture.HoloControl;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Bouton a une seule action ({@link #press()}), declenchee par un ou plusieurs clics ({@link #setTriggers}, clic
 * gauche par defaut). Cas particulier de {@link HoloWidgetMultiButton} ou tous les declencheurs partagent l'action.
 */
public class HoloWidgetButton extends HoloWidgetMultiButton
{
    private Consumer<? super HoloWidgetButton> onPress;

    public HoloWidgetButton(float x, float y, float width, float height, Component text,
                            Consumer<? super HoloWidgetButton> onPress)
    {
        super(x, y, width, height, text);
        this.onPress = Objects.requireNonNull(onPress);
        setTriggers(HoloControl.LEFT_CLICK);
    }

    /** Clics qui declenchent le bouton (remplacent les precedents). */
    public HoloWidgetButton setTriggers(HoloControl... buttons)
    {
        clearActions();
        for (HoloControl button : buttons) on(button, widget -> press());
        return this;
    }

    /** Un seul clic declencheur. */
    public HoloWidgetButton setTrigger(HoloControl button) { return setTriggers(button); }

    public HoloWidgetButton setOnPress(Consumer<? super HoloWidgetButton> onPress)
    {
        this.onPress = Objects.requireNonNull(onPress);
        return this;
    }

    public void press() { onPress.accept(this); }

    @Override
    public HoloWidgetButton setText(Component text)
    {
        super.setText(text);
        return this;
    }

    @Override
    public HoloWidgetButton setColors(int background, int border, int textColor)
    {
        super.setColors(background, border, textColor);
        return this;
    }

    @Override
    public HoloWidgetButton setColors(HoloColor background, HoloColor border, HoloColor textColor)
    {
        super.setColors(background, border, textColor);
        return this;
    }
}
