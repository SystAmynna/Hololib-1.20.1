package com.amynna.hololib.holo.widget;

import com.amynna.hololib.holo.interact.HoloInput;
import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.interact.HoloInteraction;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Bouton avec un texte centre, declenche au regard par un clic ({@link #trigger()} : gauche par defaut, droit ou
 * molette) ; seul ce clic est intercepte. L'action s'execute cote client a l'appui : pour agir sur le jeu, elle envoie
 * une action au module ({@code HoloModule.sendAction}), que le serveur valide. Eclairci au survol, plus encore tant
 * que le clic est maintenu.
 */
public class HoloButton extends HoloWidget implements HoloInteractible
{
    private static final int HOVER_OVERLAY = 0x30FFFFFF;
    private static final int PRESSED_OVERLAY = 0x60FFFFFF;

    private Component text;
    private Consumer<HoloButton> onPress;
    private HoloInput trigger = HoloInput.LEFT_CLICK;
    private int background = 0x6020A0FF;
    private int border = 0xFF40C0FF;
    private int textColor = 0xFFFFFFFF;
    private boolean hovered;
    private boolean pressed;

    public HoloButton(float x, float y, float width, float height, Component text, Consumer<HoloButton> onPress)
    {
        super(x, y, width, height);
        this.text = Objects.requireNonNull(text);
        this.onPress = Objects.requireNonNull(onPress);
    }

    public Component text() { return text; }

    public HoloButton setText(Component text)
    {
        this.text = Objects.requireNonNull(text);
        return this;
    }

    public HoloButton setColors(int background, int border, int textColor)
    {
        this.background = background;
        this.border = border;
        this.textColor = textColor;
        return this;
    }

    public HoloButton setOnPress(Consumer<HoloButton> onPress)
    {
        this.onPress = Objects.requireNonNull(onPress);
        return this;
    }

    public HoloInput trigger() { return trigger; }

    /** Clic qui declenche le bouton : {@code LEFT_CLICK}, {@code RIGHT_CLICK} ou {@code MIDDLE_CLICK}. */
    public HoloButton setTrigger(HoloInput trigger)
    {
        if (trigger != HoloInput.LEFT_CLICK && trigger != HoloInput.RIGHT_CLICK && trigger != HoloInput.MIDDLE_CLICK) {
            throw new IllegalArgumentException("Declencheur de bouton invalide : " + trigger);
        }
        this.trigger = trigger;
        return this;
    }

    public boolean hovered() { return hovered; }

    public boolean pressed() { return pressed; }

    public void press() { onPress.accept(this); }

    @Override
    public boolean intercepts(HoloInput input) { return input == trigger; }

    @Override
    public void onHover(HoloInteraction ctx) { hovered = true; }

    @Override
    public void onHoverRelease(HoloInteraction ctx) { hovered = false; }

    @Override
    public void onLeftClick(HoloInteraction ctx) { pressStart(HoloInput.LEFT_CLICK); }

    @Override
    public void onLeftClickRelease(HoloInteraction ctx) { pressEnd(HoloInput.LEFT_CLICK); }

    @Override
    public void onRightClick(HoloInteraction ctx) { pressStart(HoloInput.RIGHT_CLICK); }

    @Override
    public void onRightClickRelease(HoloInteraction ctx) { pressEnd(HoloInput.RIGHT_CLICK); }

    @Override
    public void onMiddleClick(HoloInteraction ctx) { pressStart(HoloInput.MIDDLE_CLICK); }

    @Override
    public void onMiddleClickRelease(HoloInteraction ctx) { pressEnd(HoloInput.MIDDLE_CLICK); }

    private void pressStart(HoloInput input)
    {
        if (input != trigger) return;
        pressed = true;
        press();
    }

    private void pressEnd(HoloInput input)
    {
        if (input == trigger) pressed = false;
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        canvas.fill(0, 0, width(), height(), background);
        if (pressed || hovered) canvas.fill(0, 0, width(), height(), pressed ? PRESSED_OVERLAY : HOVER_OVERLAY);
        canvas.outline(0, 0, width(), height(), 1f, border);
        canvas.text(text, (width() - canvas.textWidth(text)) / 2f, (height() - canvas.lineHeight()) / 2f + 1f,
                textColor);
    }
}
