package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.holo.object.HoloAnchor;

/** Donnees de dessin d'un objet holographique. Objet reutilise : ne pas le garder au-dela de l'appel. */
public final class HoloDrawContext
{
    private float partialTick;
    private HoloAnchor.Family family;
    private boolean ownerView;

    HoloDrawContext() {}

    void set(float partialTick, HoloAnchor.Family family, boolean ownerView)
    {
        this.partialTick = partialTick;
        this.family = family;
        this.ownerView = ownerView;
    }

    public float partialTick() { return partialTick; }

    /** Famille de renderers qui dessine l'objet pour cette frame. */
    public HoloAnchor.Family family() { return family; }

    /** Vrai si le joueur local est le proprietaire de l'objet. */
    public boolean ownerView() { return ownerView; }
}
