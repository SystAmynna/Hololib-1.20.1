package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.HoloWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Pastille deplacable au regard ({@link HoloDraggable}) : clic gauche maintenu, elle suit le regard, bornee a son
 * panneau parent ; au lacher, l'ecouteur ({@link #setOnDrop}) recoit sa position (a envoyer au serveur). La position
 * venue du module ({@link #setPosition}) est ignoree pendant un glisser.
 */
public class HoloWidgetDraggable extends HoloWidget implements HoloDraggable, HoloHoverable
{
    private final HoloDragState dragState = new HoloDragState();
    private Consumer<? super HoloWidgetDraggable> onDrop = widget -> {};
    private Component text;
    private boolean hovered;
    private int color = 0xC0A060FF;
    private int border = 0xFFD0A0FF;

    public HoloWidgetDraggable(float x, float y, float width, float height, Component text)
    {
        super(x, y, width, height);
        this.text = Objects.requireNonNull(text);
    }

    public HoloWidgetDraggable setOnDrop(Consumer<? super HoloWidgetDraggable> onDrop)
    {
        this.onDrop = Objects.requireNonNull(onDrop);
        return this;
    }

    public HoloWidgetDraggable setText(Component text)
    {
        this.text = Objects.requireNonNull(text);
        return this;
    }

    public HoloWidgetDraggable setColors(int color, int border)
    {
        this.color = color;
        this.border = border;
        return this;
    }

    /** Position venue du module, bornee au parent ; ignoree pendant un glisser. */
    public HoloWidgetDraggable setPosition(float x, float y)
    {
        if (!dragging()) moveTo(x, y);
        return this;
    }

    private void moveTo(float x, float y)
    {
        HoloPanel parent = parent();
        float maxX = parent != null ? parent.width() - width() : x;
        float maxY = parent != null ? parent.height() - height() : y;
        setBounds(Mth.clamp(x, 0f, Math.max(0f, maxX)), Mth.clamp(y, 0f, Math.max(0f, maxY)), width(), height());
    }

    @Override
    public HoloDragState dragState() { return dragState; }

    @Override
    public void onDragMove(HoloInteraction ctx, float dx, float dy) { moveTo(x() + dx, y() + dy); }

    @Override
    public void onDrop(HoloInteraction ctx) { onDrop.accept(this); }

    @Override
    public boolean hovered() { return hovered; }

    @Override
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        canvas.fill(0, 0, width(), height(), color);
        if (hovered || dragging()) canvas.fill(0, 0, width(), height(), dragging() ? 0x50FFFFFF : 0x28FFFFFF);
        canvas.outline(0, 0, width(), height(), 1f, border);
        canvas.text(text, (width() - canvas.textWidth(text)) / 2f, (height() - canvas.lineHeight()) / 2f + 1f,
                0xFFFFFFFF);
    }
}
