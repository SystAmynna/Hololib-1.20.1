package com.amynna.hololib.holo.theme.pattern;

import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloThemePattern;
import com.amynna.hololib.holo.widget.HoloCanvas;
import net.minecraft.network.chat.Component;

/** Uni : le fond sombre seul. */
public final class SolidPattern implements HoloThemePattern
{
    private static final Component NAME = Component.literal("Uni");

    @Override
    public Component name() { return NAME; }

    @Override
    public void draw(HoloCanvas canvas, float width, float height, HoloTheme theme, int alpha)
    {
        canvas.fill(0, 0, width, height, BASE.resolve(theme, alpha));
    }
}
