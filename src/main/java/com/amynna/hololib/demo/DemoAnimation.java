package com.amynna.hololib.demo;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import net.minecraft.util.Mth;

/** Petite animation : une vague de barres, avancee au tick et interpolee au dessin. Non interactive. */
final class DemoAnimation extends HoloWidget
{
    private static final HoloColor BACKGROUND = HoloColor.secondary(0x40).darker(0.7f);
    private static final HoloColor BORDER = HoloColor.primary(0xFF).lighter(0.4f);
    private static final HoloColor BAR_LOW = HoloColor.secondary(0xE0);
    private static final HoloColor BAR_HIGH = HoloColor.primary(0xE0).lighter(0.4f);

    private static final int BARS = 9;
    private static final float GAP = 3f;

    private int age;

    DemoAnimation(float x, float y, float width, float height)
    {
        super(x, y, width, height);
    }

    @Override
    public void tick() { age++; }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        canvas.fill(0, 0, width(), height(), canvas.color(BACKGROUND));
        canvas.outline(0, 0, width(), height(), 1f, canvas.color(BORDER));
        int low = canvas.color(BAR_LOW);
        int high = canvas.color(BAR_HIGH);
        float time = age + canvas.partialTick();
        float barWidth = (width() - 4f - GAP * (BARS - 1)) / BARS;
        float maxHeight = height() - 8f;
        for (int i = 0; i < BARS; i++) {
            float wave = 0.5f + 0.5f * Mth.sin(time * 0.25f - i * 0.7f);
            float barHeight = 3f + (maxHeight - 3f) * wave;
            canvas.fill(2f + i * (barWidth + GAP), height() - 4f - barHeight, barWidth, barHeight,
                    HoloColor.lerp(low, high, wave));
        }
    }
}
