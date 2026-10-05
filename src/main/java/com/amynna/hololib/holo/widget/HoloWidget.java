package com.amynna.hololib.holo.widget;

import com.amynna.hololib.holo.window.HoloWindow;

/**
 * Composant d'une {@link HoloWindow}, dans l'espace 2D de la fenetre (pixels, {@code y} vers le bas). Position
 * relative au parent. Code commun : le dessin passe par {@link HoloCanvas}, l'etat ne transite jamais sur le reseau
 * (il decoule de l'etat du module). Un widget interactif implemente
 * {@link com.amynna.hololib.holo.interact.HoloInteractible}.
 */
public abstract class HoloWidget
{
    private HoloPanel parent;
    private float x;
    private float y;
    private float width;
    private float height;
    private boolean visible = true;

    protected HoloWidget(float x, float y, float width, float height)
    {
        setBounds(x, y, width, height);
    }

    public HoloWidget setBounds(float x, float y, float width, float height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    public final float x() { return x; }

    public final float y() { return y; }

    public final float width() { return width; }

    public final float height() { return height; }

    public final boolean visible() { return visible; }

    public HoloWidget setVisible(boolean visible)
    {
        this.visible = visible;
        return this;
    }

    public final HoloPanel parent() { return parent; }

    final void parent(HoloPanel parent) { this.parent = parent; }

    /** Fenetre contenant ce widget, ou {@code null} s'il n'est pas rattache. */
    public final HoloWindow window()
    {
        HoloWidget widget = this;
        while (widget.parent != null) widget = widget.parent;
        return widget instanceof HoloPanel panel ? panel.ownerWindow() : null;
    }

    /** Abscisse du coin haut gauche dans le repere de la fenetre (somme des positions des parents). */
    public final float absoluteX()
    {
        float ax = 0f;
        for (HoloWidget widget = this; widget != null; widget = widget.parent) ax += widget.x;
        return ax;
    }

    /** Ordonnee du coin haut gauche dans le repere de la fenetre. */
    public final float absoluteY()
    {
        float ay = 0f;
        for (HoloWidget widget = this; widget != null; widget = widget.parent) ay += widget.y;
        return ay;
    }

    /** Vrai si le point, exprime dans le repere du parent, est dans le widget. */
    public boolean contains(float px, float py)
    {
        return px >= x && py >= y && px < x + width && py < y + height;
    }

    /** Dessine le widget s'il est visible, l'origine placee sur son coin haut gauche. */
    public final void render(HoloCanvas canvas)
    {
        if (!visible) return;
        canvas.push(x, y);
        try {
            draw(canvas);
        } finally {
            canvas.pop();
        }
    }

    /** Dessin dans le repere du widget : {@code (0, 0)} en haut a gauche, {@code (width, height)} en bas a droite. */
    protected abstract void draw(HoloCanvas canvas);

    /** Appele chaque tick client (animations d'etat). */
    public void tick() {}
}
