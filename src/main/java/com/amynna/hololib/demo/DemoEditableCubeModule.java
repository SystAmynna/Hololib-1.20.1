package com.amynna.hololib.demo;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;

/**
 * Demonstration du World Editor : un cube immobile au-dessus du marker, editable avec tous ses outils (deplacement,
 * rotation, echelle, affichage, portee) par le joueur qui a installe le module, ou par un operateur equipe de l'Other
 * Editor.
 */
public final class DemoEditableCubeModule extends HoloModule
{
    @Override
    public void onLoad()
    {
        DemoCube cube = addObject(new DemoCube("cube", new HoloAnchor.World(), HoloTransform.translation(0f, 1.5f, 0f),
                0.6f, 0xA0FFC040));
        // Immobile : seules les modifications de l'edition le font bouger.
        cube.setAnimated(false);
        cube.setVisibility(HoloVisibility.PUBLIC);
    }
}
