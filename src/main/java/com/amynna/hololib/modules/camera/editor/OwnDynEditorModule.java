package com.amynna.hololib.modules.camera.editor;

import com.amynna.hololib.holo.edit.HoloDisplay;
import com.amynna.hololib.holo.edit.HoloEditorCapability;

/**
 * Own Dyn Editor : edition des objets dynamiques de sa propre memoire ({@code Local} follow et
 * {@code HoloRelativeToOpen}). Tant qu'il est actif, la session d'edition les tient fixes dans le repere du regard a
 * l'ouverture (capture de l'angle de la camera) ; il porte ce repere ({@link EditorReference} : cercle autour du
 * reticule et cadre des bords de l'ecran), prive et masque ({@code OFF}) hors session. Avec l'Own Fix Editor actif
 * aussi, la bascule fixe / dynamique est proposee.
 */
public final class OwnDynEditorModule extends EditorCapabilityModule
{
    private EditorReference reference;

    public OwnDynEditorModule()
    {
        super(HoloEditorCapability.OWN_DYN, "Own Dyn Editor");
    }

    @Override
    public void onLoad()
    {
        reference = addObject(new EditorReference("reference"));
        reference.setDisplay(HoloDisplay.OFF);
    }

    public EditorReference reference() { return reference; }
}
