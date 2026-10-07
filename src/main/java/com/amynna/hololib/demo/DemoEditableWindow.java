package com.amynna.hololib.demo;

import com.amynna.hololib.holo.edit.HoloEditFixOrDynamic;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.edit.HoloEditVisibility;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.window.HoloWindow;

/**
 * Fenetre de demonstration editable en jeu avec tous les outils (translation, rotation, echelle, affichage, fixe /
 * dynamique). Les regles d'edition sont fixees a la construction, identiques des deux cotes.
 */
public class DemoEditableWindow extends HoloWindow implements HoloEditTranslation, HoloEditRotation, HoloEditScale,
        HoloEditVisibility, HoloEditFixOrDynamic
{
    private boolean ownRequired = true;
    private boolean opRequired;

    public DemoEditableWindow(String key, HoloAnchor anchor, HoloTransform transform, int width, int height,
                              float pixelScale)
    {
        super(key, anchor, transform, width, height, pixelScale);
    }

    /** Regles d'edition (voir {@code HoloEditable}). */
    public DemoEditableWindow setEditRules(boolean ownRequired, boolean opRequired)
    {
        this.ownRequired = ownRequired;
        this.opRequired = opRequired;
        return this;
    }

    @Override
    public boolean ownRequired() { return ownRequired; }

    @Override
    public boolean opRequired() { return opRequired; }
}
