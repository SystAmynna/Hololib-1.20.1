package com.amynna.hololib.demo;

import com.amynna.hololib.holo.interact.HoloInput;
import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** Zone qui intercepte la molette quand on la regarde ; chaque cran est transmis (+1 vers le haut, -1 vers le bas). */
final class DemoScrollZone extends HoloWidget implements HoloInteractible
{
    private final IntConsumer onScroll;
    private Component text = Component.empty();
    private boolean hovered;

    DemoScrollZone(float x, float y, float width, float height, IntConsumer onScroll)
    {
        super(x, y, width, height);
        this.onScroll = onScroll;
    }

    void setText(Component text) { this.text = text; }

    @Override
    public boolean intercepts(HoloInput input) { return input == HoloInput.SCROLL; }

    @Override
    public void onHover(HoloInteraction ctx) { hovered = true; }

    @Override
    public void onHoverRelease(HoloInteraction ctx) { hovered = false; }

    @Override
    public void onScroll(HoloInteraction ctx, double delta)
    {
        int steps = (int) Math.round(delta);
        if (steps == 0) steps = delta > 0 ? 1 : -1;
        onScroll.accept(steps);
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        canvas.fill(0, 0, width(), height(), hovered ? 0x6040A040 : 0x40206020);
        canvas.outline(0, 0, width(), height(), 1f, 0xFF60E060);
        Component title = Component.literal(hovered ? "Molette !" : "Zone molette");
        canvas.text(title, (width() - canvas.textWidth(title)) / 2f, 8f, 0xFFA0FFA0);
        canvas.text(text, (width() - canvas.textWidth(text)) / 2f, height() - 16f, 0xFFFFFFFF);
    }
}
