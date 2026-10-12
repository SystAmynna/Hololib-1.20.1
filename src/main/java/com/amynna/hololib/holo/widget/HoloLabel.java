package com.amynna.hololib.holo.widget;

import com.amynna.hololib.holo.theme.HoloColor;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Texte sur une ligne, centre verticalement dans le widget. */
public class HoloLabel extends HoloWidget
{
    public enum Align
    {
        LEFT, CENTER, RIGHT
    }

    private Component text;
    private HoloColor color = HoloColor.special(0xFF);
    private Align align = Align.LEFT;

    public HoloLabel(float x, float y, float width, float height, Component text)
    {
        super(x, y, width, height);
        this.text = Objects.requireNonNull(text);
    }

    public Component text() { return text; }

    public HoloLabel setText(Component text)
    {
        this.text = Objects.requireNonNull(text);
        return this;
    }

    /** Couleur fixe {@code 0xAARRGGBB}. */
    public HoloLabel setColor(int argb) { return setColor(HoloColor.fixed(argb)); }

    /** Couleur du texte (role SPECIAL par defaut). */
    public HoloLabel setColor(HoloColor color)
    {
        this.color = Objects.requireNonNull(color);
        return this;
    }

    public HoloLabel setAlign(Align align)
    {
        this.align = Objects.requireNonNull(align);
        return this;
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        float tx = switch (align) {
            case LEFT -> 0f;
            case CENTER -> (width() - canvas.textWidth(text)) / 2f;
            case RIGHT -> width() - canvas.textWidth(text);
        };
        canvas.text(text, tx, (height() - canvas.lineHeight()) / 2f + 1f, canvas.color(color));
    }
}
