package com.amynna.hololib.modules.monde.triggerarea;

import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.theme.HoloThemeSource;
import com.amynna.hololib.holo.theme.HoloThemed;

/**
 * Zone de declenchement de la Trigger Area : pave droit {@code World} depuis le marker, {@code PUBLIC}. Repere local :
 * cube centre a l'origine de demi-arete 1 ; le transform le place, l'oriente et le met a l'echelle (l'echelle de chaque
 * axe est la <b>demi-arete</b> reelle de la zone, en blocs, et peut etre differente d'un axe a l'autre). Le serveur
 * teste les entites contre ce meme pave ({@link TriggerAreaModule}).
 * <p>
 * Editable par tout operateur equipe du {@code world_editor} (translation, rotation, echelle). Dessinee seulement si
 * l'option « visible » du module est active ({@link #shown()}) ; une session d'edition la montre toujours, pour qu'une
 * zone invisible reste editable. Aucune interaction : le regard la traverse.
 */
public final class TriggerAreaZone extends HoloObject implements HoloEditTranslation, HoloEditRotation, HoloEditScale,
        HoloThemed
{
    /** Hauteur par defaut du centre de la zone au-dessus du marker, en blocs. */
    public static final float DEFAULT_HEIGHT = 1f;
    /** Demi-arete par defaut de chaque axe, en blocs (zone de 4 blocs de cote). */
    public static final float DEFAULT_SCALE = 2f;
    /** Demi-arete maximale prise en compte par la detection et le dessin, en blocs. */
    public static final float MAX_HALF = 128f;

    /** @param key cle de l'objet dans son module ({@code zone}) */
    public TriggerAreaZone(String key)
    {
        super(key, new HoloAnchor.World(), HoloTransform.translation(0f, DEFAULT_HEIGHT, 0f).setScale(DEFAULT_SCALE));
        setVisibility(HoloVisibility.PUBLIC);
    }

    /** Module qui porte la zone, {@code null} hors d'une Trigger Area. */
    public TriggerAreaModule area() { return module() instanceof TriggerAreaModule m ? m : null; }

    /** Dessinee seulement si l'option « visible » est active (etat synchronise du module). */
    @Override
    public boolean shown()
    {
        TriggerAreaModule area = area();
        return area != null && area.visible();
    }

    /** Demi-arete effective d'un axe : valeur absolue de l'echelle, bornee a {@value #MAX_HALF}. */
    public static float half(float scale)
    {
        float abs = Math.abs(scale);
        return Float.isFinite(abs) ? Math.min(abs, MAX_HALF) : MAX_HALF;
    }

    /** Reserve aux operateurs. */
    @Override
    public boolean opRequired() { return true; }

    /** Tout operateur peut l'editer, pas seulement le createur du module. */
    @Override
    public boolean ownRequired() { return false; }

    /** Demi-diagonale du cube local (le transform y applique l'echelle). */
    @Override
    public float boundingRadius() { return (float) Math.sqrt(3.0); }

    /** Theme du serveur (objet partage). */
    @Override
    public HoloThemeSource themeSource() { return HoloThemeSource.SERVER; }
}
