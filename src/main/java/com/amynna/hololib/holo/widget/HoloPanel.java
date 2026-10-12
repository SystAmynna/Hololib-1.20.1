package com.amynna.hololib.holo.widget;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.window.HoloWindow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Conteneur de widgets, avec fond (couleur, ou motif du theme) et bordure optionnels. Les enfants sont dessines dans
 * l'ordre d'ajout (le dernier au premier plan). La racine d'une {@link HoloWindow} est un panneau.
 */
public class HoloPanel extends HoloWidget
{
    private final List<HoloWidget> children = new ArrayList<>();
    private final List<HoloWidget> childrenView = Collections.unmodifiableList(children);
    private HoloWindow ownerWindow;
    private HoloColor background = HoloColor.NONE;
    private HoloColor border = HoloColor.NONE;
    private float borderThickness = 1f;
    /** Alpha du motif du theme en fond, ou -1 : fond de couleur. */
    private int patternAlpha = -1;

    public HoloPanel(float x, float y, float width, float height)
    {
        super(x, y, width, height);
    }

    /** Ajoute un enfant (retire de son ancien parent) ; renvoie l'enfant. */
    public <W extends HoloWidget> W add(W child)
    {
        if (child == this) throw new IllegalArgumentException("Un panneau ne peut pas se contenir");
        if (child.parent() != null) child.parent().remove(child);
        children.add(child);
        child.parent(this);
        return child;
    }

    public void remove(HoloWidget child)
    {
        if (children.remove(child)) child.parent(null);
    }

    public List<HoloWidget> children() { return childrenView; }

    /** Couleur de fond fixe {@code 0xAARRGGBB} ; 0 = aucun fond. */
    public HoloPanel setBackground(int argb) { return setBackground(HoloColor.fixed(argb)); }

    /** Couleur de fond ({@link HoloColor#NONE} = aucun) ; remplace le motif. */
    public HoloPanel setBackground(HoloColor color)
    {
        this.background = Objects.requireNonNull(color);
        this.patternAlpha = -1;
        return this;
    }

    /** Fond = motif du theme ({@code HoloThemePattern}), d'opacite {@code alpha} ({@code 0..255}). */
    public HoloPanel setPattern(int alpha)
    {
        this.patternAlpha = alpha & 0xFF;
        this.background = HoloColor.NONE;
        return this;
    }

    /** Couleur de bordure fixe {@code 0xAARRGGBB} ; 0 = aucune bordure. */
    public HoloPanel setBorder(int argb, float thickness) { return setBorder(HoloColor.fixed(argb), thickness); }

    /** Couleur de bordure ({@link HoloColor#NONE} = aucune). */
    public HoloPanel setBorder(HoloColor color, float thickness)
    {
        this.border = Objects.requireNonNull(color);
        this.borderThickness = thickness;
        return this;
    }

    /** Fenetre dont ce panneau est la racine, sinon {@code null}. */
    public final HoloWindow ownerWindow() { return ownerWindow; }

    /** Usage interne : appele par {@link HoloWindow} pour sa racine. */
    public final void ownerWindow(HoloWindow window) { this.ownerWindow = window; }

    /**
     * Widget le plus en avant sous le point (repere de ce panneau), en descendant dans les sous-panneaux ;
     * {@code this} si aucun enfant, {@code null} hors du panneau.
     */
    public HoloWidget widgetAt(float px, float py)
    {
        if (!visible() || px < 0 || py < 0 || px >= width() || py >= height()) return null;
        for (int i = children.size() - 1; i >= 0; i--) {
            HoloWidget child = children.get(i);
            if (!child.visible() || !child.contains(px, py)) continue;
            if (child instanceof HoloPanel panel) {
                HoloWidget hit = panel.widgetAt(px - panel.x(), py - panel.y());
                if (hit != null) return hit;
            } else {
                return child;
            }
        }
        return this;
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        if (patternAlpha >= 0) {
            canvas.theme().pattern().draw(canvas, width(), height(), canvas.theme(), patternAlpha);
        } else if (!background.none()) {
            canvas.fill(0, 0, width(), height(), canvas.color(background));
        }
        if (!border.none()) canvas.outline(0, 0, width(), height(), borderThickness, canvas.color(border));
        for (int i = 0; i < children.size(); i++) children.get(i).render(canvas);
    }

    @Override
    public void tick()
    {
        for (int i = 0; i < children.size(); i++) children.get(i).tick();
    }
}
