package com.amynna.hololib.modules.camera.editor;

import com.amynna.hololib.holo.edit.HoloEditorCapability;

/**
 * Dimension Editor : edition des objets de la memoire de dimension (celestes) dont le joueur est proprietaire
 * (createur du module), ou de tous avec l'Other Editor.
 */
public final class DimensionEditorModule extends EditorCapabilityModule
{
    public DimensionEditorModule()
    {
        super(HoloEditorCapability.DIMENSION, "Dimension Editor");
    }
}
