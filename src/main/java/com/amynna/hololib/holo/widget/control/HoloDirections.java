package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.capture.HoloControl;
import net.minecraft.util.Mth;

/**
 * Etat des quatre controles de deplacement des options ({@code key.forward}, {@code key.back}, {@code key.left},
 * {@code key.right} : la touche choisie par le joueur), pour {@link HoloDirectionalInput}.
 */
public final class HoloDirections
{
    public static final HoloControl UP = HoloControl.mapping("key.forward");
    public static final HoloControl DOWN = HoloControl.mapping("key.back");
    public static final HoloControl LEFT = HoloControl.mapping("key.left");
    public static final HoloControl RIGHT = HoloControl.mapping("key.right");

    private boolean up;
    private boolean down;
    private boolean left;
    private boolean right;

    public static boolean isDirection(HoloControl control)
    {
        return control.equals(UP) || control.equals(DOWN) || control.equals(LEFT) || control.equals(RIGHT);
    }

    /** Met a jour une direction ; faux si {@code control} n'en est pas une. */
    public boolean set(HoloControl control, boolean pressed)
    {
        if (control.equals(UP)) up = pressed;
        else if (control.equals(DOWN)) down = pressed;
        else if (control.equals(LEFT)) left = pressed;
        else if (control.equals(RIGHT)) right = pressed;
        else return false;
        return true;
    }

    public void clear() { up = down = left = right = false; }

    public boolean any() { return up || down || left || right; }

    /** Composante horizontale (-1 gauche, +1 droite), diagonales normalisees. */
    public float dx() { return component(right, left); }

    /** Composante verticale (-1 haut, +1 bas, {@code y} vers le bas comme les fenetres), diagonales normalisees. */
    public float dy() { return component(down, up); }

    private float component(boolean positive, boolean negative)
    {
        float value = (positive ? 1f : 0f) - (negative ? 1f : 0f);
        boolean diagonal = (up != down) && (left != right);
        return diagonal ? value * Mth.SQRT_OF_TWO / 2f : value;
    }
}
