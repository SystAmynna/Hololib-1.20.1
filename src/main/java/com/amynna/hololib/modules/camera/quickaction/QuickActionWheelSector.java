package com.amynna.hololib.modules.camera.quickaction;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import com.amynna.hololib.holo.widget.control.HoloClickable;
import com.amynna.hololib.holo.widget.control.HoloHoverable;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * Secteur de la roue : une couronne entre la zone morte et le bord, sur l'angle de son action (0 = haut, sens des
 * aiguilles d'une montre). Bornes = carre de la roue ; seul le secteur est vise ({@link #contains}). Surligne au
 * survol, icone au milieu ; un clic gauche ou droit choisit l'action. Il ne se dessine pas lui-meme : la roue
 * ({@code QuickActionWheelWindow}) dessine tous les secteurs d'un coup, dans une seule couche
 * ({@link #drawShape}, puis {@link #drawIcon}).
 */
final class QuickActionWheelSector extends HoloWidget implements HoloClickable, HoloHoverable
{
    private static final float SEGMENT = Mth.TWO_PI / 64f;
    /** Ecart angulaire entre deux secteurs, en radians. */
    private static final float GAP = 0.04f;
    private static final float RIM = 1.2f;
    private static final float MAX_ICON = 28f;
    private static final HoloColor FILL = HoloColor.secondary(0xB0).darker(0.84f);
    private static final HoloColor FILL_HOVER = HoloColor.secondary(0xD0).darker(0.37f);
    private static final HoloColor RIM_COLOR = HoloColor.primary(0xFF);
    private static final HoloColor RIM_HOVER = HoloColor.primary(0xFF).lighter(0.51f);

    private final QuickAction action;
    private final Consumer<QuickAction> onSelect;
    private float center;
    private float inner;
    private float outer;
    private float start;
    private float span;
    private boolean hovered;

    QuickActionWheelSector(float size, QuickAction action, Consumer<QuickAction> onSelect)
    {
        super(0, 0, size, size);
        this.action = action;
        this.onSelect = onSelect;
        this.center = size / 2f;
    }

    QuickAction action() { return action; }

    /** Rayons (pixels) et angles (radians) du secteur ; {@code span >= 2 pi} : couronne entiere. */
    void setGeometry(float inner, float outer, float start, float span)
    {
        this.inner = inner;
        this.outer = outer;
        this.start = start;
        this.span = span;
    }

    private boolean fullRing() { return span >= Mth.TWO_PI - 1e-4f; }

    @Override
    public boolean contains(float px, float py)
    {
        float dx = px - x() - center;
        float dy = py - y() - center;
        float d2 = dx * dx + dy * dy;
        if (d2 < inner * inner || d2 > outer * outer) return false;
        if (fullRing()) return true;
        float angle = (float) Math.atan2(dx, -dy) - start;
        angle -= Mth.TWO_PI * (float) Math.floor(angle / Mth.TWO_PI);
        return angle < span;
    }

    @Override
    public boolean clickableBy(HoloControl button)
    {
        return button == HoloControl.LEFT_CLICK || button == HoloControl.RIGHT_CLICK;
    }

    @Override
    public void onClick(HoloInteraction ctx, HoloControl button) { onSelect.accept(action); }

    @Override
    public boolean hovered() { return hovered; }

    @Override
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    /** Dessine par la roue ({@link #drawShape}, {@link #drawIcon}). */
    @Override
    protected void draw(HoloCanvas canvas) {}

    /** Couronne (fond et bord) du secteur, dans la couche ouverte par la roue ; repere de la roue. */
    void drawShape(HoloCanvas canvas)
    {
        float gap = fullRing() ? 0f : GAP;
        float a0 = start + gap / 2f;
        float a1 = start + span - gap / 2f;
        int steps = Math.max(1, (int) Math.ceil((a1 - a0) / SEGMENT));
        int fill = canvas.color(hovered ? FILL_HOVER : FILL);
        int rim = canvas.color(hovered ? RIM_HOVER : RIM_COLOR);
        float rimInner = outer - RIM;
        for (int i = 0; i < steps; i++) {
            float b0 = a0 + (a1 - a0) * i / steps;
            float b1 = a0 + (a1 - a0) * (i + 1) / steps;
            float s0 = Mth.sin(b0);
            float c0 = Mth.cos(b0);
            float s1 = Mth.sin(b1);
            float c1 = Mth.cos(b1);
            canvas.quad(center + inner * s0, center - inner * c0, center + rimInner * s0, center - rimInner * c0,
                    center + rimInner * s1, center - rimInner * c1, center + inner * s1, center - inner * c1, fill);
            canvas.quad(center + rimInner * s0, center - rimInner * c0, center + outer * s0, center - outer * c0,
                    center + outer * s1, center - outer * c1, center + rimInner * s1, center - rimInner * c1, rim);
        }
    }

    /** Icone de l'action au milieu de la couronne, devant les couronnes ; repere de la roue. */
    void drawIcon(HoloCanvas canvas)
    {
        // Icone au milieu de la couronne, bornee par son epaisseur et par la longueur de l'arc.
        float mid = (inner + outer) / 2f;
        float size = Math.min(MAX_ICON, 0.6f * Math.min(outer - inner, fullRing() ? outer - inner : mid * span));
        if (size < 2f) return;
        float angle = fullRing() ? 0f : start + span / 2f;
        canvas.push(center + mid * Mth.sin(angle) - size / 2f, center - mid * Mth.cos(angle) - size / 2f);
        try {
            action.drawIcon(canvas, size);
        } finally {
            canvas.pop();
        }
    }
}
