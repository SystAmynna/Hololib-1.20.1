package com.amynna.hololib.modules.camera.editor;

import com.amynna.hololib.holo.edit.HoloDisplay;
import com.amynna.hololib.holo.edit.HoloEditorCapability;

/**
 * Own Editor : edition des objets de sa propre memoire. Porte aussi le repere de l'ecran ({@link EditorReference})
 * et la fenetre du cadre HUD ({@link EditorWindow}), prives et masques ({@code OFF}) : la session d'edition les
 * revele tant que l'editeur est actif.
 */
public final class OwnEditorModule extends EditorCapabilityModule
{
    private EditorReference reference;
    private EditorWindow window;

    public OwnEditorModule()
    {
        super(HoloEditorCapability.OWN, "Own Editor");
    }

    @Override
    public void onLoad()
    {
        reference = addObject(new EditorReference("reference"));
        reference.setDisplay(HoloDisplay.OFF);
        window = addObject(new EditorWindow("window"));
        window.setDisplay(HoloDisplay.OFF);
    }

    public EditorReference reference() { return reference; }

    public EditorWindow window() { return window; }
}
