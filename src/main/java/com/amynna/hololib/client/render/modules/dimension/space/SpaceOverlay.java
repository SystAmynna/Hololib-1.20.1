package com.amynna.hololib.client.render.modules.dimension.space;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.dimension.space.math.SpaceSkyFrame;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;

/**
 * Regles de dessin partagees par les surcouches des modules Space du joueur local (T33) : la surcouche du ciel de
 * Space Knowledge ({@code SpaceKnowledgeSkyRenderer}), le lien et l'anneau du referentiel de Space Pilot
 * ({@code SpaceReferenceLinkRenderer}) et le saut ({@code SpaceJumpRenderer}). Les formules de l'anneau d'un corps
 * ({@link #bodyRing}) et de sa couleur ({@link #kindColor}) ne sont ecrites qu'ici : l'anneau du referentiel dessine
 * par Space Pilot se confond avec celui de Space Knowledge quand les deux modules sont actifs.
 * <p>
 * Tailles constantes a l'ecran : un <b>pixel de reference</b> vaut un pixel de l'ecran jusqu'a
 * {@value #REFERENCE_HEIGHT} pixels de haut, puis grandit avec la fenetre ; sa tangente au centre de l'ecran
 * ({@link #pixelTan}) depend de l'echelle verticale {@code m11} de la projection du renderer (FOV et longue-vue
 * comptent). Methodes sans allocation, fil de rendu. Conception : {@code docs/space/04-mathematiques.md} par. 9.
 */
public final class SpaceOverlay
{
    /** Hauteur d'ecran jusqu'a laquelle un pixel de reference vaut un pixel (au-dela : proportionnel). */
    public static final double REFERENCE_HEIGHT = 1080.0;
    /** Echelle verticale de la projection a defaut ({@code 1 / tan(35 deg)}, FOV 70). */
    public static final double DEFAULT_PROJECTION_SCALE = 1.0 / Math.tan(Math.toRadians(35.0));

    // ---------------------------------------------------------------- anneau d'un corps (C46)

    /** Rayon apparent de l'anneau : {@code max(alpha_corps x 1,6, alpha_min)}. */
    public static final double BODY_RING_FACTOR = 1.6;
    /** {@code alpha_min} : rayon minimal de l'anneau, en pixels de reference. */
    public static final double BODY_RING_MIN_PX = 10.0;
    /** Epaisseur de l'anneau d'un corps, en pixels de reference. */
    public static final double BODY_RING_THICKNESS_PX = 1.5;
    /** Plafond du rayon de l'anneau (radians) : un corps qui remplit le ciel garde un anneau autour de lui. */
    public static final double MAX_RING_ANGLE = Math.toRadians(100.0);
    /** Opacite de l'anneau d'un corps (avant le fondu de reperage). */
    public static final float BODY_RING_ALPHA = 0.85f;
    /** Segments d'un anneau de corps. */
    public static final int BODY_RING_SEGMENTS = 64;

    // ---------------------------------------------------------------- couleurs (RGB, D18)

    /** Planetes et objets mineurs : bleu. Anneau, orbite, nom, lien. */
    public static final int PLANET_COLOR = 0x4FA8FF;
    /** Etoiles du systeme courant (principale et compagnes) : jaune. */
    public static final int STAR_COLOR = 0xFFD84A;
    /** Lunes : gris clair. */
    public static final int MOON_COLOR = 0xB8B8B8;
    /** Etoiles de fond (autres systemes) : double cercle et nom blancs. */
    public static final int DISTANT_STAR_COLOR = 0xFFFFFF;
    /**
     * Cible du vaisseau (T43, D33, C125) : anneau et fleche de Space Pilot ; meme vert que le libelle « DECIBLER » de
     * la Space Map ({@code SpaceMapWindow.TARGET_COLOR}).
     */
    public static final int TARGET_COLOR = 0x40FF40;

    private SpaceOverlay() {}

    /**
     * Tangente d'un pixel de reference au centre de l'ecran pour une projection d'echelle verticale {@code m11}
     * (valeur absolue prise ; defaut FOV 70 si elle n'est pas utilisable) et la hauteur actuelle de la fenetre.
     */
    public static double pixelTan(double m11)
    {
        double scale = Math.abs(m11);
        if (!Double.isFinite(scale) || !(scale > 0.0)) scale = DEFAULT_PROJECTION_SCALE;
        double height = Math.max(1, Minecraft.getInstance().getWindow().getHeight());
        return 2.0 * Math.max(1.0, height / REFERENCE_HEIGHT) / (scale * height);
    }

    /** Rayon minimal de l'anneau d'un corps (radians) : {@value #BODY_RING_MIN_PX} pixels de reference. */
    public static double bodyRingMin(double pixelTan) { return Math.atan(BODY_RING_MIN_PX * pixelTan); }

    /** Demi-epaisseur de l'anneau d'un corps (radians). */
    public static double bodyRingHalf(double pixelTan) { return Math.atan(0.5 * BODY_RING_THICKNESS_PX * pixelTan); }

    /**
     * Rayon de l'anneau d'un corps (radians, milieu du trait) : {@code max(alpha x 1,6, alpha_min)}, plafonne a
     * {@link #MAX_RING_ANGLE}.
     *
     * @param distance distance du monde au centre du corps, km
     * @param minAngle {@link #bodyRingMin} de la frame
     */
    public static double bodyRing(SpaceBody body, double distance, double minAngle)
    {
        double alpha = SpaceSkyFrame.angularRadius(body.radius(), distance);
        return Math.min(MAX_RING_ANGLE, Math.max(alpha * BODY_RING_FACTOR, minAngle));
    }

    /**
     * Couleur de reperage d'un corps du systeme courant selon son type (anneau, orbite, nom, lien) : etoile jaune, lune
     * grise, planete et objet mineur bleus.
     */
    public static int kindColor(SpaceBodyKind kind)
    {
        return switch (kind) {
            case STAR -> STAR_COLOR;
            case MOON -> MOON_COLOR;
            case PLANET, MINOR -> PLANET_COLOR;
        };
    }

    public static float red(int argb) { return (argb >> 16 & 0xFF) / 255f; }

    public static float green(int argb) { return (argb >> 8 & 0xFF) / 255f; }

    public static float blue(int argb) { return (argb & 0xFF) / 255f; }

    /** {@code RenderType} des surcouches ({@link OverlayType#OVERLAY_QUADS}). */
    public static RenderType quads() { return OverlayType.OVERLAY_QUADS; }

    /**
     * {@code RenderType} local (sous-classe pour acceder aux etats proteges de {@code RenderStateShard} ; jamais
     * instanciee) : quads {@code POSITION_COLOR_LIGHTMAP} translucides double face, <b>sans ecriture de profondeur</b>
     * (test de profondeur garde) et sans tri a l'envoi (rien ne s'occulte). Deplace de {@code SpacePilotSkyRenderer}
     * en T33.
     */
    private static final class OverlayType extends RenderType
    {
        static final RenderType OVERLAY_QUADS = create(HoloLib.MODID + ":space_overlay_quads",
                DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_LIGHTMAP_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private OverlayType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                            boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear)
        {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
