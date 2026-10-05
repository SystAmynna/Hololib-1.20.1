package com.amynna.hololib.demo;

import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import net.minecraft.util.Mth;

/**
 * Cube holographique qui tourne sur lui-meme. L'angle est anime dans {@link #tick()} (cote client) ; le renderer
 * interpole entre l'angle precedent et l'angle courant.
 */
public final class DemoCube extends HoloObject
{
    /** Vitesse de rotation, en degres par tick. */
    private static final float SPEED = 4f;

    private final float size;
    private final int color;
    private float spin;
    private float spinO;

    /**
     * @param size  arete, en blocs
     * @param color couleur {@code 0xAARRGGBB}
     */
    public DemoCube(String key, HoloAnchor anchor, HoloTransform transform, float size, int color)
    {
        super(key, anchor, transform);
        this.size = size;
        this.color = color;
    }

    public float size() { return size; }

    public int color() { return color; }

    /** Angle de rotation interpole, en degres. */
    public float spin(float partialTick) { return Mth.lerp(partialTick, spinO, spin); }

    @Override
    public float boundingRadius() { return size * 0.87f; }

    @Override
    public void tick()
    {
        spinO = spin;
        spin += SPEED;
        if (spin >= 360f) {
            spin -= 360f;
            spinO -= 360f;
        }
    }
}
