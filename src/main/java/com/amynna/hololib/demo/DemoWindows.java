package com.amynna.hololib.demo;

import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraft.network.chat.Component;

/** Construction des fenetres de demonstration : un titre et une ligne de texte sur un fond translucide. */
final class DemoWindows
{
    private static final int WIDTH = 160;
    private static final int HEIGHT = 48;
    private static final int BACKGROUND = 0x8010304A;

    private DemoWindows() {}

    /**
     * @param transform  placement initial (copie)
     * @param pixelScale taille d'un pixel en blocs
     * @param accent     couleur de la bordure et du titre
     */
    static HoloWindow labeled(String key, HoloAnchor anchor, HoloTransform transform, float pixelScale, String title,
                              String text, int accent)
    {
        HoloWindow window = new HoloWindow(key, anchor, transform, WIDTH, HEIGHT, pixelScale);
        window.root().setBackground(BACKGROUND).setBorder(accent, 1f);
        window.root().add(new HoloLabel(4, 6, WIDTH - 8, 14, Component.literal(title))
                .setAlign(HoloLabel.Align.CENTER).setColor(accent));
        window.root().add(new HoloLabel(4, 26, WIDTH - 8, 14, Component.literal(text))
                .setAlign(HoloLabel.Align.CENTER));
        return window;
    }
}
