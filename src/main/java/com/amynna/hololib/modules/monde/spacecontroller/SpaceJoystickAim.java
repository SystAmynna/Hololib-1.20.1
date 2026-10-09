package com.amynna.hololib.modules.monde.spacecontroller;

import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;

/**
 * Cercle de visee du joystick du Space Controller : ancrage {@code Local} follow au centre de l'ecran, a
 * {@value SpaceJoystickForward#DISTANCE} blocs (meme distance que le {@linkplain SpaceJoystickForward cercle avant}).
 * {@code PUBLIC}, non editable, non interactible ; dessine seulement chez le pilote local de ce poste
 * ({@link SpaceControllerModule#localPilotHere()}).
 */
public final class SpaceJoystickAim extends HoloObject
{
    /** Rayon de l'anneau, en blocs. */
    public static final float RADIUS = 0.035f;

    SpaceJoystickAim(String key)
    {
        super(key, new HoloAnchor.Local(true), HoloTransform.translation(0f, 0f, SpaceJoystickForward.DISTANCE));
        setVisibility(HoloVisibility.PUBLIC);
    }

    /** Seulement chez le pilote local de ce poste. */
    @Override
    public boolean shown()
    {
        return module() instanceof SpaceControllerModule controller && controller.localPilotHere();
    }

    @Override
    public boolean flat() { return true; }

    @Override
    public float boundingRadius() { return RADIUS * 1.25f; }
}
