package com.amynna.hololib.holo.widget.control;

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
        canvas.fill(0, 0, width(), height(), controlling ? 0x6020A040 : hovered ? 0x50205020 : 0x40204020);
        canvas.outline(0, 0, width(), height(), 1f, controlling ? 0xFF80FF80 : 0xFF40A040);
        Component text = controlling ? controlText : idleText;
        canvas.text(text, (width() - canvas.textWidth(text)) / 2f, height() - 12f, 0xFFC0FFC0);
        drawContent(canvas, canvas.partialTick(), controlling);
    }

    /** Contenu de la boite : par defaut, le point. */
    protected void drawContent(HoloCanvas canvas, float partialTick, boolean controlling)
    {
        drawDot(canvas, interpolatedX(partialTick), interpolatedY(partialTick), controlling ? 0xFFFFFFFF : 0xFFA0C0A0,
                false);
    }

    /** Position du point interpolee entre les deux derniers ticks. */
    protected float interpolatedX(float partialTick) { return previousX + (pointX - previousX) * partialTick; }

    protected float interpolatedY(float partialTick) { return previousY + (pointY - previousY) * partialTick; }

    /** Point centre en {@code (x, y)}, entoure de blanc si {@code highlighted}. */
    protected static void drawDot(HoloCanvas canvas, float x, float y, int color, boolean highlighted)
    {
        if (highlighted) canvas.fill(x - DOT / 2f - 1f, y - DOT / 2f - 1f, DOT + 2f, DOT + 2f, 0xFFFFFFFF);
        canvas.fill(x - DOT / 2f, y - DOT / 2f, DOT, DOT, color);
    }
}
