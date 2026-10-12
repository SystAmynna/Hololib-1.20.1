package com.amynna.hololib.modules.camera.config;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * Bouton rond de Config : un disque borde avec un ecrou (hexagone perce) au centre. Chaque appui fait tourner l'ecrou
 * d'un tiers de tour ({@link #spin()}, anime au tick, interpole au dessin). Seul le disque est vise.
 */
public class ConfigNutButton extends HoloWidgetButton
{
    private static final int SEGMENTS = 32;
    private static final int SIDE_STEPS = 4;
    private static final float SIXTH = Mth.TWO_PI / 6f;
    /** Rotation d'un appui ; multiple de 60 degres (symetrie de l'ecrou), pour revenir a l'angle 0 au repos. */
    private static final float SPIN = Mth.TWO_PI / 3f;
    private static final int SPIN_TICKS = 12;
    private static final HoloColor DISK = HoloColor.secondary(0xC0).darker(0.84f);
    private static final HoloColor DISK_HOVER = HoloColor.secondary(0xD0).darker(0.68f);
    private static final HoloColor RIM = HoloColor.primary(0xFF);
    private static final HoloColor NUT = HoloColor.primary(0xFF);
    private static final HoloColor NUT_HOVER = HoloColor.primary(0xFF).lighter(0.51f);

    private float angle;
    private float prevAngle;
    private float spinStart;
    private int spinTick = SPIN_TICKS;

    public ConfigNutButton(float x, float y, float size, Consumer<? super HoloWidgetButton> onPress)
    {
        super(x, y, size, size, Component.empty(), onPress);
    }

    /** Lance un tiers de tour (repart de l'angle courant si l'ecrou tourne deja). */
    public void spin()
    {
        spinStart = angle;
        spinTick = 0;
    }

    @Override
    public void tick()
    {
        prevAngle = angle;
        if (spinTick < SPIN_TICKS) {
            spinTick++;
            float t = 1f - (float) spinTick / SPIN_TICKS;
            angle = spinStart + SPIN * (1f - t * t * t);
        } else {
            // Au repos : on ramene l'angle dans [0, 60[ sans changer le dessin.
            angle = prevAngle = angle % SIXTH;
        }
    }

    @Override
    public boolean contains(float px, float py)
    {
        float r = radius();
        float dx = px - x() - r;
        float dy = py - y() - r;
        return dx * dx + dy * dy <= r * r;
    }

    private float radius() { return Math.min(width(), height()) / 2f; }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        float r = radius();
        float c = r;
        boolean lit = hovered() || pressed();
        int disk = canvas.color(lit ? DISK_HOVER : DISK);
        int rimColor = canvas.color(RIM);
        // Disque en eventail puis bordure, dans une couche (a plat) ; l'ecrou dans une autre, devant.
        canvas.beginLayer();
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = i * Mth.TWO_PI / SEGMENTS;
            float a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
            float x0 = c + r * Mth.cos(a0);
            float y0 = c + r * Mth.sin(a0);
            float x1 = c + r * Mth.cos(a1);
            float y1 = c + r * Mth.sin(a1);
            canvas.quad(c, c, x0, y0, x1, y1, x1, y1, disk);
        }
        float rim = r - 1.2f;
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = i * Mth.TWO_PI / SEGMENTS;
            float a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
            canvas.quad(c + r * Mth.cos(a0), c + r * Mth.sin(a0), c + r * Mth.cos(a1), c + r * Mth.sin(a1),
                    c + rim * Mth.cos(a1), c + rim * Mth.sin(a1), c + rim * Mth.cos(a0), c + rim * Mth.sin(a0),
                    rimColor);
        }
        canvas.endLayer();
        // Ecrou : chaque cote de l'hexagone, decoupe en bandes jusqu'au trou rond.
        float turn = Mth.lerp(canvas.partialTick(), prevAngle, angle);
        float hex = 0.62f * r;
        float hole = 0.28f * r;
        int color = canvas.color(lit ? NUT_HOVER : NUT);
        canvas.beginLayer();
        for (int side = 0; side < 6; side++) {
            float a = turn + side * SIXTH;
            float hx0 = c + hex * Mth.cos(a);
            float hy0 = c + hex * Mth.sin(a);
            float hx1 = c + hex * Mth.cos(a + SIXTH);
            float hy1 = c + hex * Mth.sin(a + SIXTH);
            for (int step = 0; step < SIDE_STEPS; step++) {
                float t0 = (float) step / SIDE_STEPS;
                float t1 = (float) (step + 1) / SIDE_STEPS;
                float b0 = a + t0 * SIXTH;
                float b1 = a + t1 * SIXTH;
                canvas.quad(Mth.lerp(t0, hx0, hx1), Mth.lerp(t0, hy0, hy1), Mth.lerp(t1, hx0, hx1),
                        Mth.lerp(t1, hy0, hy1), c + hole * Mth.cos(b1), c + hole * Mth.sin(b1),
                        c + hole * Mth.cos(b0), c + hole * Mth.sin(b0), color);
            }
        }
        canvas.endLayer();
    }
}
