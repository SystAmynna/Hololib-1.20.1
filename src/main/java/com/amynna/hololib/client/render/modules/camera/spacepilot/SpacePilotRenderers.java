package com.amynna.hololib.client.render.modules.camera.spacepilot;

import com.amynna.hololib.client.render.HoloRenderers;
import com.amynna.hololib.client.render.holo.HoloObjectRenderers;
import com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickAim;
import com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickForward;

/**
 * Enregistrement des renderers de Space Pilot, appele par {@code ModuleRenderers} sur le fil de rendu. Les cercles du
 * joystick (objets du module, T19) passent par les ponts du mod ({@code HoloObjectRenderers}). Le HUD de navigation
 * ({@code SpacePilotHud}, T20) est une fenetre : rien a enregistrer, le renderer des fenetres le dessine. Le reperage
 * de la cible du saut et la roue de charge (T22, {@link SpaceJumpRenderer}) et le lien et l'anneau du referentiel
 * (T33, {@link SpaceReferenceLinkRenderer}) sont des {@code ViewRenderer}, comme l'anneau et la fleche de la cible du
 * vaisseau (T43, {@link SpaceTargetRenderer}, deux instances autour du lien). La surcouche du ciel est passee a Space
 * Knowledge en T33 ({@code SpaceKnowledgeRenderers}).
 */
public final class SpacePilotRenderers
{
    private SpacePilotRenderers() {}

    public static void register()
    {
        HoloRenderers.register(new SpaceJumpRenderer());
        // Cible du vaisseau (T43) : fleche avant le lien du referentiel (ses tirets passent dessus), anneau apres (il
        // recouvre l'anneau du referentiel du meme corps). L'ordre d'enregistrement fait l'ordre de dessin.
        HoloRenderers.register(new SpaceTargetRenderer(SpaceTargetRenderer.Part.ARROW));
        HoloRenderers.register(new SpaceReferenceLinkRenderer());
        HoloRenderers.register(new SpaceTargetRenderer(SpaceTargetRenderer.Part.RING));
        HoloObjectRenderers.register(SpaceJoystickForward.class, new SpaceJoystickRenderer.Forward());
        HoloObjectRenderers.register(SpaceJoystickAim.class, new SpaceJoystickRenderer.Aim());
    }
}
