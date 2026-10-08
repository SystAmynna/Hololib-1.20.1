package com.amynna.hololib.modules.camera.editor;

import com.amynna.hololib.holo.edit.HoloDisplay;
import com.amynna.hololib.holo.edit.HoloEditorCapability;

/**
 * Own Hud Editor : edition des objets HUD de sa propre memoire. Porte la fenetre du cadre HUD ({@link EditorWindow}),
 * privee et masquee ({@code OFF}) : tant que l'editeur est actif, la session d'edition la revele et les objets HUD y
 * sont dessines en miniature (ils quittent le HUD).
 */
public final class OwnHudEditorModule extends EditorCapabilityModule
{
    private EditorWindow window;

    public OwnHudEditorModule()
    {
        super(HoloEditorCapability.OWN_HUD, "Own Hud Editor");
    }

    @Override
    public void onLoad()
    {
        window = addObject(new EditorWindow("window"));
        window.setDisplay(HoloDisplay.OFF);
    }

    public EditorWindow window() { return window; }
}
