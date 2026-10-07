package com.amynna.hololib.client.render.modules.camera.editor;

import com.amynna.hololib.client.render.holo.HoloObjectRenderers;
import com.amynna.hololib.modules.camera.editor.EditorReference;
import com.amynna.hololib.modules.camera.editor.EditorWindow;

/** Enregistrement des renderers du module, appele par {@code ModuleRenderers} sur le fil de rendu. */
public final class EditorRenderers
{
    private EditorRenderers() {}

    public static void register()
    {
        HoloObjectRenderers.register(EditorWindow.class, new EditorWindowRenderer());
        HoloObjectRenderers.register(EditorReference.class, new EditorReferenceRenderer());
    }
}
