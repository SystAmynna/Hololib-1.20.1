package com.amynna.hololib.holo.theme.pattern;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloThemePattern;
import com.amynna.hololib.holo.widget.HoloCanvas;
import net.minecraft.network.chat.Component;

/**
 * Bandes diagonales : fond sombre traverse de bandes a 45 degres de la couleur secondaire, sur toute la surface. Bandes
 * de fond et bandes colorees alternent sans se recouvrir : chacune est decoupee au rectangle (polygone convexe d'au
 * plus 6 sommets, dessine en eventail de quadrilateres), le tout dans une couche. Tampons reutilises : fil de rendu
 * seulement.
 */
public final class DiagonalPattern implements HoloThemePattern
{
    private static final Component NAME = Component.literal("Bandes diagonales");
    private static final HoloColor STRIPE = HoloColor.secondary(0xFF);
    private static final float PERIOD = 14f;
    private static final float WIDTH = 6f;
    private static final float STRIPE_MIX = 0.35f;

    private final float[] polygon = new float[16];
    private final float[] clipped = new float[16];

    @Override
    public Component name() { return NAME; }

    @Override
    public void draw(HoloCanvas canvas, float width, float height, HoloTheme theme, int alpha)
    {
        int base = BASE.resolve(theme, alpha);
        int stripe = HoloThemePattern.mix(base, STRIPE.rgb(theme), STRIPE_MIX);
        canvas.beginLayer();
        // Bandes c0 <= x + y <= c1 ; x + y va de 0 a width + height.
        for (float c0 = -PERIOD; c0 < width + height; c0 += PERIOD) {
            band(canvas, width, height, c0, c0 + WIDTH, stripe);
            band(canvas, width, height, c0 + WIDTH, c0 + PERIOD, base);
        }
        canvas.endLayer();
    }

    /** Bande {@code c0 <= x + y <= c1} decoupee au rectangle. */
    private void band(HoloCanvas canvas, float width, float height, float c0, float c1, int argb)
    {
        int count = rectangle(width, height);
        count = clip(count, c0, 1f);
        count = clip(count, -c1, -1f);
        fan(canvas, count, argb);
    }

    private int rectangle(float width, float height)
    {
        polygon[0] = 0f;
        polygon[1] = 0f;
        polygon[2] = width;
        polygon[3] = 0f;
        polygon[4] = width;
        polygon[5] = height;
        polygon[6] = 0f;
        polygon[7] = height;
        return 4;
    }

    /** Garde la partie ou {@code sign * (x + y) >= limit} (Sutherland-Hodgman), resultat dans {@link #polygon}. */
    private int clip(int count, float limit, float sign)
    {
        int out = 0;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            float xi = polygon[2 * i];
            float yi = polygon[2 * i + 1];
            float xj = polygon[2 * j];
            float yj = polygon[2 * j + 1];
            float di = sign * (xi + yi) - limit;
            float dj = sign * (xj + yj) - limit;
            if (di >= 0f) {
                clipped[2 * out] = xi;
                clipped[2 * out + 1] = yi;
                out++;
            }
            if (di >= 0f != dj >= 0f) {
                float t = di / (di - dj);
                clipped[2 * out] = xi + (xj - xi) * t;
                clipped[2 * out + 1] = yi + (yj - yi) * t;
                out++;
            }
        }
        System.arraycopy(clipped, 0, polygon, 0, 2 * out);
        return out;
    }

    /** Polygone convexe en eventail : quadrilateres {@code (v0, vi, vi+1, vi+2)}, un triangle final degenere. */
    private void fan(HoloCanvas canvas, int count, int argb)
    {
        if (count < 3) return;
        for (int i = 1; i + 1 < count; i += 2) {
            int k = Math.min(i + 2, count - 1);
            canvas.quad(polygon[0], polygon[1], polygon[2 * i], polygon[2 * i + 1], polygon[2 * (i + 1)],
                    polygon[2 * (i + 1) + 1], polygon[2 * k], polygon[2 * k + 1], argb);
        }
    }
}
