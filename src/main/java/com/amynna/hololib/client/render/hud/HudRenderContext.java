package com.amynna.hololib.client.render.hud;

import net.minecraft.client.gui.GuiGraphics;

/** Donnees de la frame pour un {@link HudRenderer}. Objet reutilise : ne pas le garder au-dela de l'appel. */
public final class HudRenderContext
{
    private GuiGraphics graphics;
    private float partialTick;
    private int width;
    private int height;
    private double guiScale;

    HudRenderContext() {}

    void set(GuiGraphics graphics, float partialTick, int width, int height, double guiScale)
    {
        this.graphics = graphics;
        this.partialTick = partialTick;
        this.width = width;
        this.height = height;
        this.guiScale = guiScale;
    }

    /** Pour les dessins vanilla du GUI (blit, texte) ; ses buffers sont ceux passes au renderer. */
    public GuiGraphics graphics() { return graphics; }

    public float partialTick() { return partialTick; }

    /** Largeur de l'ecran, en pixels GUI. */
    public int width() { return width; }

    /** Hauteur de l'ecran, en pixels GUI. */
    public int height() { return height; }

    /** Pixels de la fenetre par pixel GUI. */
    public double guiScale() { return guiScale; }
}
