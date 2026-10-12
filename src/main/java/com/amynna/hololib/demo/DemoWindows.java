package com.amynna.hololib.demo;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.HoloLabel;
import net.minecraft.network.chat.Component;

/**
 * Construction des fenetres de demonstration : un titre et une ligne de texte sur un fond translucide. Les fenetres
 * sont editables en jeu ({@link DemoEditableWindow}).
 */
final class DemoWindows
{
    private static final int WIDTH = 160;
    private static final int HEIGHT = 48;

    private DemoWindows() {}

    /**
     * @param transform  placement initial (copie)
     * @param pixelScale taille d'un pixel en blocs
     * @param accent     couleur de la bordure et du titre
     */
    static DemoEditableWindow labeled(String key, HoloAnchor anchor, HoloTransform transform, float pixelScale,
                                      String title, String text, HoloColor accent)
    {
        return fill(new DemoEditableWindow(key, anchor, transform, WIDTH, HEIGHT, pixelScale), title, text, accent);
    }

    /** Fenetre replacee a chaque ouverture du focus ({@link DemoRelativeWindow}), ancree {@code Local} fixe. */
    static DemoRelativeWindow relative(String key, HoloTransform transform, float pixelScale, String title,
                                       String text, HoloColor accent)
    {
        return fill(new DemoRelativeWindow(key, transform, WIDTH, HEIGHT, pixelScale), title, text, accent);
    }

    private static <W extends DemoEditableWindow> W fill(W window, String title, String text, HoloColor accent)
    {
        window.root().setPattern(0x80).setBorder(accent, 1f);
        window.root().add(new HoloLabel(4, 6, WIDTH - 8, 14, Component.literal(title))
                .setAlign(HoloLabel.Align.CENTER).setColor(accent));
        window.root().add(new HoloLabel(4, 26, WIDTH - 8, 14, Component.literal(text))
                .setAlign(HoloLabel.Align.CENTER));
        return window;
    }
}
