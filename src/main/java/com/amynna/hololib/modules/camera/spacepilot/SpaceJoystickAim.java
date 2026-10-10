package com.amynna.hololib.modules.camera.spacepilot;

import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;

/**
 * Cercle de visee du joystick de pilotage (objet de {@link SpacePilotModule}, migre du Space Controller en T19) :
 * ancrage {@code Local} follow au centre de l'ecran, a {@value SpaceJoystickForward#DISTANCE} blocs (meme distance que
 * le {@linkplain SpaceJoystickForward cercle avant}). Son rayon entre dans la zone morte (C48). {@code PRIVATE}, non
 * editable, non interactible ; dessine seulement pendant que le joueur local pilote
 * ({@link SpacePilotModule#joystickShown()}).
 */
public final class SpaceJoystickAim extends HoloObject
{
    /** Rayon de l'anneau, en blocs. */
    public static final float RADIUS = 0.035f;

    SpaceJoystickAim(String key)
    {
        super(key, new HoloAnchor.Local(true), HoloTransform.translation(0f, 0f, SpaceJoystickForward.DISTANCE));
        setVisibility(HoloVisibility.PRIVATE);
    }

    /** Seulement pendant que le joueur local pilote. */
    @Override
    public boolean shown() { return module() instanceof SpacePilotModule pilot && pilot.joystickShown(); }

    @Override
    public boolean flat() { return true; }

    @Override
    public float boundingRadius() { return RADIUS * 1.25f; }
}
