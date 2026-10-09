package com.amynna.hololib.modules.monde.spaceconfigurator;

import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;

/**
 * Cube du Space Configurator : volume dans lequel le renderer dessine la carte galactique (mode galaxie) ou le
 * systeme edite (mode systeme). Ancrage {@code World} depuis le marker, {@code PUBLIC}, mais affiche seulement chez un
 * operateur ({@link #shown()}, {@code docs/space/decisions.md} C9).
 * <p>
 * Repere local : cube {@code [-1, 1]^3} ({@link #HALF_SIZE}), place par le transform de l'objet
 * ({@code docs/space/04-mathematiques.md} par. 7). Par defaut a {@value #DEFAULT_HEIGHT} blocs au-dessus du marker,
 * echelle {@value #DEFAULT_SCALE} (cube de 3 blocs). Editable par tout operateur equipe du {@code world_editor}
 * (translation, rotation, echelle ; pas besoin d'etre le createur du module). L'echelle reste uniforme : le module la
 * ramene a la moyenne apres une edition qui l'etire (C11).
 */
public final class SpaceMapCube extends HoloObject implements HoloEditTranslation, HoloEditRotation, HoloEditScale
{
    /** Demi-arete du cube dans son repere local. */
    public static final float HALF_SIZE = 1f;
    /** Hauteur par defaut du centre du cube au-dessus du marker, en blocs. */
    public static final float DEFAULT_HEIGHT = 2f;
    /** Echelle par defaut (demi-arete en blocs). */
    public static final float DEFAULT_SCALE = 1.5f;
    /** Rayon de la sphere englobante locale : demi-diagonale du cube. */
    private static final float BOUNDING_RADIUS = (float) Math.sqrt(3.0) * HALF_SIZE;

    SpaceMapCube(String key)
    {
        super(key, new HoloAnchor.World(), HoloTransform.translation(0f, DEFAULT_HEIGHT, 0f).setScale(DEFAULT_SCALE));
        setVisibility(HoloVisibility.PUBLIC);
    }

    /** Module qui porte le cube (etat et copies lues par le renderer). */
    public SpaceConfiguratorModule configurator() { return (SpaceConfiguratorModule) module(); }

    /** Reserve aux operateurs. */
    @Override
    public boolean opRequired() { return true; }

    /** Tout operateur peut l'editer, pas seulement le createur du module. */
    @Override
    public boolean ownRequired() { return false; }

    @Override
    public float boundingRadius() { return BOUNDING_RADIUS; }

    /** Dessine seulement chez un operateur (backend client du module). */
    @Override
    public boolean shown() { return SpaceConfiguratorModule.localOp(); }
}
