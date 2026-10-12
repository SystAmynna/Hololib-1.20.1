package com.amynna.hololib.modules.camera.config;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import com.amynna.hololib.holo.widget.control.HoloHoverable;
import com.amynna.hololib.holo.widget.control.HoloToggleable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Epingle d'une {@link ConfigWindow} : interrupteur dessine comme une punaise, droite et doree quand la fenetre est
 * epinglee, penchee et grise sinon. Predictif : bascule aussitot, puis l'etat est recopie depuis le module Config.
 */
public class ConfigPinButton extends HoloWidget implements HoloToggleable, HoloHoverable
{
    private static final HoloColor PINNED = HoloColor.action(0xFF);
    private static final HoloColor UNPINNED = HoloColor.special(0xFF).darker(0.37f);
    private static final HoloColor NEEDLE = HoloColor.special(0xFF).darker(0.12f);
    private static final HoloColor HOVER = HoloColor.special(0x30);
    /** Taille de reference du dessin (unites des formes ci-dessous). */
    private static final float SIZE = 14f;
    private static final float TILT = (float) Math.toRadians(45.0);

    private final Consumer<Boolean> onToggle;
    private boolean on;
    private boolean hovered;
    // Repere de la punaise pendant le dessin : centre, axe (vers la pointe) et perpendiculaire, mis a l'echelle.
    private float cx;
    private float cy;
    private float ax;
    private float ay;

    public ConfigPinButton(float x, float y, float width, float height, Consumer<Boolean> onToggle)
    {
        super(x, y, width, height);
        this.onToggle = Objects.requireNonNull(onToggle);
    }

    @Override
    public boolean isOn() { return on; }

    @Override
    public void setOn(boolean on) { this.on = on; }

    @Override
    public void onToggle(HoloInteraction ctx, boolean requested)
    {
        on = requested;
        onToggle.accept(requested);
    }

    @Override
    public boolean hovered() { return hovered; }

    @Override
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        if (hovered) canvas.fill(0, 0, width(), height(), canvas.color(HOVER));
        float k = Math.min(width(), height()) / SIZE;
        // Droite (pointe vers le bas) si epinglee, penchee vers la gauche sinon.
        float angle = on ? 0f : TILT;
        cx = width() / 2f;
        cy = height() / 2f;
        ax = -(float) Math.sin(angle) * k;
        ay = (float) Math.cos(angle) * k;
        int color = canvas.color(on ? PINNED : UNPINNED);
        // (u le long de l'axe depuis le centre, v en travers) : tete, corps, collerette, aiguille.
        part(canvas, -5.5f, -3.5f, 3.5f, 3.5f, color);
        part(canvas, -3.5f, 0.5f, 2f, 2f, color);
        part(canvas, 0.5f, 1.5f, 3.5f, 3.5f, color);
        part(canvas, 1.5f, 6f, 0.6f, 0f, canvas.color(NEEDLE));
    }

    /** Trapeze de {@code u0} a {@code u1} le long de l'axe, de demi-largeurs {@code v0} puis {@code v1}. */
    private void part(HoloCanvas canvas, float u0, float u1, float v0, float v1, int color)
    {
        // Perpendiculaire a l'axe : (ay, -ax).
        canvas.quad(x(u0, -v0), y(u0, -v0), x(u0, v0), y(u0, v0), x(u1, v1), y(u1, v1), x(u1, -v1), y(u1, -v1),
                color);
    }

    private float x(float u, float v) { return cx + u * ax + v * ay; }

    private float y(float u, float v) { return cy + u * ay - v * ax; }
}
