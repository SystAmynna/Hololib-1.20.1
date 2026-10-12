package com.amynna.hololib.modules.monde.spaceconfigurator;

import com.amynna.hololib.holo.theme.HoloThemed;
import com.amynna.hololib.holo.theme.HoloThemeSource;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;

/**
 * Poignee de translation de l'etoile selectionnee : <b>un seul</b> objet editable pour toute la carte
 * ({@code docs/space/decisions.md} C4), pose par le Configurator sur l'etoile du systeme selectionne dans la Space Map
 * du meme marker, dans le repere du cube de la Map (T42). Ancrage {@code World} depuis le marker (meme repere que le
 * cube), {@code PUBLIC}, affichee seulement chez un operateur ({@link #shown()}), editable par tout operateur equipe du
 * {@code world_editor} (outil translation seulement).
 * <p>
 * N'existe que si la Map montre sa carte galactique avec un systeme selectionne : le module la cree et la retire des
 * deux cotes (cle {@link #KEY}). Pas de callback d'edition : le module compare chaque tick serveur sa position a la
 * derniere qu'il a ecrite et en deduit le deplacement du systeme ({@code docs/space/04-mathematiques.md} par. 7).
 * Rotation et echelle restent neutres (le renderer dessine un petit marqueur de taille fixe).
 */
public final class SpaceMapHandle extends HoloObject implements HoloEditTranslation, HoloThemed
{
    /** Cle de l'objet dans le module. */
    public static final String KEY = "handle";
    /** Rayon de la sphere englobante (marqueur), en blocs. */
    public static final float RADIUS = 0.08f;

    SpaceMapHandle()
    {
        super(KEY, new HoloAnchor.World(), new HoloTransform());
        setVisibility(HoloVisibility.PUBLIC);
    }

    /** Module qui porte la poignee. */
    public SpaceConfiguratorModule configurator() { return (SpaceConfiguratorModule) module(); }

    /** Reserve aux operateurs. */
    @Override
    public boolean opRequired() { return true; }

    /** Tout operateur peut la deplacer, pas seulement le createur du module. */
    @Override
    public boolean ownRequired() { return false; }

    @Override
    public float boundingRadius() { return RADIUS; }

    /** Dessinee seulement chez un operateur (backend client du module). */
    @Override
    public boolean shown() { return SpaceConfiguratorModule.localOp(); }

    /** Theme du serveur (objet partage), jeu staff (reserve aux ops). */
    @Override
    public HoloThemeSource themeSource() { return HoloThemeSource.SERVER; }

    @Override
    public boolean staffTheme() { return true; }
}
