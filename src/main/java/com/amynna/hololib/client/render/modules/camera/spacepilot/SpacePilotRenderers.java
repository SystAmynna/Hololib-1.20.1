package com.amynna.hololib.client.render.modules.camera.spacepilot;

import com.amynna.hololib.client.render.HoloRenderers;
import com.amynna.hololib.client.render.holo.HoloObjectRenderers;
import com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickAim;
import com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickForward;

/**
 * Enregistrement des renderers de Space Pilot, appele par {@code ModuleRenderers} sur le fil de rendu, <b>apres</b>
 * {@code SpaceRenderers} : les {@code SkyRenderer} sont dessines dans l'ordre d'enregistrement
 * ({@code HoloRenderers}), et la surcouche doit passer apres le ciel spatial (meme passe, profondeur partagee). Les
 * cercles du joystick (objets du module, T19) passent par les ponts du mod ({@code HoloObjectRenderers}). Le HUD de
 * navigation ({@code SpacePilotHud}, T20) est une fenetre : rien a enregistrer, le renderer des fenetres le dessine.
 * Le reperage de la cible du saut et la roue de charge (T22) sont un {@code ViewRenderer} ({@link SpaceJumpRenderer}).
 */
public final class SpacePilotRenderers
{
    private SpacePilotRenderers() {}

    public static void register()
    {
        HoloRenderers.register(new SpacePilotSkyRenderer());
        HoloRenderers.register(new SpaceJumpRenderer());
        HoloObjectRenderers.register(SpaceJoystickForward.class, new SpaceJoystickRenderer.Forward());
        HoloObjectRenderers.register(SpaceJoystickAim.class, new SpaceJoystickRenderer.Aim());
    }
}
