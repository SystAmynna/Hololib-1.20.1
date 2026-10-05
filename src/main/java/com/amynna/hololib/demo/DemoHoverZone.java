package com.amynna.hololib.demo;

import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import net.minecraft.network.chat.Component;

/**
 * Zone qui reagit au regard sans rien intercepter : s'illumine (fondu sur quelques ticks) et change de texte. Etat
 * purement local.
 */
final class DemoHoverZone extends HoloWidget implements HoloInteractible
{
    private static final int FADE_TICKS = 5;
    private static final Component IDLE = Component.literal("Regarde-moi");
    private static final Component SEEN = Component.literal("Je te vois !");

    private boolean hovered;
    private int glow;
    private int previousGlow;

    DemoHoverZone(float x, float y, float width, float height)
    {
        super(x, y, width, height);
    }

    @Override
    public void onHover(HoloInteraction ctx) { hovered = true; }

    @Override
    public void onHoverRelease(HoloInteraction ctx) { hovered = false; }

    @Override
    public void tick()
    {
        previousGlow = glow;
        glow = Math.max(0, Math.min(FADE_TICKS, glow + (hovered ? 1 : -1)));
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        float t = (previousGlow + (glow - previousGlow) * canvas.partialTick()) / FADE_TICKS;
        int alpha = 0x30 + (int) (0x70 * t);
        canvas.fill(0, 0, width(), height(), alpha << 24 | 0xC06020);
        canvas.outline(0, 0, width(), height(), 1f, 0xFFFFA040);
        Component text = hovered ? SEEN : IDLE;
        canvas.text(text, (width() - canvas.textWidth(text)) / 2f, (height() - canvas.lineHeight()) / 2f + 1f,
                0xFFFFE0A0);
    }
}
