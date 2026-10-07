package com.amynna.hololib.demo;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;

/**
 * Demonstration de memoire de dimension : une fenetre dans le ciel, visible de toute la dimension, editable par les
 * operateurs (sans proprietaire : {@code ownRequired = false}, {@code opRequired = true}).
 */
public final class DemoDimensionModule extends HoloModule
{
    /** Distance des astres vanilla ({@code SkyRenderContext.SKY_RADIUS}), sous le plan lointain. */
    private static final float SKY_DISTANCE = 100f;

    @Override
    public void onLoad()
    {
        DemoEditableWindow window = addObject(DemoWindows.labeled("sky", new HoloAnchor.Sky(false),
                new HoloTransform().setLookingAtOrigin(0f, -35f, SKY_DISTANCE).setScale(20f), 1f / 100f,
                "Holo Lib", "Memoire de dimension", 0xFFFFC040));
        window.setEditRules(false, true);
        window.setVisibility(HoloVisibility.PUBLIC);
    }
}
