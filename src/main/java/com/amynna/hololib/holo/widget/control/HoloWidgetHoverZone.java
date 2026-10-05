package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Zone qui detecte le regard sans rien intercepter : s'illumine en fondu, change de titre au survol et previent un
 * ecouteur ({@link #setOnHoverChanged}). Une ligne de detail optionnelle s'affiche en bas ({@link #setDetail}).
 * Heritable : voir {@link HoloWidgetScrollZone}.
 */
public class HoloWidgetHoverZone extends HoloWidget implements HoloHoverable
{
    private static final int FADE_TICKS = 5;

    private Component idleText;
    private Component hoverText;
    private Component detail = Component.empty();
    private boolean hasDetail;
    private int color = 0xC06020;
    private int border = 0xFFFFA040;
    private int textColor = 0xFFFFE0A0;
    private BiConsumer<? super HoloWidgetHoverZone, Boolean> onHoverChanged = (zone, hovered) -> {};
    private boolean hovered;
    private int glow;
    private int previousGlow;

    public HoloWidgetHoverZone(float x, float y, float width, float height, Component idleText, Component hoverText)
    {
        super(x, y, width, height);
        this.idleText = Objects.requireNonNull(idleText);
        this.hoverText = Objects.requireNonNull(hoverText);
    }

    public HoloWidgetHoverZone setTexts(Component idleText, Component hoverText)
    {
        this.idleText = Objects.requireNonNull(idleText);
        this.hoverText = Objects.requireNonNull(hoverText);
        return this;
    }

    /** Ligne de detail en bas de la zone ({@code Component.empty()} = aucune). */
    public HoloWidgetHoverZone setDetail(Component detail)
    {
        this.detail = Objects.requireNonNull(detail);
        this.hasDetail = !detail.getString().isEmpty();
        return this;
    }

    /**
     * @param rgb    couleur de fond (sans alpha : il suit le fondu)
     * @param border couleur de la bordure {@code 0xAARRGGBB}
     */
    public HoloWidgetHoverZone setColors(int rgb, int border, int textColor)
    {
        this.color = rgb & 0xFFFFFF;
        this.border = border;
        this.textColor = textColor;
        return this;
    }

    public HoloWidgetHoverZone setOnHoverChanged(BiConsumer<? super HoloWidgetHoverZone, Boolean> listener)
    {
        this.onHoverChanged = Objects.requireNonNull(listener);
        return this;
    }

    @Override
    public boolean hovered() { return hovered; }

    @Override
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    @Override
    public void onHoverChanged(HoloInteraction ctx, boolean hovered) { onHoverChanged.accept(this, hovered); }

    @Override
    public void tick()
    {
        previousGlow = glow;
        glow = Math.max(0, Math.min(FADE_TICKS, glow + (hovered ? 1 : -1)));
    }

    /** Intensite du fondu de survol, 0..1. */
    protected float glow(float partialTick)
    {
        return (previousGlow + (glow - previousGlow) * partialTick) / FADE_TICKS;
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        int alpha = 0x30 + (int) (0x70 * glow(canvas.partialTick()));
        canvas.fill(0, 0, width(), height(), alpha << 24 | color);
        canvas.outline(0, 0, width(), height(), 1f, border);
        Component title = hovered ? hoverText : idleText;
        float titleY = hasDetail ? 8f : (height() - canvas.lineHeight()) / 2f + 1f;
        canvas.text(title, (width() - canvas.textWidth(title)) / 2f, titleY, textColor);
        if (hasDetail) {
            canvas.text(detail, (width() - canvas.textWidth(detail)) / 2f, height() - 16f, 0xFFFFFFFF);
        }
    }
}
