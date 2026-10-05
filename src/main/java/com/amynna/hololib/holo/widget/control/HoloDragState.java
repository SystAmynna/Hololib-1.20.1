package com.amynna.hololib.holo.widget.control;

/** Etat d'un glisser ({@link HoloDraggable}) : point de prise, dans le repere de l'element. */
public final class HoloDragState
{
    private boolean dragging;
    private float grabX;
    private float grabY;

    public boolean dragging() { return dragging; }

    public float grabX() { return grabX; }

    public float grabY() { return grabY; }

    public void grab(float x, float y)
    {
        dragging = true;
        grabX = x;
        grabY = y;
    }

    public void release() { dragging = false; }
}
