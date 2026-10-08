package com.amynna.hololib.modules.camera.editor;

import com.amynna.hololib.holo.edit.HoloEditorCapability;

/**
 * Own Fix Editor : edition des objets fixes de sa propre memoire ({@code Local} relatif, monde, ciel). Avec l'Own Dyn
 * Editor actif aussi, la bascule fixe / dynamique est proposee.
 */
public final class OwnFixEditorModule extends EditorCapabilityModule
{
    public OwnFixEditorModule()
    {
        super(HoloEditorCapability.OWN_FIX, "Own Fix Editor");
    }
}
