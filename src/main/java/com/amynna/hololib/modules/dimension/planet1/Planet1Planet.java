package com.amynna.hololib.modules.dimension.planet1;

import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import net.minecraft.util.Mth;

/**
 * Geante gazeuse cubique centree sur son origine locale. Elle tourne lentement sur son axe {@code Y} local et ses
 * bandes nuageuses ondulent ; les deux sont animes dans {@link #tick()} (cote client) et interpoles au dessin.
 */
public final class Planet1Planet extends HoloObject
{
    /** Rotation propre, en degres par tick (un tour en ~6 minutes). */
    private static final float SPIN_SPEED = 0.05f;
    /** Avance de la turbulence des bandes, en radians par tick. */
    private static final float FLOW_SPEED = 0.01f;
    /** Periode de {@link #flow} : multiple de {@code 2 PI} pour les frequences utilisees par le renderer. */
    private static final float FLOW_PERIOD = 20f * Mth.PI;

    private final float half;
    private float spin;
    private float spinO;
    private float flow;
    private float flowO;

    /** @param half demi-arete, en blocs */
    public Planet1Planet(String key, HoloAnchor anchor, HoloTransform transform, float half)
    {
        super(key, anchor, transform);
        this.half = half;
    }

    public float half() { return half; }

    /** Rotation propre interpolee, en degres. */
    public float spin(float partialTick) { return Mth.lerp(partialTick, spinO, spin); }

    /** Phase interpolee de la turbulence des bandes, en radians. */
    public float flow(float partialTick) { return Mth.lerp(partialTick, flowO, flow); }

    @Override
    public float boundingRadius() { return half * 1.75f; }

    @Override
    public void tick()
    {
        spinO = spin;
        spin += SPIN_SPEED;
        if (spin >= 360f) {
            spin -= 360f;
            spinO -= 360f;
        }
        flowO = flow;
        flow += FLOW_SPEED;
        if (flow >= FLOW_PERIOD) {
            flow -= FLOW_PERIOD;
            flowO -= FLOW_PERIOD;
        }
    }
}
