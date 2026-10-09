package com.amynna.hololib.modules.monde.spaceconfigurator;

import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;

/**
 * Poignee d'orbite du mode systeme ({@code docs/space/decisions.md} C5) : <b>un seul</b> objet editable, l'orbite du
 * corps selectionne. Ancrage {@code World} depuis le marker (meme repere que le cube), {@code PUBLIC}, affichee
 * seulement chez un operateur ({@link #shown()}), editable par tout operateur equipe du {@code world_editor} avec les
 * outils Rotation et Echelle (pas de translation : elle est posee par le module).
 * <p>
 * Placement ecrit par le module ({@code docs/space/04-mathematiques.md} par. 7) : translation = parent affiche du
 * corps (au temps fige {@code t_edit}), rotation = {@code R_cube . R_orbite}, echelle
 * {@code (a_aff . sigma, a_aff . sigma, b_aff . sigma)} (demi-axes affiches, loupe des lunes comprise). Le renderer
 * dessine dans le repere local la courbe {@code (cos t - e, 0, sin t)} : avec l'echelle, l'ellipse de l'orbite, parent
 * au foyer. L'excentricite se lit dans le rapport {@code scaleZ / scaleX} ({@link #eccentricity}).
 * <p>
 * N'existe que si un corps a orbite est selectionne en mode systeme : le module la cree et la retire des deux cotes
 * (cle {@link #KEY}). Pas de callback d'edition : le module compare chaque tick serveur sa rotation et son echelle aux
 * dernieres qu'il a ecrites et en deduit la nouvelle orbite ({@code updateBody}). {@code scaleY} n'a pas de sens : il
 * est reecrit a {@code scaleX}.
 */
public final class SpaceOrbitHandle extends HoloObject implements HoloEditRotation, HoloEditScale
{
    /** Cle de l'objet dans le module. */
    public static final String KEY = "orbit";

    SpaceOrbitHandle()
    {
        super(KEY, new HoloAnchor.World(), new HoloTransform());
        setVisibility(HoloVisibility.PUBLIC);
    }

    /** Module qui porte la poignee. */
    public SpaceConfiguratorModule configurator() { return (SpaceConfiguratorModule) module(); }

    /**
     * Excentricite lue dans l'echelle d'un transform de poignee : {@code e = sqrt(1 - (scaleZ / scaleX)^2)}, bornee a
     * {@code [0, 0,95]}, {@code 0} si {@code scaleZ >= scaleX} ({@code docs/space/04-mathematiques.md} par. 7).
     */
    public static double eccentricity(HoloTransform t)
    {
        double a = t.scaleX();
        double b = t.scaleZ();
        if (!(a > 0.0) || b >= a) return 0.0;
        double ratio = b / a;
        return Math.min(SpaceLimits.MAX_ECCENTRICITY, Math.sqrt(1.0 - ratio * ratio));
    }

    /** Reserve aux operateurs. */
    @Override
    public boolean opRequired() { return true; }

    /** Tout operateur peut l'editer, pas seulement le createur du module. */
    @Override
    public boolean ownRequired() { return false; }

    /** Distance maximale foyer -> ellipse dans le repere local : {@code 1 + e} (fois l'echelle {@code scaleX}). */
    @Override
    public float boundingRadius() { return 1f + (float) eccentricity(transform()); }

    /** Dessinee seulement chez un operateur (backend client du module). */
    @Override
    public boolean shown() { return SpaceConfiguratorModule.localOp(); }
}
