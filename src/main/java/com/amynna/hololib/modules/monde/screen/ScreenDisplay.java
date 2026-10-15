package com.amynna.hololib.modules.monde.screen;

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
 * Ecran du Screen : rectangle plat ou le renderer dessine l'image de la camera, un cadre et, sans image, l'etat du
 * signal. Ancrage {@code World} depuis le marker, {@code PUBLIC}, dessine pour tous.
 * <p>
 * Repere local : rectangle centre a l'origine, face vers {@code +Z}, hauteur 1 et largeur = aspect de la resolution
 * (dynamique : suit l'etat du module). Place par le transform : par defaut a {@value #DEFAULT_HEIGHT} blocs au-dessus
 * du marker, echelle {@value #DEFAULT_SCALE}. Editable par tout operateur equipe du {@code world_editor} (translation,
 * rotation, echelle) ; l'echelle reste uniforme (le module la ramene a la moyenne de X et Y).
 * <p>
 * Pas un {@code HoloWindow} : la taille d'une fenetre est fixe, celle de l'ecran suit la resolution. Aucune
 * interaction : le regard le traverse.
 */
public final class ScreenDisplay extends HoloObject implements HoloEditTranslation, HoloEditRotation, HoloEditScale,
        HoloThemed
{
    /** Hauteur par defaut du centre de l'ecran au-dessus du marker, en blocs. */
    public static final float DEFAULT_HEIGHT = 2.5f;
    /** Echelle par defaut (hauteur de l'ecran, en blocs). */
    public static final float DEFAULT_SCALE = 2f;

    /** @param key cle de l'objet dans son module ({@code display}) */
    public ScreenDisplay(String key)
    {
        super(key, new HoloAnchor.World(), HoloTransform.translation(0f, DEFAULT_HEIGHT, 0f).setScale(DEFAULT_SCALE));
        setVisibility(HoloVisibility.PUBLIC);
    }

    /** Module qui porte l'ecran, {@code null} hors d'un Screen. */
    public ScreenModule screen() { return module() instanceof ScreenModule m ? m : null; }

    /** Largeur / hauteur de l'image (resolution du module, 16:9 sans module). */
    public float aspect()
    {
        ScreenModule screen = screen();
        return screen != null ? (float) screen.width() / screen.height() : 16f / 9f;
    }

    @Override
    public float halfWidth() { return 0.5f * aspect(); }

    @Override
    public float halfHeight() { return 0.5f; }

    @Override
    public float boundingRadius()
    {
        float w = halfWidth();
        return (float) Math.sqrt(w * w + 0.25f);
    }

    @Override
    public boolean flat() { return true; }

    /** Reserve aux operateurs. */
    @Override
    public boolean opRequired() { return true; }

    /** Tout operateur peut l'editer, pas seulement le createur du module. */
    @Override
    public boolean ownRequired() { return false; }

    /** Theme du serveur (objet partage). */
    @Override
    public HoloThemeSource themeSource() { return HoloThemeSource.SERVER; }
}
