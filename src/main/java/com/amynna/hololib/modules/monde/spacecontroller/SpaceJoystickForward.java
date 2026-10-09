package com.amynna.hololib.modules.monde.spacecontroller;

import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;

/**
 * Cercle avant du joystick du Space Controller : ancrage {@code Local} relatif (fixe dans le monde, on peut en
 * detourner le regard), place sur l'axe avant du monde ({@code (yaw, pitch, }{@value #DISTANCE}{@code )}, reglage de
 * l'Environment). Le client le replace a chaque tick ({@link SpaceControllerModule#clientTick}) ; le serveur ne le
 * deplace jamais. Son renderer dessine aussi le segment qui le relie au centre de l'ecran (cercle de visee,
 * {@link SpaceJoystickAim}), en premiere personne seulement.
 * <p>
 * {@code PUBLIC}, non editable, non interactible ; dessine seulement chez le pilote local de ce poste
 * ({@link SpaceControllerModule#localPilotHere()}). Conception : {@code docs/space/04-mathematiques.md} par. 6.
 */
public final class SpaceJoystickForward extends HoloObject
{
    /** Distance a l'oeil, en blocs (meme distance que le cercle de visee). */
    public static final float DISTANCE = 2f;
    /** Rayon de l'anneau, en blocs. */
    public static final float RADIUS = 0.06f;

    SpaceJoystickForward(String key)
    {
        super(key, new HoloAnchor.Local(false), HoloTransform.translation(0f, 0f, DISTANCE));
        setVisibility(HoloVisibility.PUBLIC);
    }

    /** Client : place le cercle dans la direction {@code (yaw, pitch)} (axes du monde, conventions de Minecraft). */
    void place(float yaw, float pitch) { transform().setTranslation(yaw, pitch, DISTANCE); }

    /** Seulement chez le pilote local de ce poste. */
    @Override
    public boolean shown()
    {
        return module() instanceof SpaceControllerModule controller && controller.localPilotHere();
    }

    /** Client : vrai si l'ecart du regard depasse la zone morte (etat du pad, une rotation est commandee). */
    public boolean beyondDeadZone()
    {
        return module() instanceof SpaceControllerModule controller && controller.window() != null
                && controller.window().pad().beyondDeadZone();
    }

    @Override
    public boolean flat() { return true; }

    @Override
    public float boundingRadius() { return RADIUS * 1.25f; }
}
