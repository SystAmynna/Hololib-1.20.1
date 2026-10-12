package com.amynna.hololib.holo.theme.pattern;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloThemePattern;
import com.amynna.hololib.holo.widget.HoloCanvas;
import net.minecraft.network.chat.Component;

/**
 * Grille : fond sombre quadrille de la couleur principale. Lignes horizontales pleine largeur ; entre elles, cases
 * de fond et segments verticaux alternes : aucun recouvrement, une couche.
 */
public final class GridPattern implements HoloThemePattern
{
    private static final Component NAME = Component.literal("Grille");
    private static final HoloColor LINE = HoloColor.primary(0xFF);
    private static final float PERIOD = 12f;
    private static final float THICKNESS = 1f;
    private static final float LINE_MIX = 0.16f;

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
            float rowEnd = Math.min(height, y + PERIOD - THICKNESS);
            float x = 0f;
            while (x < width) {
                float cellEnd = Math.min(width, x + PERIOD - THICKNESS);
                canvas.fill(x, y, cellEnd - x, rowEnd - y, base);
                float lineEnd = Math.min(width, cellEnd + THICKNESS);
                canvas.fill(cellEnd, y, lineEnd - cellEnd, rowEnd - y, line);
                x = lineEnd;
            }
            float end = Math.min(height, rowEnd + THICKNESS);
            canvas.fill(0, rowEnd, width, end - rowEnd, line);
            y = end;
        }
        canvas.endLayer();
    }
}
