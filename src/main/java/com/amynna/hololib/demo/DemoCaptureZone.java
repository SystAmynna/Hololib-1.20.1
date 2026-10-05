package com.amynna.hololib.demo;

import com.amynna.hololib.holo.capture.HoloCapture;
import com.amynna.hololib.holo.capture.HoloCaptor;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Zone qui capture les controles quand le regard y entre : exemple d'interaction ({@link HoloInteractible}, pour le
 * survol) qui demarre une capture explicite ({@link HoloCaptor}, liee a la fenetre). Pendant la capture, la camera est
 * figee et un point suit les mouvements de la souris dans la zone ; Saut (un controle des options, quelle que soit sa
 * touche) recentre le point ; Echap ou Entree rend les controles. La capture ne se termine jamais d'elle-meme ; le
 * regard etant reste sur la zone, il faut la quitter puis y revenir pour la reprendre.
 */
final class DemoCaptureZone extends HoloWidget implements HoloInteractible, HoloCaptor
{
    /** Pixels de la zone par degre de mouvement de la souris. */
    private static final float PIXELS_PER_DEGREE = 2f;
    private static final HoloControl JUMP = HoloControl.mapping("key.jump");
    private static final Component IDLE = Component.literal("Zone de capture");
    private static final Component CAPTURED = Component.literal("Souris capturee");
    private static final Component EXIT = Component.literal("Echap / Entree");

    private boolean captured;
    private float dotX;
    private float dotY;

    DemoCaptureZone(float x, float y, float width, float height)
    {
        super(x, y, width, height);
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public void onHover(HoloInteraction ctx) { ctx.capture(this); }

    // ---------------------------------------------------------------- capture

    @Override
    public boolean captures(HoloControl control)
    {
        return control.equals(HoloControl.MOUSE_MOVE) || control.equals(HoloControl.ESCAPE)
                || control.equals(HoloControl.ENTER) || control.equals(JUMP);
    }

    @Override
    public void onCaptureStart(HoloCapture ctx)
    {
        captured = true;
        recenter();
    }

    @Override
    public void onCaptureEnd(HoloCapture ctx) { captured = false; }

    @Override
    public void onMouseMove(HoloCapture ctx, float dYaw, float dPitch)
    {
        dotX = Mth.clamp(dotX + dYaw * PIXELS_PER_DEGREE, 2f, width() - 2f);
        dotY = Mth.clamp(dotY + dPitch * PIXELS_PER_DEGREE, 2f, height() - 2f);
    }

    @Override
    public void onPress(HoloCapture ctx, HoloControl control)
    {
        if (control.equals(JUMP)) {
            recenter();
        } else {
            ctx.release();
        }
    }

    private void recenter()
    {
        dotX = width() / 2f;
        dotY = height() / 2f;
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        canvas.fill(0, 0, width(), height(), captured ? 0x80A02020 : 0x40602020);
        canvas.outline(0, 0, width(), height(), 1f, 0xFFFF6060);
        Component text = captured ? CAPTURED : IDLE;
        canvas.text(text, (width() - canvas.textWidth(text)) / 2f, 4f, 0xFFFFC0C0);
        if (!captured) return;
        canvas.text(EXIT, (width() - canvas.textWidth(EXIT)) / 2f, height() - 12f, 0xFFFFE0E0);
        canvas.fill(dotX - 2f, dotY - 2f, 4f, 4f, 0xFFFFFFFF);
    }
}
