package com.amynna.hololib.modules.camera.editor;

import com.amynna.hololib.holo.edit.HoloDisplay;
import com.amynna.hololib.holo.module.HoloModule;

/**
 * Editor Panel : fenetre ({@link EditorPanelWindow}) qui apparait a l'ouverture du mode edition quand au moins deux
 * editeurs sont equipes, pour les activer ou les desactiver. Fenetre privee, masquee ({@code OFF}) hors session.
 */
public final class EditorPanelModule extends HoloModule
{
    private EditorPanelWindow window;

    @Override
    public void onLoad()
    {
        window = addObject(new EditorPanelWindow("panel"));
        window.setDisplay(HoloDisplay.OFF);
    }

    public EditorPanelWindow window() { return window; }
}
