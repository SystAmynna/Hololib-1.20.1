package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Boite pilotee au clavier ({@link HoloDirectionalInput}) : un clic capture les controles de deplacement, qui
 * deplacent un point dans la boite au lieu du joueur ; Echap ou Entree rend la main. Le point avance au tick (vitesse
 * {@link #setSpeed}, diagonales normalisees) et est interpole au dessin.
 * <p>
 * Heritable : {@link #onPointMoved()} (chaque tick ou le point a bouge ; appelle l'ecouteur par defaut),
 * {@link #drawContent} (ce qui est dessine dans la boite), {@link #drawDot}.
 */
public class HoloWidgetKeyBox extends HoloWidget implements HoloDirectionalInput, HoloHoverable
{
    private static final HoloColor BACKGROUND = HoloColor.selection(0x40).darker(0.71f);
    private static final HoloColor BACKGROUND_HOVER = HoloColor.selection(0x50).darker(0.67f);
    private static final HoloColor BACKGROUND_ACTIVE = HoloColor.selection(0x60).darker(0.37f);
    private static final HoloColor BORDER = HoloColor.selection(0xFF).darker(0.35f);
    private static final HoloColor BORDER_ACTIVE = HoloColor.selection(0xFF).lighter(0.2f);
    private static final HoloColor TEXT = HoloColor.selection(0xFF).lighter(0.6f);
    private static final HoloColor POINT = HoloColor.selection(0xFF).lighter(0.39f);
    private static final HoloColor POINT_ACTIVE = HoloColor.special(0xFF);

    protected static final float DOT = 6f;

    private final HoloDirections directions = new HoloDirections();
    private Consumer<? super HoloWidgetKeyBox> onMove = box -> {};
    private float speed = 3f;
    private boolean hovered;
    private float pointX;
    private float pointY;
    private float previousX;
    private float previousY;
    private Component idleText = Component.literal("Clic : prendre la main");
    private Component controlText = Component.literal("Echap / Entree : rendre");

    public HoloWidgetKeyBox(float x, float y, float width, float height)
    {
        super(x, y, width, height);
        pointX = previousX = width / 2f;
        pointY = previousY = height / 2f;
    }

    public HoloWidgetKeyBox setOnMove(Consumer<? super HoloWidgetKeyBox> onMove)
    {
        this.onMove = Objects.requireNonNull(onMove);
        return this;
    }

    /** Vitesse du point, en pixels par tick. */
    public HoloWidgetKeyBox setSpeed(float speed)
    {
        this.speed = speed;
        return this;
    }

    public HoloWidgetKeyBox setTexts(Component idle, Component controlling)
    {
        this.idleText = Objects.requireNonNull(idle);
        this.controlText = Objects.requireNonNull(controlling);
        return this;
    }

    public float pointX() { return pointX; }

    public float pointY() { return pointY; }

    /** Place le point (borne a la boite), sans interpolation. */
    public void setPoint(float x, float y)
    {
        pointX = previousX = clampX(x);
        pointY = previousY = clampY(y);
    }

    public float clampX(float x) { return Mth.clamp(x, DOT / 2f + 1f, width() - DOT / 2f - 1f); }

    public float clampY(float y) { return Mth.clamp(y, DOT / 2f + 1f, height() - DOT / 2f - 1f); }

    @Override
    public HoloDirections directions() { return directions; }

    @Override
    public boolean hovered() { return hovered; }

    @Override
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    /** Le point a bouge pendant ce tick. */
    protected void onPointMoved() { onMove.accept(this); }

    @Override
    public void tick()
    {
        previousX = pointX;
        previousY = pointY;
        if (!directions.any()) return;
        pointX = clampX(pointX + directions.dx() * speed);
        pointY = clampY(pointY + directions.dy() * speed);
        if (pointX != previousX || pointY != previousY) onPointMoved();
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        boolean controlling = controlling();
        canvas.fill(0, 0, width(), height(), canvas.color(controlling ? BACKGROUND_ACTIVE : hovered ? BACKGROUND_HOVER
                : BACKGROUND));
        canvas.outline(0, 0, width(), height(), 1f, canvas.color(controlling ? BORDER_ACTIVE : BORDER));
        Component text = controlling ? controlText : idleText;
        canvas.text(text, (width() - canvas.textWidth(text)) / 2f, height() - 12f, canvas.color(TEXT));
        drawContent(canvas, canvas.partialTick(), controlling);
    }

    /** Contenu de la boite : par defaut, le point. */
    protected void drawContent(HoloCanvas canvas, float partialTick, boolean controlling)
    {
        drawDot(canvas, interpolatedX(partialTick), interpolatedY(partialTick),
                canvas.color(controlling ? POINT_ACTIVE : POINT),
                false);
    }

    /** Position du point interpolee entre les deux derniers ticks. */
    protected float interpolatedX(float partialTick) { return previousX + (pointX - previousX) * partialTick; }

    protected float interpolatedY(float partialTick) { return previousY + (pointY - previousY) * partialTick; }

    /** Point centre en {@code (x, y)}, entoure de la couleur speciale si {@code highlighted}. */
    protected static void drawDot(HoloCanvas canvas, float x, float y, int color, boolean highlighted)
    {
        if (highlighted) {
            canvas.fill(x - DOT / 2f - 1f, y - DOT / 2f - 1f, DOT + 2f, DOT + 2f, canvas.color(POINT_ACTIVE));
        }
        canvas.fill(x - DOT / 2f, y - DOT / 2f, DOT, DOT, color);
    }
}
