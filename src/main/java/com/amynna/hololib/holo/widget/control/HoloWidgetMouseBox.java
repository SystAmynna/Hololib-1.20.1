package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.capture.HoloCapture;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Boite a souris ({@link HoloPointerInput}) : pendant la capture, la camera est figee et un point suit les mouvements
 * de la souris dans la boite ; Echap ou Entree rend la souris. Capture au clic (defaut) ou des que le regard entre
 * ({@link #setCaptureOnHover}). Un controle de recentrage optionnel ({@link #setRecenterControl}, ex.
 * {@code HoloControl.mapping("key.jump")}) ramene le point au centre. L'ecouteur recoit chaque deplacement.
 */
public class HoloWidgetMouseBox extends HoloWidget implements HoloPointerInput
{
    private static final float DOT = 4f;

    private Consumer<? super HoloWidgetMouseBox> onMove = box -> {};
    private boolean captureOnHover;
    private HoloControl recenter;
    private float pixelsPerDegree = 2f;
    private boolean hovered;
    private boolean captured;
    private float pointX;
    private float pointY;
    private Component idleText = Component.literal("Boite a souris");
    private Component capturedText = Component.literal("Souris capturee");
    private Component exitText = Component.literal("Echap / Entree");
    private static final HoloColor TEXT = HoloColor.critical(0xFF).lighter(0.64f);
    private static final HoloColor EXIT_TEXT = HoloColor.critical(0xFF).lighter(0.82f);
    private static final HoloColor POINT = HoloColor.special(0xFF);

    private HoloColor color = HoloColor.critical(0xFF).darker(0.39f);
    private HoloColor border = HoloColor.critical(0xFF).lighter(0.09f);

    public HoloWidgetMouseBox(float x, float y, float width, float height)
    {
        super(x, y, width, height);
        recenter();
    }

    public HoloWidgetMouseBox setOnMove(Consumer<? super HoloWidgetMouseBox> onMove)
    {
        this.onMove = Objects.requireNonNull(onMove);
        return this;
    }

    public HoloWidgetMouseBox setCaptureOnHover(boolean captureOnHover)
    {
        this.captureOnHover = captureOnHover;
        return this;
    }

    /** Controle qui recentre le point pendant la capture ({@code null} = aucun). */
    public HoloWidgetMouseBox setRecenterControl(HoloControl recenter)
    {
        this.recenter = recenter;
        return this;
    }

    /** Pixels de deplacement du point par degre de mouvement de la souris. */
    public HoloWidgetMouseBox setSensitivity(float pixelsPerDegree)
    {
        this.pixelsPerDegree = pixelsPerDegree;
        return this;
    }

    public HoloWidgetMouseBox setTexts(Component idle, Component captured, Component exit)
    {
        this.idleText = Objects.requireNonNull(idle);
        this.capturedText = Objects.requireNonNull(captured);
        this.exitText = Objects.requireNonNull(exit);
        return this;
    }

    public HoloWidgetMouseBox setColors(int rgb, int border)
    {
        return setColors(HoloColor.fixed(0xFF000000 | rgb), HoloColor.fixed(border));
    }

    /** Fond (son alpha est ignore : il suit l'etat) et bordure (par defaut : critique). */
    public HoloWidgetMouseBox setColors(HoloColor color, HoloColor border)
    {
        this.color = color;
        this.border = border;
        return this;
    }

    public float pointX() { return pointX; }

    public float pointY() { return pointY; }

    public void recenter()
    {
        pointX = width() / 2f;
        pointY = height() / 2f;
    }

    @Override
    public boolean captureOnHover() { return captureOnHover; }

    @Override
    public boolean hovered() { return hovered; }

    @Override
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    @Override
    public boolean captures(HoloControl control)
    {
        return HoloPointerInput.super.captures(control) || recenter != null && control.equals(recenter);
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
    public void onPress(HoloCapture ctx, HoloControl control)
    {
        if (recenter != null && control.equals(recenter)) {
            recenter();
            onMove.accept(this);
        } else {
            HoloPointerInput.super.onPress(ctx, control);
        }
    }

    @Override
    public void onMouseMove(HoloCapture ctx, float dYaw, float dPitch)
    {
        if (dYaw == 0f && dPitch == 0f) return;
        pointX = Mth.clamp(pointX + dYaw * pixelsPerDegree, DOT / 2f, width() - DOT / 2f);
        pointY = Mth.clamp(pointY + dPitch * pixelsPerDegree, DOT / 2f, height() - DOT / 2f);
        onMove.accept(this);
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        canvas.fill(0, 0, width(), height(), color.resolve(canvas.theme(), captured ? 0x80 : hovered ? 0x58 : 0x40));
        canvas.outline(0, 0, width(), height(), 1f, canvas.color(border));
        Component text = captured ? capturedText : idleText;
        canvas.text(text, (width() - canvas.textWidth(text)) / 2f, 4f, canvas.color(TEXT));
        if (!captured) return;
        canvas.text(exitText, (width() - canvas.textWidth(exitText)) / 2f, height() - 12f, canvas.color(EXIT_TEXT));
        canvas.fill(pointX - DOT / 2f, pointY - DOT / 2f, DOT, DOT, canvas.color(POINT));
    }
}
