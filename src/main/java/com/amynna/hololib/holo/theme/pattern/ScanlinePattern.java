package com.amynna.hololib.holo.theme.pattern;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloThemePattern;
import com.amynna.hololib.holo.widget.HoloCanvas;
import net.minecraft.network.chat.Component;

/**
 * Lignes TV : fond sombre et fines lignes horizontales de la couleur principale, comme un ecran cathodique. Bandes de
 * fond et lignes alternees, sans recouvrement, dans une couche.
 */
public final class ScanlinePattern implements HoloThemePattern
{
    private static final Component NAME = Component.literal("Lignes TV");
    private static final HoloColor LINE = HoloColor.primary(0xFF);
    private static final float PERIOD = 3f;
    private static final float THICKNESS = 1f;
    private static final float LINE_MIX = 0.22f;

    @Override
    public Component name() { return NAME; }

    @Override
    public void draw(HoloCanvas canvas, float width, float height, HoloTheme theme, int alpha)
    {
        int base = BASE.resolve(theme, alpha);
        int line = HoloThemePattern.mix(base, LINE.rgb(theme), LINE_MIX);
        canvas.beginLayer();
        float y = 0f;
        while (y < height) {
            float lineY = Math.min(height, y + PERIOD - THICKNESS);
            canvas.fill(0, y, width, lineY - y, base);
            float end = Math.min(height, lineY + THICKNESS);
            canvas.fill(0, lineY, width, end - lineY, line);
            y = end;
        }
        canvas.endLayer();
    }
}
