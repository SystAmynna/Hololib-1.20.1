package com.amynna.hololib.modules.monde.areadisplayer;

import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;

/**
 * Pave droit de l'Area Displayer : volume dans lequel le renderer dessine le terrain de la zone, les points suivis et
 * leurs trainees. Ancrage {@code World} depuis le marker, {@code PUBLIC}, dessine pour tous.
 * <p>
 * Repere local : pave centre a l'origine, de demi-aretes {@link #halfX()}, {@link #halfY()}, {@link #halfZ()} =
 * proportions de la grille de voxels de la zone (l'axe le plus long vaut 1 ; sans zone, cube de demi-arete 1). Place
 * par le transform de l'objet : par defaut a {@value #DEFAULT_HEIGHT} blocs au-dessus du marker, echelle
 * {@value #DEFAULT_SCALE}. Editable par tout operateur equipe du {@code world_editor} (translation, rotation,
 * echelle) ; l'echelle reste uniforme (le module la ramene a la moyenne), les proportions suivent la zone.
 * <p>
 * Aucune interaction : le regard le traverse (le nom d'un point regarde est un affichage du renderer).
 */
public final class AreaDisplayerCube extends HoloObject implements HoloEditTranslation, HoloEditRotation, HoloEditScale
{
    /** Hauteur par defaut du centre du pave au-dessus du marker, en blocs. */
    public static final float DEFAULT_HEIGHT = 2f;
    /** Echelle par defaut (demi-arete du plus grand axe, en blocs). */
    public static final float DEFAULT_SCALE = 1.5f;

    /** @param key cle de l'objet dans son module ({@code cube}) */
    public AreaDisplayerCube(String key)
    {
        super(key, new HoloAnchor.World(), HoloTransform.translation(0f, DEFAULT_HEIGHT, 0f).setScale(DEFAULT_SCALE));
        setVisibility(HoloVisibility.PUBLIC);
    }

    /** Module qui porte le pave, {@code null} hors d'un Area Displayer. */
    public AreaDisplayerModule displayer() { return module() instanceof AreaDisplayerModule m ? m : null; }

    /** Demi-arete locale sur X (proportion de la zone). */
    public float halfX() { return half(0); }

    /** Demi-arete locale sur Y. */
    public float halfY() { return half(1); }

    /** Demi-arete locale sur Z. */
    public float halfZ() { return half(2); }

    private float half(int axis)
    {
        AreaDisplayerModule displayer = displayer();
        AreaGrid grid = displayer != null ? displayer.grid() : null;
        if (grid == null) return 1f;
        int size = axis == 0 ? grid.sizeX() : axis == 1 ? grid.sizeY() : grid.sizeZ();
        return (float) size / grid.maxSize();
    }

    /** Reserve aux operateurs. */
    @Override
    public boolean opRequired() { return true; }

    /** Tout operateur peut l'editer, pas seulement le createur du module. */
    @Override
    public boolean ownRequired() { return false; }

    /** Demi-diagonale du pave. */
    @Override
    public float boundingRadius()
    {
        float x = halfX();
        float y = halfY();
        float z = halfZ();
        return (float) Math.sqrt(x * x + y * y + z * z);
    }
}
