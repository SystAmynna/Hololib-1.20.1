package com.amynna.hololib.modules.camera.spacepilot;

import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.modules.monde.spacecontroller.SpacePilotPad;

/**
 * Cercle avant du joystick de pilotage (objet de {@link SpacePilotModule}, migre du Space Controller en T19) :
 * ancrage {@code Local} relatif (fixe dans le monde, on peut en detourner le regard), place sur l'axe avant du monde
 * ({@code (yaw, pitch, }{@value #DISTANCE}{@code )}, reglage de l'Environment). Le client du proprietaire le replace a
 * chaque tick ({@link SpacePilotModule#clientTick}) ; le serveur ne le deplace jamais. Son renderer dessine aussi le
 * segment qui le relie au centre de l'ecran (cercle de visee, {@link SpaceJoystickAim}), en premiere personne
 * seulement.
 * <p>
 * Il materialise la <b>zone morte</b> (C48) : aucune rotation tant que le cercle de visee est entierement dedans,
 * {@code D = atan(RADIUS / DISTANCE) - atan(SpaceJoystickAim.RADIUS / DISTANCE)} ({@code SpacePilotPad.DEAD_ZONE}).
 * Rayon porte de 0,06 a {@value #RADIUS} bloc en T19 : avec 0,06, {@code D} valait 0,72 degre (moins de 1,5) ;
 * avec {@value #RADIUS}, {@code D} = 2,0 degres, la zone morte du lot 1 (C27).
 * <p>
 * {@code PRIVATE}, non editable, non interactible ; dessine seulement pendant que le joueur local pilote
 * ({@link SpacePilotModule#joystickShown()}). Conception : {@code docs/space/04-mathematiques.md} par. 6.
 */
public final class SpaceJoystickForward extends HoloObject
{
    /** Distance a l'oeil, en blocs (meme distance que le cercle de visee). */
    public static final float DISTANCE = 2f;
    /** Rayon de l'anneau, en blocs : avec le cercle de visee, il fixe la zone morte du joystick. */
    public static final float RADIUS = 0.105f;

    SpaceJoystickForward(String key)
    {
        super(key, new HoloAnchor.Local(false), HoloTransform.translation(0f, 0f, DISTANCE));
        setVisibility(HoloVisibility.PRIVATE);
    }

    /** Client : place le cercle dans la direction {@code (yaw, pitch)} (axes du monde, conventions de Minecraft). */
    void place(float yaw, float pitch) { transform().setTranslation(yaw, pitch, DISTANCE); }

    /** Seulement pendant que le joueur local pilote (capture du pad en cours, etat recu qui le designe). */
    @Override
    public boolean shown() { return module() instanceof SpacePilotModule pilot && pilot.joystickShown(); }

    /** Client : vrai si l'ecart du regard depasse la zone morte (etat du pad, une rotation est commandee). */
    public boolean beyondDeadZone()
    {
        SpacePilotPad pad = SpacePilotPad.localPilot();
        return pad != null && pad.beyondDeadZone();
    }

    @Override
    public boolean flat() { return true; }

    @Override
    public float boundingRadius() { return RADIUS * 1.25f; }
}
