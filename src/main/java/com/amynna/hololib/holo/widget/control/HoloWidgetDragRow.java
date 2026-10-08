package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloPanel;
import net.minecraft.util.Mth;

import java.util.Objects;

/**
 * Ligne d'une {@link HoloWidgetList} qu'on reordonne au regard ({@link HoloDraggable}) : clic gauche maintenu, elle
 * suit le regard verticalement (bornee a la liste) et passe devant les autres ; au lacher, elle prend la place de la
 * ligne visee ({@link HoloWidgetList#moveItem}) et l'ecouteur ({@link #setOnMove}) recoit les indices, a envoyer au
 * serveur. Panneau : peut contenir des widgets (interrupteur...), qui gardent leurs clics.
 */
public class HoloWidgetDragRow extends HoloPanel implements HoloDraggable, HoloHoverable
{
    /** Ecouteur d'un deplacement effectif ({@code from != to}). */
    @FunctionalInterface
    public interface OnMove
    {
        void moved(HoloWidgetDragRow row, int from, int to);
    }

    private final HoloDragState dragState = new HoloDragState();
    private OnMove onMove = (row, from, to) -> {};
    private boolean hovered;
    private int color = 0x6020A0FF;

    public HoloWidgetDragRow(float x, float y, float width, float height)
    {
        super(x, y, width, height);
    }

    /** Couleur de la ligne, sous le surlignage et les enfants. */
    public HoloWidgetDragRow setColor(int argb)
    {
        this.color = argb;
        return this;
    }

    public HoloWidgetDragRow setOnMove(OnMove onMove)
    {
        this.onMove = Objects.requireNonNull(onMove);
        return this;
    }

    @Override
    public HoloDragState dragState() { return dragState; }

    @Override
    public void onClick(HoloInteraction ctx, HoloControl button)
    {
        HoloDraggable.super.onClick(ctx, button);
        // Dernier enfant : dessine devant les autres lignes et vise en premier pendant le glisser.
        HoloPanel parent = parent();
        if (parent != null) parent.add(this);
    }

    @Override
    public void onDragMove(HoloInteraction ctx, float dx, float dy)
    {
        HoloPanel parent = parent();
        float maxY = parent != null ? parent.height() - height() : y() + dy;
        setBounds(x(), Mth.clamp(y() + dy, 0f, Math.max(0f, maxY)), width(), height());
    }

    @Override
    public void onDrop(HoloInteraction ctx)
    {
        if (!(parent() instanceof HoloWidgetList list)) return;
        int from = list.items().indexOf(this);
        int to = list.indexAt(y() + height() / 2f);
        if (from < 0 || to < 0) return;
        list.moveItem(from, to);
        if (from != to) onMove.moved(this, from, to);
    }

    @Override
    public boolean hovered() { return hovered; }

    @Override
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        canvas.fill(0, 0, width(), height(), color);
        if (hovered || dragging()) canvas.fill(0, 0, width(), height(), dragging() ? 0x40FFFFFF : 0x20FFFFFF);
        super.draw(canvas);
    }
}
