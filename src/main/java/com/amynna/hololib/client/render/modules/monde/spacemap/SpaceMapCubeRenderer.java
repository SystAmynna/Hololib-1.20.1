package com.amynna.hololib.client.render.modules.monde.spacemap;

import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.modules.dimension.space.SpaceBodyTextureCache;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.holo.edit.HoloEditMath;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.math.SpacePose;
import com.amynna.hololib.modules.dimension.space.math.SpaceSpin;
import com.amynna.hololib.modules.dimension.space.math.SpaceUnits;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import com.amynna.hololib.modules.dimension.space.model.SpaceOrbit;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem.SystemHeader;
import com.amynna.hololib.modules.monde.spacemap.SpaceCoordinates;
import com.amynna.hololib.modules.monde.spacemap.SpaceMapCube;
import com.amynna.hololib.modules.monde.spacemap.SpaceMapView;
import com.amynna.hololib.modules.monde.spacemap.SpaceSystemScale;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renderer du {@link SpaceMapCube} (Space Map, T37) : dessine
 * tout le contenu du cube dans son repere local {@code [-1, 1]^3} (pile placee par le pont monde : translation,
 * orientation et echelle du transform deja appliquees), d'apres la {@link SpaceMapView} du module qui le porte.
 * <p>
 * <b>Carte ajustee au cube</b> (T37) : positions en unites du cube, et toutes les tailles en blocs (etoiles, anneaux,
 * textes, traits, ecarts, portee de lecture) multipliees par {@code taille = echelle / }{@link SpaceMapCube#DEFAULT_SCALE}
 * : a l'echelle par defaut, les valeurs des constantes ; agrandir le cube agrandit toute la carte (avant T37, etoiles,
 * textes et graduations gardaient leur taille en blocs).
 * <p>
 * <b>Toujours</b> : douze aretes en bandes fines croisees (deux quads perpendiculaires par arete, couleur d'accent
 * attenuee) et six faces tres legerement teintees pour lire le volume.
 * <p>
 * <b>Carte galactique</b> ({@code docs/space/04-mathematiques.md} par. 7) :
 * <ul>
 *   <li>graduations : pas {@code nice(H / 4)} al sur trois aretes qui partent du coin haut-gauche-avant
 *       {@code (-1, 1, 1)} : arete du haut le long de {@code +X} (traits vers le haut), arete verticale avant gauche le
 *       long de {@code +Y} (traits vers la gauche, valeur comptee depuis le bas), arete du haut a gauche le long de
 *       {@code -Z} (traits vers la gauche). Chaque regle compte la distance depuis son debut ; etiquettes « 5 al » (zero
 *       omis) ;</li>
 *   <li>etoiles : billboard face camera, rayon et couleur selon la luminosite et la couleur de l'etoile principale
 *       (coeur plein + halo degrade) ; systeme courant de l'Environment entoure d'un <b>double anneau vert</b>
 *       ({@link SpaceMapCube#WORLD_COLOR}, nom vert), systeme selectionne en surbrillance (anneau jaune plus large, halo
 *       renforce, nom jaune) ; un systeme hors du cadrage n'est pas dessine ;</li>
 *   <li>noms et etiquettes : texte billboard ({@code font.drawInBatch}) : orientation et echelle de l'objet
 *       compensees, puis rotation de la camera, puis echelle {@code (-s, -s, s)} comme les etiquettes de nom vanilla
 *       ; seulement en famille {@code WORLD} et a moins de {@value #LABEL_DISTANCE} blocs (fois la taille si elle
 *       depasse 1).</li>
 * </ul>
 * <b>Vue systeme</b> ({@link #renderSystem}, echelle de {@code SpaceSystemScale} : tout a l'echelle, centre = un corps
 * ou l'origine du systeme, demi-etendue de la vue) :
 * <ul>
 *   <li>graduations en UA (memes regles que la galaxie, demi-etendue de la vue) ;</li>
 *   <li>orbites : ellipse complete en rubans croises (un ruban dans le plan de l'orbite, un perpendiculaire), couleur du
 *       corps attenuee, autour de la position affichee du parent, si l'apoapside ne depasse pas
 *       {@value #FULL_ORBIT_RATIO} demi-etendues de la vue (au-dela, l'ellipse de {@value #ORBIT_SEGMENTS} segments
 *       s'ecarterait visiblement du corps) ; sinon, pour un corps visible, un <b>arc local</b> de son orbite recalcule
 *       chaque frame autour de lui (generalise l'arc du corps zoome de T25). Un segment entierement hors du cube n'est pas
 *       dessine ; l'orbite d'un corps dessinee par un autre objet (poignee d'orbite du Configurator) est omise ;</li>
 *   <li>corps : a leurs positions de Kepler au temps de la vue (temps vivant de la Space Map), rayon affiche en unites du cube (formule exageree, ou rayon reel s'il est plus grand) ;
 *       <b>textures et rotation propre</b> (T37, T30) : texture du corps prete dans
 *       {@link SpaceBodyTextureCache#instance()} (demandee chaque frame pour tous les corps du systeme montre) : disque
 *       face camera dont chaque sommet porte les coordonnees de texture du point de la sphere qu'il represente (vue
 *       orthographique, normale ramenee dans les axes du corps par sa rotation propre {@link SpaceSpin} au temps de la
 *       vue), sans ombrage ; etoile teinte par sa couleur et eclaircie comme dans le ciel ; sinon (chargement, echec)
 *       disque en couleur unie. Etoiles avec halo, selection entouree d'un anneau jaune, noms ; un corps hors du cube
 *       n'est pas dessine ;</li>
 *   <li><b>vaisseau</b> (T25, C49) si le systeme montre est le systeme courant de l'Environment (miroir client) :
 *       disque vert de rayon fixe en unites du cube ({@value #SHIP_RADIUS}), avance de {@value #SHIP_LIFT} bloc (fois la
 *       taille) vers la camera, en {@code P_ref(t_aff) + (s - P_ref(maintenant))} (au temps vivant : {@code s}), et
 *       segment de {@value #SHIP_SEGMENT} unite du cube selon l'axe avant {@code Q . F_W}. Masque hors du cube.</li>
 * </ul>
 * <b>Croix de selection et coordonnees</b> (T26, C50, doc 04 par. 7), dans les deux vues : pour l'objet selectionne
 * dessine, trois segments fins de la couleur de la selection, paralleles aux axes du cube, passent par lui et vont
 * jusqu'aux six faces ; a chaque extremite, une etiquette face camera donne la coordonnee de l'axe du segment,
 * {@code « X -1,617 al »} ({@link SpaceCoordinates}, 3 decimales) : galaxie relative a l'origine des coordonnees,
 * systeme en UA relative a l'etoile principale (au temps de la vue). Texte ecrit sans allocation dans un
 * {@link FormattedCharSequence} reutilise ({@link CoordinateText}).
 * <p>
 * <b>Ordre et profondeur</b> : aretes, graduations, orbites, coeurs et anneaux dans
 * {@link HoloRenderTypes#TRANSLUCENT_QUADS} (ecrit la profondeur), puis les corps textures (un {@code RenderType} par
 * texture, opaque), puis le texte, puis halos et faces dans un {@code RenderType} translucide <b>sans ecriture de
 * profondeur</b> ({@link GlowType#GLOW_QUADS}) : une face ou un halo ne cache jamais une etoile ni une etiquette.
 * Changer de {@code RenderType} envoie le lot precedent : les faces sont donc toujours dessinees apres le contenu.
 * <p>
 * Le renderer lit la vue et ne fait que la mise en page. Tout ce qui ne change qu'a la synchronisation (positions
 * locales des etoiles, rayons, couleurs, largeurs des noms, graduations et leurs chaines, courbes des orbites) est mis en
 * cache par vue ({@link Layout}), reconstruit quand la liste d'en-tetes ou le cadrage change (galaxie), ou quand la copie
 * du systeme ou la demi-etendue change (systeme) ; le vaisseau et les arcs locaux ne le reconstruisent pas. Aucune
 * allocation par frame dans ce code (le {@code Font} vanilla cree son petit objet de sortie a chaque texte, comme pour
 * les etiquettes de nom), aucun etat GL modifie. Le cache de textures est avance une fois par cube dessine en vue
 * systeme ({@link SpaceBodyTextureCache#beginFrame}, en plus du ciel).
 */
public final class SpaceMapCubeRenderer implements HoloObjectRenderer<SpaceMapCube>
{
    // ---------------------------------------------------------------- cube

    /** Demi-epaisseur des aretes et des traits, en blocs (a l'echelle par defaut). */
    private static final float THICKNESS = 0.01f;
    private static final HoloColor EDGE_COLOR = HoloColor.primary(0x90);
    /** Teinte des faces (alpha faible). */
    private static final HoloColor FACE_COLOR = HoloColor.primary(0x14);
    /** Axes unitaires X, Y, Z. */
    private static final float[][] AXES = {{1f, 0f, 0f}, {0f, 1f, 0f}, {0f, 0f, 1f}};
    /** Signes des deux coordonnees fixes d'une arete (quatre aretes paralleles par axe). */
    private static final float[][] CORNERS = {{-1f, -1f}, {-1f, 1f}, {1f, -1f}, {1f, 1f}};

    // ---------------------------------------------------------------- graduations

    /** Debut (valeur 0) de chaque regle, en unites du cube (multipliees par la demi-arete). */
    private static final float[][] RULER_START = {{-1f, 1f, 1f}, {-1f, -1f, 1f}, {-1f, 1f, 1f}};
    /** Sens croissant de chaque regle. */
    private static final float[][] RULER_DIR = {{1f, 0f, 0f}, {0f, 1f, 0f}, {0f, 0f, -1f}};
    /** Direction des traits (vers l'exterieur du cube). */
    private static final float[][] RULER_OUT = {{0f, 1f, 0f}, {-1f, 0f, 0f}, {-1f, 0f, 0f}};
    /** Troisieme axe (second quad croise des traits). */
    private static final float[][] RULER_SIDE = {{0f, 0f, 1f}, {0f, 0f, 1f}, {0f, 1f, 0f}};
    /** Longueur d'un trait, en blocs. */
    private static final float TICK_LENGTH = 0.07f;
    /** Ecart entre le bout d'un trait et le centre de son etiquette, en blocs. */
    private static final float TICK_LABEL_GAP = 0.09f;
    /** Nombre maximal de graduations par regle (le pas {@code nice(H / 4)} en donne au plus 8). */
    private static final int MAX_TICKS = 16;
    private static final HoloColor TICK_COLOR = HoloColor.primary(0xC0);
    private static final HoloColor TICK_LABEL_COLOR = HoloColor.primary(0xFF);

    // ---------------------------------------------------------------- etoiles

    /** Rayon du coeur d'une etoile de luminosite 1, en blocs. */
    private static final float STAR_RADIUS = 0.03f;
    /** Bornes du facteur de rayon {@code 1 + 0,25 log10(L)}. */
    private static final float STAR_MIN_FACTOR = 0.5f;
    private static final float STAR_MAX_FACTOR = 2f;
    /** Rayon exterieur du halo, en rayons du coeur. */
    private static final float HALO_FACTOR = 2.6f;
    private static final int HALO_ALPHA = 0x60;
    private static final int SELECTED_HALO_ALPHA = 0xB0;
    /** Part de blanc au centre du coeur. */
    private static final float CORE_WHITE = 0.6f;
    /** Rayon de l'anneau du systeme courant, en rayons du coeur (au moins {@link #RING_MIN} blocs). */
    private static final float CURRENT_RING_FACTOR = 1.9f;
    /** Rayon de l'anneau de selection, en rayons du coeur (au moins {@link #RING_MIN} + 0,02 blocs). */
    private static final float SELECTED_RING_FACTOR = 2.6f;
    private static final float RING_MIN = 0.035f;
    /** Ecart entre un corps et son anneau de selection, en blocs. */
    private static final float SELECTED_RING_GAP = 0.02f;
    /** Demi-epaisseur des anneaux, en blocs. */
    private static final float RING_THICKNESS = 0.004f;
    /** Systeme courant (monde) : double anneau vert, second anneau a cet ecart du premier, en blocs. */
    private static final float CURRENT_RING_GAP = 0.009f;
    private static final HoloColor CURRENT_RING_COLOR = HoloColor.selection(0xE0);
    private static final HoloColor CURRENT_NAME_COLOR = SpaceMapCube.WORLD_COLOR;
    private static final HoloColor HIGHLIGHT = HoloColor.action(0xFF).lighter(0.03f);
    /** Tolerance du test « dans le cube ». */
    private static final float INSIDE_EPSILON = 1e-4f;
    /** Segments des disques et anneaux (pair : le disque est dessine en quads de deux secteurs). */
    private static final int SEGMENTS = 24;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];
    /** Sommet transforme (fil de rendu seulement) : {@code VertexConsumer.vertex(Matrix4f, ...)} alloue un vecteur par sommet. */
    private static final Vector3f VERTEX = new Vector3f();

    // Couleurs du theme de la frame en cours (fil de rendu), posees par {@link #applyTheme}.
    private static int edgeColor;
    private static int faceColor;
    private static int tickColor;
    private static int tickLabelColor;
    private static int currentRingColor;
    private static int currentNameColor;
    private static int highlight;
    private static int nameColor;
    private static int shipRim;
    private static int shipCore;
    private static int shipSegmentColor;
    private static int crossColor;
    private static int crossLabelColor;

    /** Resout les couleurs dans le theme de l'objet (debut de frame). */
    private static void applyTheme(HoloTheme theme)
    {
        edgeColor = EDGE_COLOR.resolve(theme);
        faceColor = FACE_COLOR.resolve(theme);
        tickColor = TICK_COLOR.resolve(theme);
        tickLabelColor = TICK_LABEL_COLOR.resolve(theme);
        currentRingColor = CURRENT_RING_COLOR.resolve(theme);
        currentNameColor = CURRENT_NAME_COLOR.resolve(theme);
        highlight = HIGHLIGHT.resolve(theme);
        nameColor = NAME_COLOR.resolve(theme);
        shipRim = SHIP_RIM.resolve(theme);
        shipCore = SHIP_CORE.resolve(theme);
        shipSegmentColor = SHIP_SEGMENT_COLOR.resolve(theme);
        crossColor = CROSS_COLOR.resolve(theme);
        crossLabelColor = CROSS_LABEL_COLOR.resolve(theme);
    }

    // ---------------------------------------------------------------- corps textures (T37)

    /** Anneaux du disque texture (du centre au bord), sur {@link #SEGMENTS} secteurs. */
    private static final int TEX_RINGS = 8;
    /** Rayon relatif ({@code sin}) et profondeur ({@code cos}) de chaque anneau : pas reguliers en angle sur la sphere. */
    private static final float[] TEX_RHO = new float[TEX_RINGS + 1];
    private static final float[] TEX_DEPTH = new float[TEX_RINGS + 1];
    private static final float INV_TWO_PI = (float) (0.5 / Math.PI);
    private static final float INV_PI = (float) (1.0 / Math.PI);
    private static final float HALF_PI = (float) (0.5 * Math.PI);
    private static final float PI = (float) Math.PI;

    static
    {
        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = i * Mth.TWO_PI / SEGMENTS;
            COS[i] = Mth.cos(angle);
            SIN[i] = Mth.sin(angle);
        }
        for (int k = 0; k <= TEX_RINGS; k++) {
            double angle = k * 0.5 * Math.PI / TEX_RINGS;
            TEX_RHO[k] = (float) Math.sin(angle);
            TEX_DEPTH[k] = (float) Math.cos(angle);
        }
    }

    // ---------------------------------------------------------------- texte

    /** Echelle du texte, en blocs par pixel de police (etiquettes de nom vanilla : 0,025). */
    private static final float TEXT_SCALE = 0.0125f;
    /** Echelle des etiquettes des graduations. */
    private static final float TICK_TEXT_SCALE = 0.01f;
    /** Distance maximale de lecture des textes, en blocs (a l'echelle par defaut ou plus petite). */
    private static final float LABEL_DISTANCE = 24f;
    /** Ecart entre le haut du coeur d'une etoile et le bas de son nom, en blocs. */
    private static final float NAME_GAP = 0.015f;
    private static final HoloColor NAME_COLOR = HoloColor.special(0xFF);

    // ---------------------------------------------------------------- vue systeme

    /** Segments d'une orbite dessinee. */
    private static final int ORBIT_SEGMENTS = 64;
    /**
     * Apoapside maximale, en demi-etendues de la vue, d'une orbite dessinee en entier : au-dela, la corde d'un segment
     * s'ecarte de l'ellipse de plus de {@code 0,0012 x 4} unite du cube, et seul un arc local est dessine.
     */
    private static final double FULL_ORBIT_RATIO = 4.0;
    /** Demi-epaisseur des orbites, en blocs. */
    private static final float ORBIT_THICKNESS = 0.004f;
    private static final int ORBIT_ALPHA = 0x90;
    /** Marge du test « dans la vue », en fraction de la demi-arete. */
    private static final float VIEW_MARGIN = 0.05f;
    /** Segments d'un arc local d'orbite. */
    private static final int ARC_SEGMENTS = 32;
    /** Etendue de l'arc local : chemin parcouru d'environ {@code ARC_REACH x H} de chaque cote du corps. */
    private static final double ARC_REACH = 2.0;

    // ---------------------------------------------------------------- vaisseau (T25)

    /** Rayon du vaisseau, en unites du cube (suit l'echelle du cube). */
    private static final float SHIP_RADIUS = 0.01f;
    /** Avance du disque du vaisseau vers la camera, en blocs (devant le disque du corps qu'il frole). */
    private static final float SHIP_LIFT = 0.06f;
    /** Longueur du segment de l'axe avant, en unites du cube. */
    private static final float SHIP_SEGMENT = 0.12f;
    /** Demi-epaisseur du segment, en blocs. */
    private static final float SHIP_THICKNESS = 0.003f;
    private static final HoloColor SHIP_RIM = SpaceMapCube.WORLD_COLOR;
    private static final HoloColor SHIP_CORE = HoloColor.selection(0xFF).lighter(0.6f);
    private static final HoloColor SHIP_SEGMENT_COLOR = HoloColor.selection(0xE0);
    private static final String UNIT_LIGHT_YEAR = " al";
    private static final String UNIT_AU = " UA";

    // ---------------------------------------------------------------- croix de selection (T26)

    /** Demi-epaisseur des segments de la croix, en blocs (plus fins que les aretes). */
    private static final float CROSS_THICKNESS = 0.0025f;
    /** Couleur de la croix : celle de la selection, un peu transparente. */
    private static final HoloColor CROSS_COLOR = HoloColor.action(0xB0).lighter(0.03f);
    private static final HoloColor CROSS_LABEL_COLOR = HoloColor.action(0xFF).lighter(0.03f);
    /** Echelle du texte des coordonnees (comme les graduations). */
    private static final float CROSS_TEXT_SCALE = TICK_TEXT_SCALE;
    /** Ecart entre l'extremite d'un segment et le bord de son etiquette, en blocs. */
    private static final float CROSS_LABEL_GAP = 0.03f;
    /** Demi-hauteur d'une ligne de texte, en pixels de police. */
    private static final float TEXT_HALF_HEIGHT = 4.5f;
    private static final char[] AXIS_NAMES = {'X', 'Y', 'Z'};

    /** Mise en page par vue (cle faible : un module oublie libere son cache). */
    private final Map<SpaceMapView, Layout> layouts = new WeakHashMap<>();
    /** Positions affichees des corps (unites du cube), tableau reutilise. */
    private final Vector3d[] positions = SpaceOrbits.newPositionArray();
    private final Vector3d work = new Vector3d();
    /** Matrice d'un billboard (unite = bloc, plan XY face a la camera). */
    private final Matrix4f billboard = new Matrix4f();
    /** Matrice d'un texte. */
    private final Matrix4f textMatrix = new Matrix4f();
    /** Rotation qui ramene le repere de l'objet a celui de la camera : {@code R^-1 . C} (billboard -> cube). */
    private final Quaternionf facing = new Quaternionf();
    /** Corps visibles de la frame (dans le cube). */
    private final boolean[] visible = new boolean[SpaceLimits.MAX_BODIES];
    /** Arc local d'une orbite (unites du cube, relatif au corps) et directions laterales. */
    private final float[] arcPoints = new float[(ARC_SEGMENTS + 1) * 3];
    private final float[] arcSides = new float[(ARC_SEGMENTS + 1) * 3];
    private final Vector3d arcCenter = new Vector3d();
    /** Pose affichee du monde, positions des corps du systeme courant de l'Environment, axe avant (vaisseau). */
    private final SpacePose shipPose = new SpacePose();
    private final Vector3d[] envBodies = SpaceOrbits.newPositionArray();
    private final Vector3d ship = new Vector3d();
    private final Vector3d shipForward = new Vector3d();
    /** Croix : texte d'une etiquette, coordonnees affichees, direction d'un axe dans le repere du billboard. */
    private final CoordinateText coordinate = new CoordinateText();
    private final double[] crossValues = new double[3];
    private final Vector3f crossDirection = new Vector3f();
    /** Textures des corps du systeme montre (frame courante), {@code null} = pas prete. */
    private final SpaceBodyTextureCache textures = SpaceBodyTextureCache.instance();
    private final SpaceBodyTextureCache.Texture[] bodyTextures = new SpaceBodyTextureCache.Texture[SpaceLimits.MAX_BODIES];
    /** Rotation propre d'un corps, billboard -> axes du corps, axes du billboard dans les axes du corps. */
    private final Quaterniond spin = new Quaterniond();
    private final Quaterniond facingD = new Quaterniond();
    private final Quaterniond toBody = new Quaterniond();
    private final Vector3d bodyX = new Vector3d();
    private final Vector3d bodyY = new Vector3d();
    private final Vector3d bodyEye = new Vector3d();
    /** Coordonnees de texture des sommets du disque texture. */
    private final float[] vertexU = new float[(TEX_RINGS + 1) * (SEGMENTS + 1)];
    private final float[] vertexV = new float[(TEX_RINGS + 1) * (SEGMENTS + 1)];

    // ---------------------------------------------------------------- frame en cours (fil de rendu)

    /** Echelle moyenne du cube et taille relative a l'echelle par defaut. */
    private float scale;
    private float size;
    private float invX;
    private float invY;
    private float invZ;
    /** Carre de la distance de lecture des textes de la frame. */
    private float labelDistanceSqr;

    @Override
    public void render(SpaceMapCube cube, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        applyTheme(HoloThemes.resolve(cube));
        HoloTransform t = cube.transform();
        scale = Math.max(1e-3f, (t.scaleX() + t.scaleY() + t.scaleZ()) / 3f);
        size = scale / SpaceMapCube.DEFAULT_SCALE;
        invX = 1f / Math.max(1e-3f, t.scaleX());
        invY = 1f / Math.max(1e-3f, t.scaleY());
        invZ = 1f / Math.max(1e-3f, t.scaleZ());
        float distance = LABEL_DISTANCE * Math.max(1f, size);
        labelDistanceSqr = distance * distance;
        Matrix4f matrix = pose.last().pose();
        edges(buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS), matrix, THICKNESS * size / scale, packedLight);
        SpaceMapView view = cube.view();
        if (view != null) {
            HoloEditMath.rotation(facing, t).conjugate()
                    .mul(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            if (view.systemView()) {
                SpaceSystem system = view.shownSystem();
                if (system != null) renderSystem(view, system, matrix, buffers, packedLight, ctx);
            } else if (view.frameHalf() > 0.0) {
                renderGalaxy(view, matrix, buffers, packedLight, ctx);
            }
        }
        faces(buffers.getBuffer(GlowType.GLOW_QUADS), matrix, packedLight);
    }

    // ---------------------------------------------------------------- carte galactique

    private void renderGalaxy(SpaceMapView view, Matrix4f matrix, MultiBufferSource buffers, int light,
                              HoloDrawContext ctx)
    {
        Layout layout = layout(view);
        layout.ensureGraduations(view.frameHalf(), UNIT_LIGHT_YEAR, Minecraft.getInstance().font);
        int selected = view.selectedSystem();
        int current = view.currentSystemId();
        float ringThickness = RING_THICKNESS * size;

        // Lot avec profondeur : graduations, coeurs et anneaux.
        VertexConsumer solid = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        ticks(solid, matrix, layout, THICKNESS * size / scale, TICK_LENGTH * size / scale, light);
        for (int i = 0; i < layout.count; i++) {
            if (!layout.inside[i]) continue;
            placeBillboard(matrix, layout.qx[i], layout.qy[i], layout.qz[i]);
            float r = layout.radius[i] * size;
            disc(solid, billboard, r, layout.core[i], layout.color[i], light);
            int id = layout.ids[i];
            if (id == current) {
                float ringRadius = Math.max(RING_MIN * size, r * CURRENT_RING_FACTOR);
                ring(solid, billboard, ringRadius, ringThickness, currentRingColor, light);
                ring(solid, billboard, ringRadius + CURRENT_RING_GAP * size, ringThickness, currentRingColor,
                        light);
            }
            if (id == selected) ring(solid, billboard, Math.max((RING_MIN + 0.02f) * size, r * SELECTED_RING_FACTOR),
                    ringThickness, highlight, light);
        }
        int crossIndex = selectedStar(layout, selected);
        if (crossIndex >= 0) {
            cross(solid, matrix, layout.qx[crossIndex], layout.qy[crossIndex], layout.qz[crossIndex],
                    CROSS_THICKNESS * size / scale, light);
        }

        // Textes : famille monde seulement, a portee de lecture.
        if (ctx.family() == HoloAnchor.Family.WORLD) {
            Font font = Minecraft.getInstance().font;
            float textScale = TEXT_SCALE * size;
            for (int i = 0; i < layout.count; i++) {
                if (!layout.inside[i]) continue;
                placeBillboard(matrix, layout.qx[i], layout.qy[i], layout.qz[i]);
                if (!readable(billboard)) continue;
                textMatrix.set(billboard).translate(0f, (layout.radius[i] + NAME_GAP) * size, 0f)
                        .scale(-textScale, -textScale, textScale);
                int label = layout.ids[i] == selected ? highlight
                        : layout.ids[i] == current ? currentNameColor : nameColor;
                font.drawInBatch(layout.names[i], -0.5f * layout.widths[i], -8f, label, false, textMatrix,
                        buffers, Font.DisplayMode.NORMAL, 0, light);
            }
            tickLabels(font, matrix, layout, buffers, light);
            if (crossIndex >= 0) {
                // Coordonnees galactiques relatives a l'origine (al).
                SystemHeader star = layout.headers.get(crossIndex);
                SystemHeader origin = view.originHeader();
                crossValues[0] = star.x() - (origin != null ? origin.x() : 0.0);
                crossValues[1] = star.y() - (origin != null ? origin.y() : 0.0);
                crossValues[2] = star.z() - (origin != null ? origin.z() : 0.0);
                crossLabels(font, matrix, buffers, layout.qx[crossIndex], layout.qy[crossIndex],
                        layout.qz[crossIndex], UNIT_LIGHT_YEAR, light);
            }
        }

        // Lot sans profondeur : halos.
        VertexConsumer glow = buffers.getBuffer(GlowType.GLOW_QUADS);
        for (int i = 0; i < layout.count; i++) {
            if (!layout.inside[i]) continue;
            placeBillboard(matrix, layout.qx[i], layout.qy[i], layout.qz[i]);
            halo(glow, billboard, layout.radius[i] * size, layout.color[i],
                    layout.ids[i] == selected ? SELECTED_HALO_ALPHA : HALO_ALPHA, light);
        }
    }

    // ---------------------------------------------------------------- vue systeme

    /**
     * Vue systeme : dessin du systeme montre (copie complete) dans le repere du cube, au temps de la vue, centre sur
     * son corps central (ou l'origine). Appele entre les aretes (deja dans {@link HoloRenderTypes#TRANSLUCENT_QUADS})
     * et les faces ({@link GlowType#GLOW_QUADS}) ; la pile n'est pas modifiee.
     */
    private void renderSystem(SpaceMapView view, SpaceSystem system, Matrix4f matrix, MultiBufferSource buffers,
                              int light, HoloDrawContext ctx)
    {
        textures.beginFrame();
        Font font = Minecraft.getInstance().font;
        double half = view.viewHalf();
        int center = view.centerIndex();
        Layout layout = layoutOf(view);
        layout.ensureBodies(system, half, font);
        layout.ensureGraduations(half, UNIT_AU, font);
        double days = view.displayDays(ctx.partialTick());
        SpaceSystemScale.positions(system, days, half, center, positions);
        for (int i = 0; i < layout.bodyCount; i++) {
            Vector3d p = positions[i];
            visible[i] = inside((float) p.x, (float) p.y, (float) p.z);
            // Textures de tous les corps du systeme montre (chargement a la demande, gardees tant qu'il l'est).
            bodyTextures[i] = textures.texture(system, i);
        }
        int selected = view.selectedBody();
        int hidden = view.hiddenOrbitBody();

        // Lot avec profondeur : graduations, orbites, corps non textures, anneau de selection, croix, vaisseau.
        VertexConsumer solid = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        ticks(solid, matrix, layout, THICKNESS * size / scale, TICK_LENGTH * size / scale, light);
        float width = ORBIT_THICKNESS * size / scale;
        int stride = (ORBIT_SEGMENTS + 1) * 3;
        List<SpaceBody> bodies = system.bodies();
        for (int i = 0; i < layout.bodyCount; i++) {
            if (layout.parent[i] < 0 || layout.bodyIds[i] == hidden) continue;
            if (layout.orbitFull[i]) {
                Vector3d p = positions[layout.parent[i]];
                ribbon(solid, matrix, layout.orbitPoints, layout.orbitSides, i * stride, ORBIT_SEGMENTS,
                        layout.orbitNormals, i * 3, (float) p.x, (float) p.y, (float) p.z, width,
                        layout.orbitColor[i], light);
            } else if (visible[i]) {
                // Orbite bien plus grande que la vue : arc local autour du corps, au temps de la vue.
                Vector3d p = positions[i];
                localArc(bodies.get(i).orbit(), days, half, layout.orbitNormals, i * 3);
                ribbon(solid, matrix, arcPoints, arcSides, 0, ARC_SEGMENTS, layout.orbitNormals, i * 3,
                        (float) p.x, (float) p.y, (float) p.z, width, layout.orbitColor[i], light);
            }
        }
        float ringThickness = RING_THICKNESS * size;
        for (int i = 0; i < layout.bodyCount; i++) {
            if (!visible[i]) continue;
            Vector3d p = positions[i];
            placeBillboard(matrix, (float) p.x, (float) p.y, (float) p.z);
            float r = layout.bodyRadius[i] * scale;
            if (bodyTextures[i] == null) disc(solid, billboard, r, layout.bodyCore[i], layout.bodyColor[i], light);
            if (layout.bodyIds[i] == selected) {
                ring(solid, billboard, r + SELECTED_RING_GAP * size, ringThickness, highlight, light);
            }
        }
        int crossIndex = selectedBody(layout, selected);
        if (crossIndex >= 0) {
            Vector3d p = positions[crossIndex];
            cross(solid, matrix, (float) p.x, (float) p.y, (float) p.z, CROSS_THICKNESS * size / scale, light);
        }
        if (shipPosition(system, center, half, ctx.partialTick())) drawShip(solid, matrix, light);

        // Corps textures : un lot par texture.
        for (int i = 0; i < layout.bodyCount; i++) {
            SpaceBodyTextureCache.Texture texture = bodyTextures[i];
            if (!visible[i] || texture == null) continue;
            Vector3d p = positions[i];
            texturedBody(buffers.getBuffer(texture.renderType()), matrix, texture, bodies.get(i), days,
                    (float) p.x, (float) p.y, (float) p.z, layout.bodyRadius[i] * scale);
        }

        // Textes : famille monde seulement, a portee de lecture.
        if (ctx.family() == HoloAnchor.Family.WORLD) {
            float textScale = TEXT_SCALE * size;
            for (int i = 0; i < layout.bodyCount; i++) {
                if (!visible[i]) continue;
                Vector3d p = positions[i];
                placeBillboard(matrix, (float) p.x, (float) p.y, (float) p.z);
                if (!readable(billboard)) continue;
                textMatrix.set(billboard).translate(0f, layout.bodyRadius[i] * scale + NAME_GAP * size, 0f)
                        .scale(-textScale, -textScale, textScale);
                font.drawInBatch(layout.bodyNames[i], -0.5f * layout.bodyWidths[i], -8f,
                        layout.bodyIds[i] == selected ? highlight : nameColor, false, textMatrix, buffers,
                        Font.DisplayMode.NORMAL, 0, light);
            }
            tickLabels(font, matrix, layout, buffers, light);
            if (crossIndex >= 0) {
                // Position relative a l'etoile principale (indice 0, a l'origine du systeme), en UA : l'ecart des
                // positions affichees ne depend pas du centre de la vue.
                Vector3d p = positions[crossIndex];
                Vector3d star = positions[0];
                double toAu = half / SpaceMapCube.HALF_SIZE;
                crossValues[0] = (p.x - star.x) * toAu;
                crossValues[1] = (p.y - star.y) * toAu;
                crossValues[2] = (p.z - star.z) * toAu;
                crossLabels(font, matrix, buffers, (float) p.x, (float) p.y, (float) p.z, UNIT_AU, light);
            }
        }

        // Lot sans profondeur : halos des etoiles.
        VertexConsumer glow = buffers.getBuffer(GlowType.GLOW_QUADS);
        for (int i = 0; i < layout.bodyCount; i++) {
            if (!layout.star[i] || !visible[i]) continue;
            Vector3d p = positions[i];
            placeBillboard(matrix, (float) p.x, (float) p.y, (float) p.z);
            halo(glow, billboard, layout.bodyRadius[i] * scale, layout.bodyColor[i],
                    layout.bodyIds[i] == selected ? SELECTED_HALO_ALPHA : HALO_ALPHA, light);
        }
    }

    /** Vrai si le point local {@code (x, y, z)} est dans le cube, a {@value #VIEW_MARGIN} demi-arete pres. */
    private static boolean inside(float x, float y, float z)
    {
        float limit = SpaceMapCube.HALF_SIZE * (1f + VIEW_MARGIN);
        return Math.abs(x) <= limit && Math.abs(y) <= limit && Math.abs(z) <= limit;
    }

    /**
     * Ruban d'une courbe de {@code segments} segments ({@code points} et {@code sides} a partir de {@code base}, trois
     * flottants par point, relatifs a {@code (px, py, pz)}) : deux rubans de demi-largeur {@code w}, l'un dans le plan
     * de l'orbite (directions laterales), l'autre selon sa normale ({@code normals[n..n+2]}). Un segment dont les deux
     * extremites sont hors du cube n'est pas dessine.
     */
    private static void ribbon(VertexConsumer consumer, Matrix4f matrix, float[] points, float[] sides, int base,
                               int segments, float[] normals, int n, float px, float py, float pz, float w, int color,
                               int light)
    {
        float nx = normals[n] * w;
        float ny = normals[n + 1] * w;
        float nz = normals[n + 2] * w;
        for (int k = 0; k < segments; k++) {
            int a = base + k * 3;
            int b = a + 3;
            float ax = px + points[a];
            float ay = py + points[a + 1];
            float az = pz + points[a + 2];
            float bx = px + points[b];
            float by = py + points[b + 1];
            float bz = pz + points[b + 2];
            if (!inside(ax, ay, az) && !inside(bx, by, bz)) continue;
            float sax = sides[a] * w;
            float say = sides[a + 1] * w;
            float saz = sides[a + 2] * w;
            float sbx = sides[b] * w;
            float sby = sides[b + 1] * w;
            float sbz = sides[b + 2] * w;
            vertex(consumer, matrix, ax - sax, ay - say, az - saz, color, light);
            vertex(consumer, matrix, bx - sbx, by - sby, bz - sbz, color, light);
            vertex(consumer, matrix, bx + sbx, by + sby, bz + sbz, color, light);
            vertex(consumer, matrix, ax + sax, ay + say, az + saz, color, light);
            vertex(consumer, matrix, ax - nx, ay - ny, az - nz, color, light);
            vertex(consumer, matrix, bx - nx, by - ny, bz - nz, color, light);
            vertex(consumer, matrix, bx + nx, by + ny, bz + nz, color, light);
            vertex(consumer, matrix, ax + nx, ay + ny, az + nz, color, light);
        }
    }

    /**
     * {@link #arcPoints} / {@link #arcSides} : arc d'une orbite, relatif au corps, en unites du cube, au temps
     * {@code days}. Anomalie excentrique du corps {@code E0}, puis {@value #ARC_SEGMENTS} segments reguliers en
     * {@code E} sur {@code E0 +/- dE}, avec {@code dE = min(pi, ARC_REACH . H / (a (1 - e)))} (la vitesse le long de
     * l'ellipse vaut au moins {@code a (1 - e)} par radian : l'arc sort du cube des deux cotes). Sans allocation.
     */
    private void localArc(SpaceOrbit orbit, double days, double half, float[] normals, int n)
    {
        double unit = SpaceSystemScale.unit(half);
        double e = orbit.eccentricity();
        double aKm = orbit.semiMajorAxis() * SpaceUnits.AU_KM;
        double spread = Math.min(Math.PI, ARC_REACH * half * SpaceUnits.AU_KM / Math.max(1e-9, aKm * (1.0 - e)));
        double e0 = SpaceOrbits.eccentricAnomaly(SpaceOrbits.meanAnomaly(orbit, days), e);
        SpaceOrbits.orbitPoint(orbit, e0, arcCenter);
        for (int k = 0; k <= ARC_SEGMENTS; k++) {
            SpaceOrbits.orbitPoint(orbit, e0 + spread * (2.0 * k / ARC_SEGMENTS - 1.0), work);
            arcPoints[k * 3] = (float) ((work.x - arcCenter.x) * unit);
            arcPoints[k * 3 + 1] = (float) ((work.y - arcCenter.y) * unit);
            arcPoints[k * 3 + 2] = (float) ((work.z - arcCenter.z) * unit);
        }
        lateral(arcPoints, arcSides, 0, ARC_SEGMENTS, false, normals[n], normals[n + 1], normals[n + 2], work);
    }

    /**
     * Directions laterales unitaires d'une courbe ({@code segments + 1} points a partir de {@code base}) : tangente par
     * difference centree (courbe fermee : le dernier point est le premier ; ouverte : difference simple aux bouts),
     * croisee avec la normale du plan {@code (nx, ny, nz)}. {@code tmp} : vecteur de travail.
     */
    private static void lateral(float[] points, float[] sides, int base, int segments, boolean closed, double nx,
                                double ny, double nz, Vector3d tmp)
    {
        for (int k = 0; k <= segments; k++) {
            int next = base + (closed ? (k + 1) % segments : Math.min(k + 1, segments)) * 3;
            int previous = base + (closed ? (k + segments - 1) % segments : Math.max(k - 1, 0)) * 3;
            double tx = points[next] - points[previous];
            double ty = points[next + 1] - points[previous + 1];
            double tz = points[next + 2] - points[previous + 2];
            // Laterale = tangente x normale, unitaire.
            tmp.set(ty * nz - tz * ny, tz * nx - tx * nz, tx * ny - ty * nx);
            double length = tmp.length();
            if (length > 0.0) tmp.div(length);
            int at = base + k * 3;
            sides[at] = (float) tmp.x;
            sides[at + 1] = (float) tmp.y;
            sides[at + 2] = (float) tmp.z;
        }
    }

    // ---------------------------------------------------------------- corps textures (T37)

    /**
     * Corps texture de rayon {@code r} (blocs) au point local {@code (x, y, z)} : disque face camera de
     * {@value #TEX_RINGS} anneaux sur {@value #SEGMENTS} secteurs, chaque sommet au rayon {@code sin a} du disque
     * representant le point de la sphere de normale {@code sin a (cos t X + sin t Y) + cos a E} (vue orthographique ;
     * {@code X}, {@code Y} : axes du billboard, {@code E} : vers l'oeil, tous ramenes dans les axes du corps : billboard
     * -> cube = axes du systeme par {@link #facing}, puis systeme -> corps par l'inverse de la rotation propre au temps
     * {@code days}). Coordonnees de texture comme le ciel ({@code docs/space/04-mathematiques.md} par. 4, C85) :
     * longitude de {@code +X} vers {@code -Z}, 0 au centre de l'image, latitude vers {@code +Y} ; couture traitee quad par
     * quad ({@code u} au-dela de 1, texture repetee). Couleur : blanc, ou couleur du corps pour une texture teintee
     * (etoile eclaircie par {@code min(1 / luminance, 1 / max(r, g, b))}, comme le ciel) ; pas d'ombrage. Ordre des
     * sommets inverse de {@link #disc} : le billboard a {@code +X} a gauche de l'ecran, et le {@code RenderType} des
     * textures elimine les faces arriere.
     */
    private void texturedBody(VertexConsumer vc, Matrix4f matrix, SpaceBodyTextureCache.Texture texture,
                              SpaceBody body, double days, float x, float y, float z, float r)
    {
        placeBillboard(matrix, x, y, z);
        SpaceSpin.orientation(body, days, spin);
        facingD.set(facing.x, facing.y, facing.z, facing.w);
        toBody.set(spin).conjugate().mul(facingD);
        toBody.transform(bodyX.set(1.0, 0.0, 0.0));
        toBody.transform(bodyY.set(0.0, 1.0, 0.0));
        toBody.transform(bodyEye.set(0.0, 0.0, -1.0));
        float red = 1f;
        float green = 1f;
        float blue = 1f;
        if (texture.tinted()) {
            int color = body.color();
            red = (color >> 16 & 0xFF) / 255f;
            green = (color >> 8 & 0xFF) / 255f;
            blue = (color & 0xFF) / 255f;
            if (body.kind() == SpaceBodyKind.STAR) {
                float gain = Math.min(1f / Math.max(texture.luminance(), 0.05f),
                        1f / Math.max(Math.max(red, green), Math.max(blue, 0.05f)));
                red *= gain;
                green *= gain;
                blue *= gain;
            }
        }
        int row = SEGMENTS + 1;
        for (int k = 0; k <= TEX_RINGS; k++) {
            float rho = TEX_RHO[k];
            float depth = TEX_DEPTH[k];
            for (int j = 0; j <= SEGMENTS; j++) {
                float c = COS[j] * rho;
                float s = SIN[j] * rho;
                float nx = (float) (c * bodyX.x + s * bodyY.x + depth * bodyEye.x);
                float ny = (float) (c * bodyX.y + s * bodyY.y + depth * bodyEye.y);
                float nz = (float) (c * bodyX.z + s * bodyY.z + depth * bodyEye.z);
                int index = k * row + j;
                vertexU[index] = 0.5f + atan2(-nz, nx) * INV_TWO_PI;
                vertexV[index] = 0.5f - atan2(ny, Mth.sqrt(nx * nx + nz * nz)) * INV_PI;
            }
        }
        for (int k = 0; k < TEX_RINGS; k++) {
            for (int j = 0; j < SEGMENTS; j++) {
                // Interieur, interieur suivant, exterieur suivant, exterieur : sens direct vu de l'oeil.
                int i0 = k * row + j;
                int i1 = i0 + 1;
                int i2 = i1 + row;
                int i3 = i0 + row;
                float u0 = vertexU[i0], u1 = vertexU[i1], u2 = vertexU[i2], u3 = vertexU[i3];
                float low = Math.min(Math.min(u0, u1), Math.min(u2, u3));
                float high = Math.max(Math.max(u0, u1), Math.max(u2, u3));
                if (high - low > 0.5f) {
                    if (u0 < 0.5f) u0 += 1f;
                    if (u1 < 0.5f) u1 += 1f;
                    if (u2 < 0.5f) u2 += 1f;
                    if (u3 < 0.5f) u3 += 1f;
                }
                texturedVertex(vc, r, k, j, u0, vertexV[i0], red, green, blue);
                texturedVertex(vc, r, k, j + 1, u1, vertexV[i1], red, green, blue);
                texturedVertex(vc, r, k + 1, j + 1, u2, vertexV[i2], red, green, blue);
                texturedVertex(vc, r, k + 1, j, u3, vertexV[i3], red, green, blue);
            }
        }
    }

    /** Sommet du disque texture ({@code POSITION_COLOR_TEX}) : anneau {@code ring}, secteur {@code sector}. */
    private void texturedVertex(VertexConsumer vc, float r, int ring, int sector, float u, float v, float red,
                                float green, float blue)
    {
        float rho = TEX_RHO[ring] * r;
        billboard.transformPosition(rho * COS[sector], rho * SIN[sector], 0f, VERTEX);
        vc.vertex(VERTEX.x, VERTEX.y, VERTEX.z).color(red, green, blue, 1f).uv(u, v).endVertex();
    }

    /**
     * {@code atan2(y, x)} approche (erreur sous {@code 1e-5} rad ; copie de celui du ciel) : polynome minimax de
     * {@code atan} sur {@code [0, 1]}, puis octants. {@code (0, 0)} rend 0.
     */
    private static float atan2(float y, float x)
    {
        float ax = Math.abs(x);
        float ay = Math.abs(y);
        float max = Math.max(ax, ay);
        if (max == 0f) return 0f;
        float t = Math.min(ax, ay) / max;
        float t2 = t * t;
        float a = t * (0.99997726f + t2 * (-0.33262347f + t2 * (0.19354346f + t2 * (-0.11643287f
                + t2 * (0.05265332f + t2 * -0.01172120f)))));
        if (ay > ax) a = HALF_PI - a;
        if (x < 0f) a = PI - a;
        return y < 0f ? -a : a;
    }

    // ---------------------------------------------------------------- croix de selection (T26)

    /** Indice dans la mise en page du systeme selectionne {@code id}, s'il est dans le cadrage ; {@code -1} sinon. */
    private static int selectedStar(Layout layout, int id)
    {
        if (id == 0) return -1;
        for (int i = 0; i < layout.count; i++) {
            if (layout.ids[i] == id) return layout.inside[i] ? i : -1;
        }
        return -1;
    }

    /** Indice du corps selectionne {@code id}, s'il est dessine (dans le cube) ; {@code -1} sinon. */
    private int selectedBody(Layout layout, int id)
    {
        if (id == 0) return -1;
        for (int i = 0; i < layout.bodyCount; i++) {
            if (layout.bodyIds[i] == id) return visible[i] ? i : -1;
        }
        return -1;
    }

    /**
     * Croix de la selection en {@code (x, y, z)} (unites du cube) : un segment par axe, d'une face a l'autre, en deux
     * bandes croisees de demi-largeur {@code t}.
     */
    private static void cross(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float t, int light)
    {
        float h = SpaceMapCube.HALF_SIZE;
        for (int i = 0; i < 3; i++) {
            float[] a = AXES[i];
            float[] u = AXES[(i + 1) % 3];
            float[] v = AXES[(i + 2) % 3];
            // Debut sur la face -h de l'axe, les deux autres coordonnees sont celles de l'objet.
            float sx = a[0] != 0f ? -h : x;
            float sy = a[1] != 0f ? -h : y;
            float sz = a[2] != 0f ? -h : z;
            float dx = 2f * h * a[0];
            float dy = 2f * h * a[1];
            float dz = 2f * h * a[2];
            band(consumer, matrix, sx, sy, sz, dx, dy, dz, t * u[0], t * u[1], t * u[2], crossColor, light);
            band(consumer, matrix, sx, sy, sz, dx, dy, dz, t * v[0], t * v[1], t * v[2], crossColor, light);
        }
    }

    /**
     * Etiquettes des six extremites de la croix en {@code (x, y, z)} : pour chaque axe, sa coordonnee
     * ({@link #crossValues}) suivie de {@code unit}, aux deux faces.
     */
    private void crossLabels(Font font, Matrix4f matrix, MultiBufferSource buffers, float x, float y, float z,
                             String unit, int light)
    {
        float h = SpaceMapCube.HALF_SIZE;
        for (int i = 0; i < 3; i++) {
            coordinate.set(AXIS_NAMES[i], crossValues[i], unit, font);
            float[] a = AXES[i];
            for (int side = -1; side <= 1; side += 2) {
                float ex = a[0] != 0f ? side * h : x;
                float ey = a[1] != 0f ? side * h : y;
                float ez = a[2] != 0f ? side * h : z;
                crossLabel(font, matrix, buffers, ex, ey, ez, side * a[0], side * a[1], side * a[2], light);
            }
        }
    }

    /**
     * Etiquette {@link #coordinate} a l'extremite {@code (ex, ey, ez)} d'un segment dont la direction sortante est
     * {@code (ax, ay, az)} (axes du cube). Direction vue a l'ecran = axe exprime dans le repere du billboard
     * ({@code facing^-1 . a}, composantes X / Y) ; le centre du texte est pousse dans cette direction de l'ecart plus la
     * demi-etendue de la boite du texte dans cette direction : la boite ne touche ni l'extremite ni le segment. Axe
     * presque face a la camera : etiquette au-dessus (face {@code +}) ou au-dessous (face {@code -}).
     */
    private void crossLabel(Font font, Matrix4f matrix, MultiBufferSource buffers, float ex, float ey, float ez,
                            float ax, float ay, float az, int light)
    {
        placeBillboard(matrix, ex, ey, ez);
        if (!readable(billboard)) return;
        facing.transformInverse(crossDirection.set(ax, ay, az));
        float dx = crossDirection.x;
        float dy = crossDirection.y;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < 1e-3f) {
            dx = 0f;
            dy = ax + ay + az >= 0f ? 1f : -1f;
        } else {
            dx /= length;
            dy /= length;
        }
        float textScale = CROSS_TEXT_SCALE * size;
        float halfWidth = 0.5f * coordinate.width * textScale;
        float halfHeight = TEXT_HALF_HEIGHT * textScale;
        float d = CROSS_LABEL_GAP * size + Math.abs(dx) * halfWidth + Math.abs(dy) * halfHeight;
        textMatrix.set(billboard).translate(dx * d, dy * d, 0f).scale(-textScale, -textScale, textScale);
        font.drawInBatch(coordinate, -0.5f * coordinate.width, -4f, crossLabelColor, false, textMatrix, buffers,
                Font.DisplayMode.NORMAL, 0, light);
    }

    /**
     * Texte d'une etiquette de coordonnee, reutilise ({@code « X -1,617 al »}) : caracteres ASCII dans un tableau
     * fixe, livres a la police comme {@link FormattedCharSequence} ; largeur sommee depuis une table des largeurs des
     * caracteres ASCII remplie au premier usage. Sans allocation ensuite. Fil de rendu seulement.
     */
    private static final class CoordinateText implements FormattedCharSequence
    {
        private final char[] chars = new char[2 + SpaceCoordinates.MAX_LENGTH + 8];
        private int length;
        /** Largeur du texte, en pixels de police. */
        private int width;
        /** Largeur de chaque caractere ASCII imprimable, en pixels de police ; {@code null} avant le premier usage. */
        private int[] charWidths;

        /** {@code axis}, espace, {@code value} au format de {@link SpaceCoordinates#write}, puis {@code unit}. */
        void set(char axis, double value, String unit, Font font)
        {
            if (charWidths == null) {
                charWidths = new int[128];
                for (int c = 32; c < 127; c++) charWidths[c] = font.width(String.valueOf((char) c));
            }
            int at = 0;
            chars[at++] = axis;
            chars[at++] = ' ';
            at = SpaceCoordinates.write(value, chars, at);
            for (int i = 0; i < unit.length() && at < chars.length; i++) chars[at++] = unit.charAt(i);
            length = at;
            int sum = 0;
            for (int i = 0; i < length; i++) {
                char c = chars[i];
                sum += c < 128 ? charWidths[c] : 0;
            }
            width = sum;
        }

        @Override
        public boolean accept(FormattedCharSink sink)
        {
            for (int i = 0; i < length; i++) {
                if (!sink.accept(i, Style.EMPTY, chars[i])) return false;
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- vaisseau (T25)

    /**
     * {@link #ship} (unites du cube) et {@link #shipForward} (axes du systeme = axes locaux du cube) du vaisseau, si le
     * systeme montre est le systeme courant du miroir client de l'Environment (bloc froid recu) : position
     * {@code P_ref(t_aff) + (s - P_ref(maintenant))} (C49) dans la vue courante, ou {@code s} sans referentiel (vue
     * centree sur l'origine du systeme seulement) ; faux sinon, ou hors du cube. Le referentiel et la pose sont ceux de
     * l'affichage du ciel ({@code referenceBodyId}, {@code pose(partialTick)}). Sans allocation.
     */
    private boolean shipPosition(SpaceSystem system, int center, double half, float partialTick)
    {
        SpaceEnvironmentModule env = SpaceEnvironmentModule.client();
        if (env == null || !env.synced()) return false;
        SpaceSystem current = env.currentSystem();
        if (current == null || current.id() != system.id()) return false;
        env.pose(partialTick, shipPose);
        Vector3d s = shipPose.position();
        double unit = SpaceSystemScale.unit(half);
        int reference = env.referenceBodyId();
        int now = reference == 0 ? -1 : current.indexOf(reference);
        int shown = reference == 0 ? -1 : system.indexOf(reference);
        if (now >= 0 && shown >= 0) {
            env.bodyPositions(partialTick, envBodies);
            Vector3d r = envBodies[now];
            Vector3d q = positions[shown];
            ship.set(q.x + (s.x - r.x) * unit, q.y + (s.y - r.y) * unit, q.z + (s.z - r.z) * unit);
        } else if (center < 0) {
            ship.set(s.x * unit, s.y * unit, s.z * unit);
        } else {
            return false;
        }
        if (!inside((float) ship.x, (float) ship.y, (float) ship.z)) return false;
        shipPose.orientation().transform(env.settings().forward(shipForward));
        return true;
    }

    /**
     * Vaisseau : disque vert (rayon {@value #SHIP_RADIUS} unite du cube) avance de {@value #SHIP_LIFT} bloc (fois la
     * taille) vers la camera, puis segment de l'axe avant ({@value #SHIP_SEGMENT} unite du cube, deux bandes croisees)
     * dans le repere local du cube.
     */
    private void drawShip(VertexConsumer consumer, Matrix4f matrix, int light)
    {
        float x = (float) ship.x;
        float y = (float) ship.y;
        float z = (float) ship.z;
        float fx = (float) shipForward.x;
        float fy = (float) shipForward.y;
        float fz = (float) shipForward.z;
        // Deux directions perpendiculaires a l'axe : u = f x (Y ou X), v = f x u.
        float ux;
        float uy;
        float uz;
        if (Math.abs(fy) < 0.9f) {
            ux = -fz;
            uy = 0f;
            uz = fx;
        } else {
            ux = 0f;
            uy = fz;
            uz = -fy;
        }
        float length = Mth.sqrt(ux * ux + uy * uy + uz * uz);
        float w = SHIP_THICKNESS * size / scale / Math.max(1e-6f, length);
        ux *= w;
        uy *= w;
        uz *= w;
        float vx = (fy * uz - fz * uy);
        float vy = (fz * ux - fx * uz);
        float vz = (fx * uy - fy * ux);
        float d = SHIP_SEGMENT * SpaceMapCube.HALF_SIZE;
        band(consumer, matrix, x, y, z, fx * d, fy * d, fz * d, ux, uy, uz, shipSegmentColor, light);
        band(consumer, matrix, x, y, z, fx * d, fy * d, fz * d, vx, vy, vz, shipSegmentColor, light);
        placeBillboard(matrix, x, y, z);
        // Repere de la camera : +Z regarde vers la scene, -Z revient vers l'oeil.
        billboard.translate(0f, 0f, -SHIP_LIFT * size);
        disc(consumer, billboard, SHIP_RADIUS * SpaceMapCube.HALF_SIZE * scale, shipCore, shipRim, light);
    }

    /**
     * {@link #billboard} : repere de bloc face a la camera, centre au point local {@code q} du cube. Chaine
     * {@code M . T(q) . S^-1 . R^-1 . C} : l'echelle et l'orientation de l'objet sont annulees, puis la rotation de la
     * camera appliquee (comme les etiquettes de nom vanilla), d'ou une taille en blocs (les appelants la multiplient par
     * la taille de la carte).
     */
    private void placeBillboard(Matrix4f matrix, float qx, float qy, float qz)
    {
        billboard.set(matrix).translate(qx, qy, qz).scale(invX, invY, invZ).rotate(facing);
    }

    /** Vrai si le centre du billboard courant est a portee de lecture de la camera. */
    private boolean readable(Matrix4f m)
    {
        // Pile du monde : translation = position relative a la camera (apres sa rotation, distance conservee).
        return m.m30() * m.m30() + m.m31() * m.m31() + m.m32() * m.m32() <= labelDistanceSqr;
    }

    /** Traits des trois regles. */
    private static void ticks(VertexConsumer consumer, Matrix4f matrix, Layout layout, float t, float length,
                              int light)
    {
        float h = SpaceMapCube.HALF_SIZE;
        for (int r = 0; r < 3; r++) {
            float[] start = RULER_START[r];
            float[] dir = RULER_DIR[r];
            float[] out = RULER_OUT[r];
            float[] side = RULER_SIDE[r];
            float ox = out[0] * length;
            float oy = out[1] * length;
            float oz = out[2] * length;
            for (int k = 0; k <= layout.ticks; k++) {
                float s = layout.tickPos[k] * h;
                float x = start[0] * h + dir[0] * s;
                float y = start[1] * h + dir[1] * s;
                float z = start[2] * h + dir[2] * s;
                band(consumer, matrix, x, y, z, ox, oy, oz, t * dir[0], t * dir[1], t * dir[2], tickColor, light);
                band(consumer, matrix, x, y, z, ox, oy, oz, t * side[0], t * side[1], t * side[2], tickColor,
                        light);
            }
        }
    }

    /** Etiquettes des graduations (zero omis), au-dela du bout des traits. */
    private void tickLabels(Font font, Matrix4f matrix, Layout layout, MultiBufferSource buffers, int light)
    {
        float h = SpaceMapCube.HALF_SIZE;
        float offset = (TICK_LENGTH + TICK_LABEL_GAP) * size / scale;
        float textScale = TICK_TEXT_SCALE * size;
        for (int r = 0; r < 3; r++) {
            float[] start = RULER_START[r];
            float[] dir = RULER_DIR[r];
            float[] out = RULER_OUT[r];
            for (int k = 1; k <= layout.ticks; k++) {
                float s = layout.tickPos[k] * h;
                placeBillboard(matrix, start[0] * h + dir[0] * s + out[0] * offset,
                        start[1] * h + dir[1] * s + out[1] * offset, start[2] * h + dir[2] * s + out[2] * offset);
                if (!readable(billboard)) continue;
                textMatrix.set(billboard).scale(-textScale, -textScale, textScale);
                font.drawInBatch(layout.tickLabels[k], -0.5f * layout.tickWidths[k], -4f, tickLabelColor, false,
                        textMatrix, buffers, Font.DisplayMode.NORMAL, 0, light);
            }
        }
    }

    // ---------------------------------------------------------------- mise en page (cache)

    /** Cache de la vue (cree au premier dessin). */
    private Layout layoutOf(SpaceMapView view)
    {
        Layout layout = layouts.get(view);
        if (layout == null) {
            layout = new Layout();
            layouts.put(view, layout);
        }
        return layout;
    }

    /** Cache de la carte galactique, reconstruit si la liste d'en-tetes (remplacee a chaque synchro) ou le cadrage change. */
    private Layout layout(SpaceMapView view)
    {
        Layout layout = layoutOf(view);
        List<SystemHeader> headers = view.headers();
        double cx = view.frameX();
        double cy = view.frameY();
        double cz = view.frameZ();
        double half = view.frameHalf();
        if (headers != layout.headers || cx != layout.cx || cy != layout.cy || cz != layout.cz
                || half != layout.half) {
            layout.stars(headers, cx, cy, cz, half, Minecraft.getInstance().font);
        }
        return layout;
    }

    /** Mise en page d'une vue : tout ce qui ne change qu'a la synchronisation. */
    private static final class Layout
    {
        private List<SystemHeader> headers;
        private double cx;
        private double cy;
        private double cz;
        private double half;
        private int count;
        private int[] ids = new int[0];
        private float[] qx = new float[0];
        private float[] qy = new float[0];
        private float[] qz = new float[0];
        private boolean[] inside = new boolean[0];
        /** Rayon du coeur, en blocs a l'echelle par defaut. */
        private float[] radius = new float[0];
        /** Couleur de l'etoile (bord du coeur, halo) et centre eclairci du coeur. */
        private int[] color = new int[0];
        private int[] core = new int[0];
        private String[] names = new String[0];
        private int[] widths = new int[0];

        private double tickHalf = Double.NaN;
        /** Unite des etiquettes des graduations (constante, comparee par identite). */
        private String tickUnit;
        /** Index de la derniere graduation (la graduation 0 est le debut de la regle). */
        private int ticks;
        /** Position de chaque graduation le long de sa regle, en unites du cube {@code [0, 2]}. */
        private final float[] tickPos = new float[MAX_TICKS + 1];
        private final String[] tickLabels = new String[MAX_TICKS + 1];
        private final int[] tickWidths = new int[MAX_TICKS + 1];

        /** Positions locales {@code q = (g - c) / H}, rayons, couleurs et largeurs des noms. */
        void stars(List<SystemHeader> list, double cx, double cy, double cz, double half, Font font)
        {
            headers = list;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.half = half;
            count = list.size();
            if (ids.length < count) {
                ids = new int[count];
                qx = new float[count];
                qy = new float[count];
                qz = new float[count];
                inside = new boolean[count];
                radius = new float[count];
                color = new int[count];
                core = new int[count];
                names = new String[count];
                widths = new int[count];
            }
            float h = SpaceMapCube.HALF_SIZE;
            for (int i = 0; i < count; i++) {
                SystemHeader header = list.get(i);
                ids[i] = header.id();
                qx[i] = (float) ((header.x() - cx) / half) * h;
                qy[i] = (float) ((header.y() - cy) / half) * h;
                qz[i] = (float) ((header.z() - cz) / half) * h;
                float limit = h + INSIDE_EPSILON;
                inside[i] = Math.abs(qx[i]) <= limit && Math.abs(qy[i]) <= limit && Math.abs(qz[i]) <= limit;
                float factor = 1f + 0.25f * (float) Math.log10(header.starLuminosity());
                radius[i] = STAR_RADIUS * Mth.clamp(factor, STAR_MIN_FACTOR, STAR_MAX_FACTOR);
                color[i] = header.starColor() | 0xFF000000;
                core[i] = mixWhite(color[i], CORE_WHITE);
                names[i] = header.name();
                widths[i] = font.width(names[i]);
            }
            for (int i = count; i < names.length; i++) names[i] = null;
        }

        /** Graduations de demi-etendue {@code half} et d'unite {@code unit}, recalculees si elles changent. */
        void ensureGraduations(double half, String unit, Font font)
        {
            if (half == tickHalf && unit == tickUnit) return;
            tickHalf = half;
            tickUnit = unit;
            double step = SpaceSystemScale.nice(half / 4.0);
            ticks = (int) Math.min(MAX_TICKS, Math.floor(2.0 * half / step + 1e-6));
            for (int k = 0; k <= ticks; k++) {
                double value = k * step;
                tickPos[k] = (float) (value / half);
                tickLabels[k] = format(value, step) + unit;
                tickWidths[k] = font.width(tickLabels[k]);
            }
        }

        /** Valeur sans decimale si le pas est entier, sinon autant de decimales que le pas en demande (virgule). */
        private static String format(double value, double step)
        {
            if (step >= 1.0) return Long.toString(Math.round(value));
            int decimals = (int) Math.ceil(-Math.log10(step) - 1e-9);
            return String.format(Locale.ROOT, "%." + decimals + "f", value).replace('.', ',');
        }

        // ------------------------------------------------------------ vue systeme

        private SpaceSystem system;
        private double systemHalf = Double.NaN;
        private int bodyCount;
        private final int[] bodyIds = new int[SpaceLimits.MAX_BODIES];
        /** Indice du parent, {@code -1} pour un corps sans orbite (centre). */
        private final int[] parent = new int[SpaceLimits.MAX_BODIES];
        private final boolean[] star = new boolean[SpaceLimits.MAX_BODIES];
        /** Rayon affiche, en unites du cube. */
        private final float[] bodyRadius = new float[SpaceLimits.MAX_BODIES];
        private final int[] bodyColor = new int[SpaceLimits.MAX_BODIES];
        private final int[] bodyCore = new int[SpaceLimits.MAX_BODIES];
        private final String[] bodyNames = new String[SpaceLimits.MAX_BODIES];
        private final int[] bodyWidths = new int[SpaceLimits.MAX_BODIES];
        private final int[] orbitColor = new int[SpaceLimits.MAX_BODIES];
        /** Orbite dessinee en entier (apoapside d'au plus {@link #FULL_ORBIT_RATIO} demi-etendues de la vue). */
        private final boolean[] orbitFull = new boolean[SpaceLimits.MAX_BODIES];
        /** Points des orbites relatifs au parent (unites du cube, a l'echelle de la vue), {@code SEGMENTS + 1} par corps. */
        private final float[] orbitPoints = new float[SpaceLimits.MAX_BODIES * (ORBIT_SEGMENTS + 1) * 3];
        /** Direction unitaire dans le plan de l'orbite, perpendiculaire a la courbe, en chaque point. */
        private final float[] orbitSides = new float[SpaceLimits.MAX_BODIES * (ORBIT_SEGMENTS + 1) * 3];
        /** Normale unitaire du plan de chaque orbite. */
        private final float[] orbitNormals = new float[SpaceLimits.MAX_BODIES * 3];
        private final Vector3d point = new Vector3d();
        private final Vector3d normal = new Vector3d();
        private final Quaterniond orientation = new Quaterniond();

        /**
         * Corps du systeme montre (rayons affiches, couleurs, noms) et courbes des orbites relativement au parent,
         * recalcules seulement quand la copie (nouvelle instance a chaque synchro) ou la demi-etendue change.
         */
        void ensureBodies(SpaceSystem shown, double half, Font font)
        {
            if (shown == system && half == systemHalf) return;
            system = shown;
            systemHalf = half;
            double unit = SpaceSystemScale.unit(half);
            List<SpaceBody> bodies = shown.bodies();
            bodyCount = Math.min(bodies.size(), SpaceLimits.MAX_BODIES);
            for (int i = 0; i < bodyCount; i++) {
                SpaceBody body = bodies.get(i);
                bodyIds[i] = body.id();
                SpaceOrbit orbit = body.orbit();
                parent[i] = orbit != null ? shown.parentIndex(i) : -1;
                star[i] = body.kind() == SpaceBodyKind.STAR;
                bodyRadius[i] = (float) SpaceSystemScale.displayRadius(body.radius(), half);
                bodyColor[i] = body.color() | 0xFF000000;
                bodyCore[i] = mixWhite(bodyColor[i], star[i] ? CORE_WHITE : 0.25f);
                orbitColor[i] = ORBIT_ALPHA << 24 | body.color() & 0xFFFFFF;
                bodyNames[i] = body.name();
                bodyWidths[i] = font.width(bodyNames[i]);
                orbitFull[i] = parent[i] >= 0 && orbit.apoapsis() <= FULL_ORBIT_RATIO * half;
                if (parent[i] >= 0) curve(i, orbit, unit, orbitFull[i]);
            }
            for (int i = bodyCount; i < bodyNames.length; i++) bodyNames[i] = null;
        }

        /**
         * Normale du plan de l'orbite et, si elle est dessinee en entier, ses points (anomalie excentrique reguliere)
         * et directions laterales.
         */
        private void curve(int i, SpaceOrbit orbit, double unit, boolean full)
        {
            orientation.rotationYXZ(-Math.toRadians(orbit.yaw()), Math.toRadians(orbit.pitch()),
                    Math.toRadians(orbit.roll()));
            orientation.transform(normal.set(0.0, 1.0, 0.0));
            orbitNormals[i * 3] = (float) normal.x;
            orbitNormals[i * 3 + 1] = (float) normal.y;
            orbitNormals[i * 3 + 2] = (float) normal.z;
            if (!full) return;
            int base = i * (ORBIT_SEGMENTS + 1) * 3;
            for (int k = 0; k <= ORBIT_SEGMENTS; k++) {
                SpaceOrbits.orbitPoint(orbit, k * SpaceUnits.TWO_PI / ORBIT_SEGMENTS, point);
                orbitPoints[base + k * 3] = (float) (point.x * unit);
                orbitPoints[base + k * 3 + 1] = (float) (point.y * unit);
                orbitPoints[base + k * 3 + 2] = (float) (point.z * unit);
            }
            // Courbe fermee : le point SEGMENTS est le point 0.
            lateral(orbitPoints, orbitSides, base, ORBIT_SEGMENTS, true, normal.x, normal.y, normal.z, point);
        }

        private static int mixWhite(int argb, float white)
        {
            int r = (int) ((argb >> 16 & 0xFF) * (1f - white) + 255f * white);
            int g = (int) ((argb >> 8 & 0xFF) * (1f - white) + 255f * white);
            int b = (int) ((argb & 0xFF) * (1f - white) + 255f * white);
            return argb & 0xFF000000 | r << 16 | g << 8 | b;
        }
    }

    // ---------------------------------------------------------------- primitives

    /** Douze aretes, deux bandes croisees chacune. */
    private static void edges(VertexConsumer consumer, Matrix4f matrix, float t, int light)
    {
        float h = SpaceMapCube.HALF_SIZE;
        for (int i = 0; i < 3; i++) {
            float[] a = AXES[i];
            float[] u = AXES[(i + 1) % 3];
            float[] v = AXES[(i + 2) % 3];
            for (float[] corner : CORNERS) {
                // Debut de l'arete : coordonnees fixes sur u et v, -h sur a.
                float x = h * (corner[0] * u[0] + corner[1] * v[0] - a[0]);
                float y = h * (corner[0] * u[1] + corner[1] * v[1] - a[1]);
                float z = h * (corner[0] * u[2] + corner[1] * v[2] - a[2]);
                float dx = 2f * h * a[0];
                float dy = 2f * h * a[1];
                float dz = 2f * h * a[2];
                band(consumer, matrix, x, y, z, dx, dy, dz, t * u[0], t * u[1], t * u[2], edgeColor, light);
                band(consumer, matrix, x, y, z, dx, dy, dz, t * v[0], t * v[1], t * v[2], edgeColor, light);
            }
        }
    }

    /** Six faces teintees (sans profondeur). */
    private static void faces(VertexConsumer consumer, Matrix4f matrix, int light)
    {
        float h = SpaceMapCube.HALF_SIZE;
        for (int i = 0; i < 3; i++) {
            float[] n = AXES[i];
            float[] u = AXES[(i + 1) % 3];
            float[] v = AXES[(i + 2) % 3];
            for (int side = -1; side <= 1; side += 2) {
                float cx = side * h * n[0];
                float cy = side * h * n[1];
                float cz = side * h * n[2];
                vertex(consumer, matrix, cx + h * (-u[0] - v[0]), cy + h * (-u[1] - v[1]), cz + h * (-u[2] - v[2]),
                        faceColor, light);
                vertex(consumer, matrix, cx + h * (u[0] - v[0]), cy + h * (u[1] - v[1]), cz + h * (u[2] - v[2]),
                        faceColor, light);
                vertex(consumer, matrix, cx + h * (u[0] + v[0]), cy + h * (u[1] + v[1]), cz + h * (u[2] + v[2]),
                        faceColor, light);
                vertex(consumer, matrix, cx + h * (-u[0] + v[0]), cy + h * (-u[1] + v[1]), cz + h * (-u[2] + v[2]),
                        faceColor, light);
            }
        }
    }

    /** Bande de {@code p} a {@code p + d}, de demi-largeur {@code w}. */
    private static void band(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float dx, float dy,
                             float dz, float wx, float wy, float wz, int color, int light)
    {
        vertex(consumer, matrix, x - wx, y - wy, z - wz, color, light);
        vertex(consumer, matrix, x + dx - wx, y + dy - wy, z + dz - wz, color, light);
        vertex(consumer, matrix, x + dx + wx, y + dy + wy, z + dz + wz, color, light);
        vertex(consumer, matrix, x + wx, y + wy, z + wz, color, light);
    }

    /** Disque plein de rayon {@code r} dans le plan XY du billboard : centre {@code center}, bord {@code rim}. */
    private static void disc(VertexConsumer consumer, Matrix4f m, float r, int center, int rim, int light)
    {
        for (int i = 0; i < SEGMENTS; i += 2) {
            vertex(consumer, m, 0f, 0f, 0f, center, light);
            vertex(consumer, m, r * COS[i], r * SIN[i], 0f, rim, light);
            vertex(consumer, m, r * COS[i + 1], r * SIN[i + 1], 0f, rim, light);
            vertex(consumer, m, r * COS[i + 2], r * SIN[i + 2], 0f, rim, light);
        }
    }

    /** Halo : couronne de {@code r} a {@code HALO_FACTOR . r}, alpha {@code alpha} vers 0. */
    private static void halo(VertexConsumer consumer, Matrix4f m, float r, int color, int alpha, int light)
    {
        int inner = alpha << 24 | color & 0xFFFFFF;
        int outer = color & 0xFFFFFF;
        float big = r * HALO_FACTOR;
        for (int i = 0; i < SEGMENTS; i++) {
            vertex(consumer, m, r * COS[i], r * SIN[i], 0f, inner, light);
            vertex(consumer, m, big * COS[i], big * SIN[i], 0f, outer, light);
            vertex(consumer, m, big * COS[i + 1], big * SIN[i + 1], 0f, outer, light);
            vertex(consumer, m, r * COS[i + 1], r * SIN[i + 1], 0f, inner, light);
        }
    }

    /** Anneau fin de rayon {@code r} et de demi-epaisseur {@code thickness} dans le plan XY du billboard. */
    private static void ring(VertexConsumer consumer, Matrix4f m, float r, float thickness, int color, int light)
    {
        float in = r - thickness;
        float out = r + thickness;
        for (int i = 0; i < SEGMENTS; i++) {
            vertex(consumer, m, in * COS[i], in * SIN[i], 0f, color, light);
            vertex(consumer, m, out * COS[i], out * SIN[i], 0f, color, light);
            vertex(consumer, m, out * COS[i + 1], out * SIN[i + 1], 0f, color, light);
            vertex(consumer, m, in * COS[i + 1], in * SIN[i + 1], 0f, color, light);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, int color,
                               int light)
    {
        matrix.transformPosition(x, y, z, VERTEX);
        consumer.vertex(VERTEX.x, VERTEX.y, VERTEX.z)
                .color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >>> 24).uv2(light).endVertex();
    }

    // ---------------------------------------------------------------- RenderType

    /**
     * {@code RenderType} local (sous-classe pour acceder aux etats proteges de {@code RenderStateShard} ; jamais
     * instanciee) : comme {@link HoloRenderTypes#TRANSLUCENT_QUADS} mais <b>sans ecriture de profondeur</b> (test de
     * profondeur garde), pour les faces et les halos qui ne doivent rien cacher.
     */
    private static final class GlowType extends RenderType
    {
        static final RenderType GLOW_QUADS = create(HoloLib.MODID + ":space_map_glow_quads",
                DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_LIGHTMAP_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private GlowType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                         boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear)
        {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
