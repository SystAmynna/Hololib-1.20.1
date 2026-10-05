package com.amynna.hololib.holo.widget;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Bouton avec un texte centre. L'action s'execute cote client : pour agir sur le jeu, elle envoie une action au module
 * ({@code HoloModule.sendAction}), que le serveur valide.
 */
public class HoloButton extends HoloWidget
{
    private Component text;
    private Consumer<HoloButton> onPress;
    private int background = 0x6020A0FF;
    private int border = 0xFF40C0FF;
    private int textColor = 0xFFFFFFFF;

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

    public void press() { onPress.accept(this); }

    @Override
    public boolean onClick(float localX, float localY)
    {
        press();
        return true;
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        canvas.fill(0, 0, width(), height(), background);
        canvas.outline(0, 0, width(), height(), 1f, border);
        canvas.text(text, (width() - canvas.textWidth(text)) / 2f, (height() - canvas.lineHeight()) / 2f + 1f,
                textColor);
    }
}
