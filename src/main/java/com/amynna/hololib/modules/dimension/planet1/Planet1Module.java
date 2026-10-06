package com.amynna.hololib.modules.dimension.planet1;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;

/** Planete gazeuse cubique et son anneau carre, dans le ciel de la dimension. Aucun etat : tout est constant. */
public final class Planet1Module extends HoloModule
{
    /** Direction de la planete depuis l'oeil (degres, conventions de Minecraft : pitch negatif = vers le haut). */
    private static final float YAW = 135f;
    private static final float PITCH = -30f;
    /** Distance du centre a l'oeil : coin le plus lointain de l'anneau a ~{@code 40 + 56 * sqrt(2)} < 128 blocs. */
    private static final float DISTANCE = 40f;

    /** Demi-arete de la planete (~36 degres de ciel) ; sa demi-diagonale (~22.5) reste sous le rayon de l'anneau. */
    private static final float PLANET_HALF = 13f;
    /** Inclinaison propre de la planete par rapport a la direction de l'oeil (degres). */
    private static final float PLANET_TILT_YAW = 20f;
    private static final float PLANET_TILT_PITCH = 18f;
    private static final float PLANET_ROLL = -15f;

    /** Demi-cotes interieur et exterieur de l'anneau : l'oeil, a {@link #DISTANCE} du centre, est dans la bande. */
    private static final float RING_INNER = 24f;
    private static final float RING_OUTER = 56f;
    /** Rotation de l'anneau autour de l'axe centre -> oeil : son plan contient toujours cet axe. */
    private static final float RING_ROLL = 25f;
    /** Ecart du plan de l'anneau a l'oeil (degres) : ~{@code DISTANCE * sin(6) * cos(25)} = 3.8 blocs. */
    private static final float RING_OFFSET = 6f;

    @Override
    public void onLoad()
    {
        // Ordre d'ajout = ordre de dessin : la planete opaque avant l'anneau translucide (qui ecrit la profondeur).
        HoloTransform planet = new HoloTransform().setLookingAtOrigin(YAW, PITCH, DISTANCE);
        planet.setRotation(planet.yaw() + PLANET_TILT_YAW, planet.pitch() + PLANET_TILT_PITCH, PLANET_ROLL);
        addObject(new Planet1Planet("planet", new HoloAnchor.Sky(false), planet, PLANET_HALF))
                .setVisibility(HoloVisibility.PUBLIC);

        // Face +Z vers l'oeil : le plan local XZ de l'anneau contient l'axe centre -> oeil, quel que soit le roll.
        HoloTransform ring = new HoloTransform().setLookingAtOrigin(YAW, PITCH, DISTANCE);
        ring.setRotation(ring.yaw(), ring.pitch() + RING_OFFSET, RING_ROLL);
        addObject(new Planet1Ring("ring", new HoloAnchor.Sky(false), ring, RING_INNER, RING_OUTER))
                .setVisibility(HoloVisibility.PUBLIC);
    }
}
