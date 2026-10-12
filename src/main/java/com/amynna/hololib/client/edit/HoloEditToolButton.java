package com.amynna.hololib.client.edit;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.control.HoloWidgetMultiButton;
import net.minecraft.network.chat.Component;

/**
 * Bouton carre d'un outil de transformation du panneau d'edition, avec son icone dessinee en quadrilateres :
 * <ul>
 *     <li>{@code TRANSLATE} : croix, une pointe de fleche a chaque bout ;</li>
 *     <li>{@code ROTATE} : cercle, deux ovales dedans (un large, un haut) ;</li>
 *     <li>{@code SCALE} : carre en bas a gauche, double fleche diagonale en haut a droite.</li>
 * </ul>
 * Fond, survol et bordure : ceux de {@link HoloWidgetMultiButton} ; l'icone est dessinee dans une seule couche
 * ({@code HoloCanvas.beginLayer}), a plat.
 */
final class HoloEditToolButton extends HoloWidgetMultiButton
{
    private static final HoloColor ICON = HoloColor.special(0xFF);
    private static final float STROKE = 1.5f;
    private static final float SQRT_HALF = (float) Math.sqrt(0.5);

    private final HoloEditTool tool;

    HoloEditToolButton(float x, float y, float size, HoloEditTool tool)
    {
        super(x, y, size, size, Component.empty());
        this.tool = tool;
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        super.draw(canvas);
        float s = Math.min(width(), height());
        float c = s / 2f;
        canvas.beginLayer();
        try {
            switch (tool) {
                case TRANSLATE -> translate(canvas, s, c);
                case ROTATE -> rotate(canvas, s, c);
                case SCALE -> scale(canvas, s, c);
                case RANGE -> {}
            }
        } finally {
            canvas.endLayer();
        }
    }

    private static void translate(HoloCanvas canvas, float s, float c)
    {
        float r = 0.36f * s;
        float head = 0.14f * s;
        canvas.line(c - r + head, c, c + r - head, c, STROKE, canvas.color(ICON));
        canvas.line(c, c - r + head, c, c + r - head, STROKE, canvas.color(ICON));
        arrowHead(canvas, c + r, c, 1f, 0f, head);
        arrowHead(canvas, c - r, c, -1f, 0f, head);
        arrowHead(canvas, c, c - r, 0f, -1f, head);
        arrowHead(canvas, c, c + r, 0f, 1f, head);
    }

    private static void rotate(HoloCanvas canvas, float s, float c)
    {
        float r = 0.38f * s;
        canvas.ellipse(c, c, r, r, STROKE, 24, canvas.color(ICON));
        float inner = r - STROKE - 1f;
        canvas.ellipse(c, c, inner, 0.38f * inner, 1.2f, 20, canvas.color(ICON));
        canvas.ellipse(c, c, 0.38f * inner, inner, 1.2f, 20, canvas.color(ICON));
    }

    private static void scale(HoloCanvas canvas, float s, float c)
    {
        float r = 0.38f * s;
        // Carre en bas a gauche.
        float side = 0.42f * s;
        canvas.outline(c - r, c + r - side, side, side, STROKE, canvas.color(ICON));
        // Double fleche du coin du carre vers le coin haut droit.
        float head = 0.13f * s;
        float x0 = c - r + side + 0.06f * s;
        float y0 = c + r - side - 0.06f * s;
        float x1 = c + r;
        float y1 = c - r;
        float inset = head * SQRT_HALF;
        canvas.line(x0 + inset, y0 - inset, x1 - inset, y1 + inset, STROKE, canvas.color(ICON));
        arrowHead(canvas, x1, y1, SQRT_HALF, -SQRT_HALF, head);
        arrowHead(canvas, x0, y0, -SQRT_HALF, SQRT_HALF, head);
    }

    /** Pointe de fleche pleine, sommet en {@code (tipX, tipY)}, orientee selon la direction unitaire (dx, dy). */
    private static void arrowHead(HoloCanvas canvas, float tipX, float tipY, float dx, float dy, float size)
    {
        float bx = tipX - dx * size;
        float by = tipY - dy * size;
        float nx = -dy * size;
        float ny = dx * size;
        canvas.quad(tipX, tipY, bx + nx, by + ny, bx - nx, by - ny, bx - nx, by - ny, canvas.color(ICON));
    }
}
