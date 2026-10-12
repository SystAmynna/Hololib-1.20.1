package com.amynna.hololib.demo;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.edit.HoloEditVisibility;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import net.minecraft.util.Mth;

/**
 * Cube holographique qui tourne sur lui-meme. L'angle est anime dans {@link #tick()} (cote client) ; le renderer
 * interpole entre l'angle precedent et l'angle courant ; l'animation peut etre coupee ({@link #setAnimated}). Editable
 * en jeu (deplacement, rotation, echelle, affichage) :
 * dans le monde, par les gizmos.
 */
public final class DemoCube extends HoloObject implements HoloEditTranslation, HoloEditRotation, HoloEditScale,
        HoloEditVisibility
{
    /** Vitesse de rotation, en degres par tick. */
    private static final float SPEED = 4f;

    private final float size;
    private final HoloColor color;
    private float spin;
    private float spinO;
    private boolean ownRequired = true;
    private boolean opRequired;
    private boolean animated = true;

    /**
     * @param size  arete, en blocs
     * @param color couleur (role du theme)
     */
    public DemoCube(String key, HoloAnchor anchor, HoloTransform transform, float size, HoloColor color)
    {
        super(key, anchor, transform);
        this.size = size;
        this.color = color;
    }

    /** Faux : le cube reste immobile (pour voir les modifications de l'edition). Vrai par defaut. */
    public DemoCube setAnimated(boolean animated)
    {
        this.animated = animated;
        return this;
    }

    /** Regles d'edition (voir {@code HoloEditable}), a fixer de la meme facon des deux cotes. */
    public DemoCube setEditRules(boolean ownRequired, boolean opRequired)
    {
        this.ownRequired = ownRequired;
        this.opRequired = opRequired;
        return this;
    }

    @Override
    public boolean ownRequired() { return ownRequired; }

    @Override
    public boolean opRequired() { return opRequired; }

    public float size() { return size; }

    public HoloColor color() { return color; }

    /** Angle de rotation interpole, en degres. */
    public float spin(float partialTick) { return Mth.lerp(partialTick, spinO, spin); }

    @Override
    public float boundingRadius() { return size * 0.87f; }

    @Override
    public void tick()
    {
        if (!animated) return;
        spinO = spin;
        spin += SPEED;
        if (spin >= 360f) {
            spin -= 360f;
            spinO -= 360f;
        }
    }
}
