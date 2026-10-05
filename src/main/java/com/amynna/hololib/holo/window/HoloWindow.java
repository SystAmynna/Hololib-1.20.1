package com.amynna.hololib.holo.window;

import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.HoloWidget;

/**
 * Fenetre holographique : un {@link HoloObject} plat contenant une arborescence de widgets, a partir d'un
 * {@link HoloPanel} racine. Taille en pixels ; {@link #pixelScale()} donne la taille d'un pixel en blocs. La fenetre
 * est centree sur l'origine de l'objet.
 */
public class HoloWindow extends HoloObject
{
    private final HoloPanel root;
    private final int width;
    private final int height;
    private final float pixelScale;

    /**
     * @param transform  placement initial dans le repere de l'ancrage (copie)
     * @param width      largeur en pixels
     * @param height     hauteur en pixels
     * @param pixelScale taille d'un pixel en blocs (ex. {@code 1/160f} : 160 pixels = 1 bloc)
     */
    public HoloWindow(String key, HoloAnchor anchor, HoloTransform transform, int width, int height, float pixelScale)
    {
        super(key, anchor, transform);
        if (width <= 0 || height <= 0 || !(pixelScale > 0f)) throw new IllegalArgumentException("Taille invalide");
        this.width = width;
        this.height = height;
        this.pixelScale = pixelScale;
        this.root = new HoloPanel(0, 0, width, height);
        root.ownerWindow(this);
    }

    public final HoloPanel root() { return root; }

    public final int width() { return width; }

    public final int height() { return height; }

    public final float pixelScale() { return pixelScale; }

    /** Widget le plus en avant sous le point (pixels de la fenetre), ou {@code null} hors de la fenetre. */
    public HoloWidget widgetAt(float px, float py) { return root.widgetAt(px, py); }

    @Override
    public float boundingRadius()
    {
        return 0.5f * pixelScale * (float) Math.sqrt((double) width * width + (double) height * height);
    }

    @Override
    public void tick() { root.tick(); }
}
