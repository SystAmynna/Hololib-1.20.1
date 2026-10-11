package com.amynna.hololib.client.render.modules.camera.spaceknowledge;

import com.amynna.hololib.client.render.HoloRenderers;

/**
 * Enregistrement des renderers de Space Knowledge (T33), appele par {@code ModuleRenderers} sur le fil de rendu,
 * <b>apres</b> {@code SpaceRenderers} : les {@code SkyRenderer} sont dessines dans l'ordre d'enregistrement
 * ({@code HoloRenderers}), et la surcouche doit passer apres le ciel spatial (meme passe, profondeur partagee). La
 * fenetre de Config du module est une fenetre : rien a enregistrer, le renderer des fenetres la dessine.
 */
public final class SpaceKnowledgeRenderers
{
    private SpaceKnowledgeRenderers() {}

    public static void register()
    {
        HoloRenderers.register(new SpaceKnowledgeSkyRenderer());
    }
}
