package com.amynna.hololib.modules.monde.spacemap;

import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.control.HoloScrollable;

/**
 * Cube de la carte (T37 : deplace du Configurator dans la Space Map) : volume dans lequel le renderer dessine la carte
 * galactique ou un systeme, selon la {@link SpaceMapView} du module qui le porte ({@link #view()}). Ancrage
 * {@code World} depuis le marker, {@code PUBLIC} ; dessine si la vue l'autorise ({@link #shown()} : tous pour la Space
 * Map). Depuis T42, il porte aussi les poignees du Space Configurator du meme marker (repere du cube et de la vue).
 * <p>
 * Repere local : cube {@code [-1, 1]^3} ({@link #HALF_SIZE}), place par le transform de l'objet
 * ({@code docs/space/04-mathematiques.md} par. 7). Par defaut a {@value #DEFAULT_HEIGHT} blocs au-dessus du marker,
 * echelle {@value #DEFAULT_SCALE} (cube de 3 blocs) : a cette echelle, etoiles, textes et traits ont leur taille de
 * reference, et ils suivent l'echelle du cube (la carte grandit avec lui). Editable par tout operateur equipe du
 * {@code world_editor} (translation, rotation, echelle ; pas besoin d'etre le createur du module). L'echelle reste
 * uniforme : le module la ramene a la moyenne apres une edition qui l'etire (C11).
 * <p>
 * <b>Molette</b> : si la vue est {@linkplain SpaceMapView#zoomable() zoomable} (Space Map), le cube prend la molette
 * quand le regard traverse son plan median {@code z = 0} ({@link #interactibleAt}) et zoome la vue vers son centre ;
 * son acces ({@code EVERYONE}, pose par la Space Map) decide qui peut le faire.
 */
public final class SpaceMapCube extends HoloObject
        implements HoloEditTranslation, HoloEditRotation, HoloEditScale, HoloScrollable
{
    /** Demi-arete du cube dans son repere local. */
    public static final float HALF_SIZE = 1f;
    /** Hauteur par defaut du centre du cube au-dessus du marker, en blocs. */
    public static final float DEFAULT_HEIGHT = 2f;
    /** Echelle par defaut (demi-arete en blocs) ; echelle de reference des tailles dessinees. */
    public static final float DEFAULT_SCALE = 1.5f;
    /**
     * Couleur du monde (T25) : vaisseau de la vue systeme, anneau du systeme courant sur la carte galactique, systeme
     * courant dans les listes des fenetres (distincte du jaune de la selection).
     */
    public static final int WORLD_COLOR = 0xFF50F070;
    /** Rayon de la sphere englobante locale : demi-diagonale du cube. */
    private static final float BOUNDING_RADIUS = (float) Math.sqrt(3.0) * HALF_SIZE;

    /** @param key cle de l'objet dans son module ({@code cube}) */
    public SpaceMapCube(String key)
    {
        super(key, new HoloAnchor.World(), HoloTransform.translation(0f, DEFAULT_HEIGHT, 0f).setScale(DEFAULT_SCALE));
        setVisibility(HoloVisibility.PUBLIC);
    }

    /** Vue du module qui porte le cube (etat et copies lues par le renderer), {@code null} hors d'un tel module. */
    public SpaceMapView view() { return module() instanceof SpaceMapView view ? view : null; }

    /** Reserve aux operateurs. */
    @Override
    public boolean opRequired() { return true; }

    /** Tout operateur peut l'editer, pas seulement le createur du module. */
    @Override
    public boolean ownRequired() { return false; }

    @Override
    public float boundingRadius() { return BOUNDING_RADIUS; }

    /** Dessine selon la vue (Space Map : tous). */
    @Override
    public boolean shown()
    {
        SpaceMapView view = view();
        return view != null && view.cubeShown();
    }

    /** Le cube lui-meme sur son plan median, si la vue est zoomable ; sinon le regard le traverse. */
    @Override
    public HoloInteractible interactibleAt(float localX, float localY)
    {
        SpaceMapView view = view();
        if (view == null || !view.zoomable()) return null;
        return Math.abs(localX) <= HALF_SIZE && Math.abs(localY) <= HALF_SIZE ? this : null;
    }

    /** Molette : zoom de la vue vers son centre (un pas par cran). */
    @Override
    public void onScrolled(HoloInteraction ctx, int steps)
    {
        SpaceMapView view = view();
        if (view != null && view.zoomable()) view.requestZoom(this, steps);
    }
}
