package com.amynna.hololib.modules.dimension.planet1;

import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditVisibility;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;

/**
 * Anneau carre plat (plan local {@code XZ}) centre sur son origine locale, entre deux demi-cotes. Statique. Editable
 * en jeu (Dimension Editor) par le createur du module, ou avec l'Other Editor, sauf sa position : son centre est
 * toujours celui de la planete ({@code Planet1Module}).
 */
public final class Planet1Ring extends HoloObject implements HoloEditRotation, HoloEditScale, HoloEditVisibility
{
    private final float inner;
    private final float outer;

    /**
     * @param inner demi-cote interieur, en blocs
     * @param outer demi-cote exterieur, en blocs
     */
    public Planet1Ring(String key, HoloAnchor anchor, HoloTransform transform, float inner, float outer)
    {
        super(key, anchor, transform);
        this.inner = inner;
        this.outer = outer;
    }

    public float inner() { return inner; }

    public float outer() { return outer; }

    @Override
    public float boundingRadius() { return outer * 1.42f; }
}
