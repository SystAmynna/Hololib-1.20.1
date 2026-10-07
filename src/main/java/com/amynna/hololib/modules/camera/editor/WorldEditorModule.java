package com.amynna.hololib.modules.camera.editor;

import com.amynna.hololib.holo.edit.HoloEditorCapability;

/**
 * World Editor : edition des objets des memoires de position (markers) dont le joueur est proprietaire (createur du
 * module), ou de tous avec l'Other Editor.
 */
public final class WorldEditorModule extends EditorCapabilityModule
{
    public WorldEditorModule()
    {
        super(HoloEditorCapability.WORLD, "World Editor");
    }
}
