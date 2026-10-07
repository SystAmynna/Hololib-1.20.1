package com.amynna.hololib.modules.camera.editor;

import com.amynna.hololib.holo.edit.HoloEditorCapability;

/**
 * Other Editor (operateurs) : leve la propriete ({@code ownRequired}) pour le World et le Dimension Editor. N'ouvre
 * pas la memoire des autres joueurs.
 */
public final class OtherEditorModule extends EditorCapabilityModule
{
    public OtherEditorModule()
    {
        super(HoloEditorCapability.OTHER, "Other Editor");
    }
}
