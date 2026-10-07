package com.amynna.hololib.demo;

import com.amynna.hololib.holo.edit.HoloRelativeToOpen;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;

/**
 * Fenetre de demonstration replacee a chaque ouverture du mode focus, devant le regard ({@link HoloRelativeToOpen} :
 * la position relative est le transform initial, editable dans l'editor).
 */
public final class DemoRelativeWindow extends DemoEditableWindow implements HoloRelativeToOpen
{
    public DemoRelativeWindow(String key, HoloTransform transform, int width, int height, float pixelScale)
    {
        super(key, new HoloAnchor.Local(false), transform, width, height, pixelScale);
    }
}
