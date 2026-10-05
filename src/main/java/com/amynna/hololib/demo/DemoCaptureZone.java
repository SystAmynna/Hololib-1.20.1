package com.amynna.hololib.demo;

import com.amynna.hololib.holo.interact.HoloInput;
import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Zone qui capture la souris quand on la regarde ({@link HoloInput#MOUSE_MOVE}) : pendant {@link #FREE_SECONDS}, la
 * camera est figee et un point suit les mouvements de la souris ; puis le regard est ramene en douceur vers le centre
 * de la fenetre, et la souris est rendue. Le centre de la fenetre doit etre hors de la zone.
 */
final class DemoCaptureZone extends HoloWidget implements HoloInteractible
{
    private static final float FREE_SECONDS = 1.5f;
    /** Delai maximal du retour au centre, en secondes (filet de securite). */
    private static final float RETURN_TIMEOUT = 2f;
    /** Vitesse de convergence du regard, par seconde. */
    private static final float RETURN_RATE = 8f;
    /** Ecart (degres) en dessous duquel le regard est considere centre. */
    private static final float RETURN_EPSILON = 0.3f;
    /** Pixels de la zone par degre de mouvement de la souris. */
    private static final float PIXELS_PER_DEGREE = 2f;

    private enum Phase
    {
        IDLE, FREE, RETURN
    }

    private Phase phase = Phase.IDLE;
    private float timer;
    private float dotX;
    private float dotY;

    DemoCaptureZone(float x, float y, float width, float height)
    {
        super(x, y, width, height);
    }

    @Override
    public boolean intercepts(HoloInput input) { return input == HoloInput.MOUSE_MOVE; }

    @Override
    public void onCaptureStart(HoloInteraction ctx)
    {
        phase = Phase.FREE;
        timer = 0f;
        dotX = width() / 2f;
        dotY = height() / 2f;
    }

    @Override
    public void onMouseMove(HoloInteraction ctx, float dYaw, float dPitch)
    {
        timer += ctx.frameSeconds();
        if (phase == Phase.FREE) {
            dotX = Mth.clamp(dotX + dYaw * PIXELS_PER_DEGREE, 2f, width() - 2f);
            dotY = Mth.clamp(dotY + dPitch * PIXELS_PER_DEGREE, 2f, height() - 2f);
            if (timer >= FREE_SECONDS) {
                phase = Phase.RETURN;
                timer = 0f;
            }
            return;
        }
        float yawError = Mth.wrapDegrees(ctx.objectYaw() - ctx.yaw());
        float pitchError = ctx.objectPitch() - ctx.pitch();
        if (Math.abs(yawError) < RETURN_EPSILON && Math.abs(pitchError) < RETURN_EPSILON || timer >= RETURN_TIMEOUT) {
            ctx.setLook(ctx.yaw() + yawError, ctx.objectPitch());
            ctx.releaseMouse();
            return;
        }
        float step = 1f - (float) Math.exp(-RETURN_RATE * ctx.frameSeconds());
        ctx.setLook(ctx.yaw() + yawError * step, ctx.pitch() + pitchError * step);
    }

    @Override
    public void onCaptureEnd(HoloInteraction ctx) { phase = Phase.IDLE; }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        int background = switch (phase) {
            case IDLE -> 0x40602020;
            case FREE -> 0x80A02020;
            case RETURN -> 0x60A06020;
        };
        canvas.fill(0, 0, width(), height(), background);
        canvas.outline(0, 0, width(), height(), 1f, 0xFFFF6060);
        Component text = Component.literal(switch (phase) {
            case IDLE -> "Zone de capture";
            case FREE -> "Souris capturee";
            case RETURN -> "Retour au centre";
        });
        canvas.text(text, (width() - canvas.textWidth(text)) / 2f, 4f, 0xFFFFC0C0);
        if (phase == Phase.FREE) canvas.fill(dotX - 2f, dotY - 2f, 4f, 4f, 0xFFFFFFFF);
    }
}
