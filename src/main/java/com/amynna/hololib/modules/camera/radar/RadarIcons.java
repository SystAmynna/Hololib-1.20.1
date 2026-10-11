package com.amynna.hololib.modules.camera.radar;

import com.amynna.hololib.holo.widget.HoloCanvas;

/** Icone du radar en quadrilateres (roue Quick Action) : point central et deux ondes concentriques. */
final class RadarIcons
{
    private RadarIcons() {}

    /** Point au centre et deux anneaux, dans un carre {@code size} x {@code size} ; une couche par forme. */
    static void waves(HoloCanvas canvas, float size, int color, int segments)
    {
        float c = size / 2f;
        float thickness = Math.max(1f, 0.07f * size);
        canvas.beginLayer();
        float dot = 0.08f * size;
        canvas.fill(c - dot, c - dot, 2f * dot, 2f * dot, color);
        canvas.endLayer();
        canvas.ellipse(c, c, 0.25f * size, 0.25f * size, thickness, segments, color);
        canvas.ellipse(c, c, 0.42f * size, 0.42f * size, thickness, segments, (color & 0x00FFFFFF) | 0xA0000000);
    }
}
