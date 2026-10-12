package com.amynna.hololib.demo;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;

/**
 * Demonstration de memoire de position : un cube qui tourne un bloc au-dessus du marker. L'ancrage monde a pour origine
 * le marker hote : le cube le suit sans que le module ait a connaitre sa position. Editable par les operateurs.
 */
public final class DemoWorldPosModule extends HoloModule
{
    @Override
    public void onLoad()
    {
        DemoCube cube = addObject(new DemoCube("cube", new HoloAnchor.World(), HoloTransform.translation(0f, 1f, 0f),
                0.5f, HoloColor.primary(0xA0)));
        cube.setEditRules(false, true);
        cube.setVisibility(HoloVisibility.PUBLIC);
    }
}
