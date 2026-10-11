package com.amynna.hololib.client.render.modules.dimension.space;

import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.client.holo.HoloModes;
import com.amynna.hololib.client.render.modules.dimension.space.warp.SpaceWarpConvergence;
import com.amynna.hololib.client.render.modules.dimension.space.warp.SpaceWarpEffect;
import com.amynna.hololib.client.render.modules.dimension.space.warp.SpaceWarpTimeline;
import com.amynna.hololib.client.render.sky.SkyRenderContext;
import com.amynna.hololib.client.render.sky.SkyRenderer;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.math.SpacePose;
import com.amynna.hololib.modules.dimension.space.math.SpaceSkyFrame;
import com.amynna.hololib.modules.dimension.space.math.SpaceSpin;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceGalaxy.BackgroundStar;
import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import com.amynna.hololib.modules.dimension.space.model.SpaceStarField;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Arrays;
import java.util.List;

/**
 * Ciel du module {@code hololib:space_environment} : remplace visuellement le ciel vanilla par le systeme courant vu
 * depuis la pose du monde ({@code docs/space/04-mathematiques.md} par. 2 a 4). Dessine des que le miroir de
 * dimension contient un Environment ; sans Environment, le ciel vanilla reste intact. Tant que l'Environment n'est pas
 * synchronise (bloc froid de la revision annoncee pas encore recu, T15b), seul le fond est dessine.
 * <p>
 * Ordre de dessin (profondeur de la passe du ciel, chaque objet plus proche que le precedent) :
 * <ol>
 *     <li>fond : sphere opaque noire, faces tournees vers l'interieur, a {@link #BACKGROUND_RADIUS} blocs ;</li>
 *     <li>fond etoile procedural (T31, {@link SpaceStarField} de la graine de l'Environment) : environ 4 000 petits
 *     quads a {@link #STAR_PATTERN_RADIUS} blocs, fixes dans le repere du systeme (une seule matrice par frame : celle
 *     du ciel tournee par {@code Q^-1}) ;</li>
 *     <li>etoiles de fond (les autres systemes) : quads de taille angulaire fixe a {@link #STAR_FIELD_RADIUS}
 *     blocs ;</li>
 *     <li>corps du systeme tries du plus lointain au plus proche, a des rayons de dessin distincts repartis
 *     geometriquement de {@link #FAR_BODY_RADIUS} a {@link #NEAR_BODY_RADIUS} blocs.</li>
 * </ol>
 * Un corps est un maillage polaire centre sur sa direction : ses sommets sont poses <b>sur la sphere</b> de son rayon
 * de dessin (calotte d'angle egal a son rayon angulaire apparent, jusqu'a 90 degres : au plancher d'altitude, une
 * planete est une demi-sphere de ciel bordee par un horizon) plutot que sur un plan. Le long d'un rayon de vue, la
 * profondeur est alors celle du rayon de dessin : l'occultation entre corps reste exacte quelle que soit leur taille,
 * et aucun sommet ne depasse le plan lointain. Les corps qui ne sont pas des etoiles (planetes, lunes, objets mineurs
 * depuis T16 : dessines a l'identique) recoivent la normale <b>exacte</b> de la sphere qu'ils representent au point
 * vise par chaque sommet, perspective comprise (phases ; de pres on ne voit qu'une calotte du corps, dont les
 * normales restent proches de la verticale), eclaires par l'etoile la plus proche ; les
 * etoiles sont en pleine lumiere, avec un halo translucide. Sous {@link #MIN_ANGULAR_RADIUS}, un corps devient un
 * point de taille fixe dont l'eclat decroit.
 * <p>
 * <b>Textures et rotation propre</b> (T30, D21) : le disque d'un corps est texture des que sa texture est chargee
 * ({@link SpaceBodyTextureCache}, demandee pour tous les corps du systeme courant ; avant : couleur unie, comme un
 * point). Chaque sommet du disque recoit les coordonnees de texture du point de la sphere qu'il vise (la meme normale
 * exacte que l'eclairage), ramene dans les axes du corps par sa rotation propre au temps d'affichage
 * ({@link SpaceSpin}, {@code docs/space/04-mathematiques.md} par. 10) : latitude {@code asin(y)}, longitude
 * {@code atan2(-z, x)}, {@code u = 0,5 + longitude / 2 pi} (longitude 0 au centre de l'image, C85),
 * {@code v = 0,5 - latitude / pi}. Le maillage reste le disque polaire tourne vers l'oeil (resolution la ou on regarde,
 * de pres comme de loin) ; la couture de la longitude est traitee quad par quad (les {@code u} d'un quad qui
 * l'enjambe passent au-dela de 1, texture repetee en {@code u}). Planetes : texture eclairee par sommet comme avant ;
 * etoiles : texture teintee par la couleur du corps, eclaircie selon sa luminance moyenne, sans eclairage, halo
 * attenue par l'eclat moyen du disque ; un point prend la couleur moyenne de sa texture.
 * <p>
 * <b>Distorsion du saut</b> (T35, D23, C73 ; package {@code warp}) : chronologie {@link SpaceWarpTimeline} lue une
 * fois par frame. Pendant la convergence (premiers ticks du voyage), le ciel dessine le fond etoile procedural et les
 * etoiles des autres systemes glissant vers la cible ({@link SpaceWarpConvergence}, fraction fournie par la
 * chronologie ; corps inchanges) ; pendant le voyage, il n'est plus dessine (sauf flash : scene remplacee par le
 * blanc) ; {@link SpaceWarpEffect} dessine par-dessus, dans la meme passe, la scene du voyage et les flashs blancs, et
 * accorde le brouillard au degrade (hors fluides).
 * <p>
 * <b>Niveaux de detail</b> (D9) : quatre maillages polaires precalcules, de 24 x 6 a 192 x 48 (secteurs x anneaux),
 * choisis par corps selon son rayon apparent a l'ecran ({@code tan alpha} fois l'echelle verticale de la projection :
 * le FOV et le zoom comptent), avec hysteresis ({@link #LOD_DOWN}) : la silhouette reste ronde (fleche du polygone
 * sous un demi-pixel environ) sans saut visible au changement de niveau.
 * <p>
 * Temps : la pose et les positions des corps sont celles de l'affichage du module, au temps de son horloge
 * d'affichage ({@code displayDays(partialTick)}, en retard sur le niveau et sans saut), jamais le temps de jeu de
 * Minecraft. Tous les tableaux, vecteurs et le maillage sont alloues
 * une fois ; fil de rendu uniquement.
 */
public final class SpaceSkyRenderer implements SkyRenderer
{
    // ---------------------------------------------------------------- rayons de dessin (blocs)

    /** Fond opaque : sous le plan lointain (128 blocs au minimum), derriere tout le reste. */
    static final float BACKGROUND_RADIUS = 120f;
    /**
     * Fond etoile procedural (T31) : devant le fond (les faces de la sphere grossiere descendent a environ 115 blocs),
     * derriere les etoiles des systemes.
     */
    static final float STAR_PATTERN_RADIUS = 112.5f;
    /** Etoiles de fond : devant le fond etoile procedural. */
    static final float STAR_FIELD_RADIUS = 110f;
    /** Corps le plus lointain. */
    static final float FAR_BODY_RADIUS = 105f;
    /** Corps le plus proche. */
    static final float NEAR_BODY_RADIUS = 50f;
    /** Marge sous le plan lointain si celui-ci etait plus court que le fond (garde-fou). */
    private static final float FAR_PLANE_MARGIN = 0.95f;

    // ---------------------------------------------------------------- maillages

    /** Fond : tranches en longitude, piles en latitude. */
    private static final int BACKGROUND_SLICES = 16;
    private static final int BACKGROUND_STACKS = 8;
    /**
     * Disque polaire d'un corps, par niveau de detail : secteurs et anneaux (un anneau pour quatre secteurs). Cout du
     * plus fin : 9 216 quads (36 864 sommets), pour un seul gros corps en pratique.
     */
    private static final int[] LOD_SECTORS = {24, 48, 96, 192};
    private static final int[] LOD_RINGS = {6, 12, 24, 48};
    /**
     * Seuils de montee d'un niveau : rayon apparent a l'ecran ({@code tan alpha} x {@code m11} de la projection, en
     * demi-hauteurs d'ecran). A 70 degres de FOV : 4,0, 15,6 et 44,4 degres de rayon angulaire.
     */
    private static final double[] LOD_UP = {0.1, 0.4, 1.4};
    /** Hysteresis : un niveau ne redescend que sous ce facteur fois son seuil de montee. */
    private static final double LOD_DOWN = 0.8;
    /** Rayon angulaire pris en compte pour la taille a l'ecran (evite {@code tan(90 deg)}). */
    private static final double LOD_MAX_ALPHA = Math.toRadians(89.0);
    /** Echelle verticale de la projection a defaut ({@code 1 / tan(35 deg)}, FOV 70). */
    private static final double DEFAULT_PROJECTION_SCALE = 1.0 / Math.tan(Math.toRadians(35.0));
    /** Anneaux et secteurs du niveau le plus fin (taille des tableaux par corps). */
    private static final int MAX_RINGS = 48;
    private static final int MAX_SECTORS = 192;
    private static final float INV_TWO_PI = (float) (0.5 / Math.PI);
    private static final float INV_PI = (float) (1.0 / Math.PI);
    private static final float HALF_PI = (float) (0.5 * Math.PI);
    private static final float PI = (float) Math.PI;
    /** Anneaux du halo d'une etoile (couronne). */
    private static final int HALO_RINGS = 4;

    // ---------------------------------------------------------------- apparence

    /** Couleur du brouillard et du fond de l'ecran dans l'espace (quasi noire). */
    private static final float FOG_RED = 0.01f;
    private static final float FOG_GREEN = 0.01f;
    private static final float FOG_BLUE = 0.02f;
    /** Eclairage minimal de la face nocturne d'une planete. */
    private static final float AMBIENT = 0.06f;
    /** Rayon angulaire minimal d'un corps (radians, 0,12 degre) : en dessous, c'est un point de cette taille. */
    private static final double MIN_ANGULAR_RADIUS = Math.toRadians(0.12);
    /** Eclat minimal d'un point (planete ou lune minuscule), avant eclairage. */
    private static final float MIN_POINT_BRIGHTNESS = 0.35f;
    /**
     * Halo d'une etoile : rayon angulaire relatif au disque, plafond (radians), opacite au bord du disque. Le plafond
     * de 100 degres garde son sens avec un disque jusqu'a 90 degres (D9) : au plancher du Soleil (81,9 degres), une
     * couronne de 18 degres ; jamais moins de 10 degres.
     */
    private static final double HALO_SCALE = 2.5;
    private static final double MAX_HALO_ANGLE = Math.toRadians(100.0);
    private static final float HALO_ALPHA = 0.55f;
    /** Etoiles de fond : taille angulaire (diametre, degres) de la plus faible a la plus brillante. */
    private static final double STAR_MIN_SIZE = 0.15;
    private static final double STAR_MAX_SIZE = 0.4;
    /** Etoiles de fond : bornes de {@code log10(luminosite / distance^2)} (luminosite solaire, al). */
    private static final double STAR_LOG_MIN = -5.0;
    private static final double STAR_LOG_MAX = 0.0;
    /** Etoiles de fond : eclat de la plus faible (la plus brillante est a 1). */
    private static final float STAR_MIN_BRIGHTNESS = 0.3f;
    /**
     * Fond etoile procedural (T31) : taille angulaire (diametre, degres) et eclat de la plus faible a la plus brillante
     * ({@link SpaceStarField#brightness}, eleve a {@link #PATTERN_GAMMA}) : plus petites et plus ternes que les etoiles
     * des systemes. Au FOV 70 en 1080p, un pixel fait environ 0,065 degre : de 1,7 a 3,7 pixels.
     */
    private static final double PATTERN_MIN_SIZE = 0.11;
    private static final double PATTERN_MAX_SIZE = 0.24;
    private static final float PATTERN_MIN_BRIGHTNESS = 0.22f;
    private static final float PATTERN_MAX_BRIGHTNESS = 0.9f;
    private static final float PATTERN_GAMMA = 0.7f;

    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    // ---------------------------------------------------------------- tables precalculees

    /**
     * Par niveau de detail : cosinus / sinus des secteurs du disque, {@code secteurs + 1} valeurs (la derniere ferme
     * le tour).
     */
    private static final float[][] SECTOR_COS = new float[LOD_SECTORS.length][];
    private static final float[][] SECTOR_SIN = new float[LOD_SECTORS.length][];
    /**
     * Par niveau de detail : rayon normalise de chaque anneau du disque, {@code rho_k = sin(k pi / 2 anneaux)}
     * (angle de vue {@code rho_k alpha}) : anneaux resserres vers le bord, ou la normale de sphere varie le plus vite
     * (silhouette et limbe), et pas regulier au centre (terminateur).
     */
    private static final float[][] RING_RHO = new float[LOD_RINGS.length][];
    /** Sommets unitaires du fond, {@code (SLICES + 1) x (STACKS + 1)}. */
    private static final float[] BACKGROUND_X;
    private static final float[] BACKGROUND_Y;
    private static final float[] BACKGROUND_Z;

    static
    {
        for (int level = 0; level < LOD_SECTORS.length; level++) {
            int sectors = LOD_SECTORS[level];
            int rings = LOD_RINGS[level];
            SECTOR_COS[level] = new float[sectors + 1];
            SECTOR_SIN[level] = new float[sectors + 1];
            RING_RHO[level] = new float[rings + 1];
            for (int j = 0; j <= sectors; j++) {
                double angle = 2.0 * Math.PI * j / sectors;
                SECTOR_COS[level][j] = (float) Math.cos(angle);
                SECTOR_SIN[level][j] = (float) Math.sin(angle);
            }
            for (int k = 0; k <= rings; k++) RING_RHO[level][k] = (float) Math.sin(k * Math.PI / (2.0 * rings));
        }
        int count = (BACKGROUND_SLICES + 1) * (BACKGROUND_STACKS + 1);
        BACKGROUND_X = new float[count];
        BACKGROUND_Y = new float[count];
        BACKGROUND_Z = new float[count];
        for (int j = 0; j <= BACKGROUND_STACKS; j++) {
            double latitude = -0.5 * Math.PI + Math.PI * j / BACKGROUND_STACKS;
            for (int i = 0; i <= BACKGROUND_SLICES; i++) {
                double longitude = 2.0 * Math.PI * i / BACKGROUND_SLICES;
                int index = j * (BACKGROUND_SLICES + 1) + i;
                BACKGROUND_X[index] = (float) (Math.cos(latitude) * Math.cos(longitude));
                BACKGROUND_Y[index] = (float) Math.sin(latitude);
                BACKGROUND_Z[index] = (float) (Math.cos(latitude) * Math.sin(longitude));
            }
        }
    }

    // ---------------------------------------------------------------- etat de la frame (reutilise)

    /** Environment trouve par {@link #shouldRender}, utilise par {@link #render} de la meme frame. */
    private SpaceEnvironmentModule environment;
    /** Distorsion du saut (T35) : scene du voyage, flashs, brouillard ; convergence des etoiles placee par le ciel. */
    private final SpaceWarpEffect warp = new SpaceWarpEffect();
    private final SpaceWarpConvergence convergence = new SpaceWarpConvergence();
    private final SpacePose pose = new SpacePose();
    private final SpaceSkyFrame frame = new SpaceSkyFrame();
    private final Vector3d[] positions = SpaceOrbits.newPositionArray();
    /** Par corps (indice du systeme) : direction monde, distance (km). */
    private final Vector3f[] directions = new Vector3f[SpaceLimits.MAX_BODIES];
    private final double[] distances = new double[SpaceLimits.MAX_BODIES];
    /** Indices des corps visibles, du plus lointain au plus proche. */
    private final int[] order = new int[SpaceLimits.MAX_BODIES];
    /** Niveau de detail retenu par corps (indice du systeme), {@code -1} = aucun, et id du corps qui l'a fixe. */
    private final int[] lodLevels = new int[SpaceLimits.MAX_BODIES];
    private final int[] lodIds = new int[SpaceLimits.MAX_BODIES];
    /** Echelle verticale de la projection de la frame ({@code m11}). */
    private double projectionScale = DEFAULT_PROJECTION_SCALE;
    /** Maillage du corps en cours : tables de son niveau de detail. */
    private float[] sectorCos = SECTOR_COS[0];
    private float[] sectorSin = SECTOR_SIN[0];
    private float[] ringRho = RING_RHO[0];
    private int sectors = LOD_SECTORS[0];
    private int rings = LOD_RINGS[0];
    /** Cosinus / sinus de l'angle de vue de chaque anneau (disque, puis halo) du corps en cours. */
    private final float[] ringCos = new float[MAX_RINGS + 1];
    private final float[] ringSin = new float[MAX_RINGS + 1];
    /**
     * Normale de sphere au point vise par chaque anneau du corps en cours, dans la base {@code (e, -w)} ({@code e} =
     * direction radiale du secteur) : composante radiale et composante vers l'oeil.
     */
    private final float[] ringRadial = new float[MAX_RINGS + 1];
    private final float[] ringDepth = new float[MAX_RINGS + 1];
    private final float[] haloCos = new float[HALO_RINGS + 1];
    /** Opacite du halo de l'etoile en cours relative a {@link #HALO_ALPHA} : eclat moyen de son disque (1 sans texture). */
    private float haloGain = 1f;
    private final float[] haloSin = new float[HALO_RINGS + 1];
    /** Base du disque en cours : {@code bu x bv = -w} (tournee vers l'oeil, sens direct vu de l'oeil). */
    private final Vector3f bu = new Vector3f();
    private final Vector3f bv = new Vector3f();
    private final Vector3f light = new Vector3f();
    private final Vector3f direction = new Vector3f();
    private final Vector3f transformed = new Vector3f();
    /** Textures des astres (partage avec la Space Map) et texture de chaque corps (indice du systeme) pour la frame. */
    private final SpaceBodyTextureCache textures = SpaceBodyTextureCache.instance();
    private final SpaceBodyTextureCache.Texture[] bodyTextures = new SpaceBodyTextureCache.Texture[SpaceLimits.MAX_BODIES];
    /** Temps d'affichage de la frame (jours simules), celui des positions et de la rotation propre. */
    private double days;
    /** Rotation propre du corps en cours, puis passage axes du monde -> axes du corps. */
    private final Quaterniond spin = new Quaterniond();
    private final Quaterniond toBody = new Quaterniond();
    private final Vector3d rotated = new Vector3d();
    /** Base {@code (bu, bv, w)} du disque en cours exprimee dans les axes du corps (coordonnees de texture). */
    private final Vector3f bodyU = new Vector3f();
    private final Vector3f bodyV = new Vector3f();
    private final Vector3f bodyW = new Vector3f();
    /** Par sommet du disque texture en cours ({@code anneau x (secteurs + 1) + secteur}) : u, v, ombrage. */
    private final float[] vertexU = new float[(MAX_RINGS + 1) * (MAX_SECTORS + 1)];
    private final float[] vertexV = new float[(MAX_RINGS + 1) * (MAX_SECTORS + 1)];
    private final float[] vertexShade = new float[(MAX_RINGS + 1) * (MAX_SECTORS + 1)];
    /** Eclairage du corps en cours, dans la base {@code (bu, bv, -w)}. */
    private float lightU;
    private float lightV;
    private float lightW;
    /**
     * Fond etoile procedural (T31) : champ dont les tableaux ci-dessous sont faits ({@code null} avant le premier),
     * quatre coins par etoile sur la sphere unite dans les axes du systeme ({@code 12} flottants), couleur deja
     * multipliee par l'eclat ({@code 3} flottants). Refaits seulement quand le champ change (nouvelle graine).
     */
    private SpaceStarField pattern;
    private float[] patternCorners = new float[0];
    private float[] patternColors = new float[0];
    /** Demi-taille de chaque etoile du fond etoile (tangente), pour la convergence du saut. */
    private float[] patternHalf = new float[0];
    /** Matrice de la frame pour le fond etoile : celle du ciel, tournee systeme -> monde ({@code Q^-1}), echelle. */
    private final Matrix4f patternMatrix = new Matrix4f();
    private final Quaternionf patternRotation = new Quaternionf();

    public SpaceSkyRenderer()
    {
        for (int i = 0; i < directions.length; i++) directions[i] = new Vector3f();
        Arrays.fill(lodLevels, -1);
    }

    // ---------------------------------------------------------------- SkyRenderer

    @Override
    public boolean shouldRender(SkyRenderContext ctx)
    {
        // Televersements termines et liberation des textures inutilisees, meme hors d'une dimension spatiale.
        textures.beginFrame();
        environment = find();
        return environment != null;
    }

    /**
     * Fond, puis, si l'Environment est synchronise (bloc froid connu), etoiles de fond et corps (pas pendant la scene
     * du voyage ni sous un flash entierement blanc) ; enfin la distorsion du saut ({@link SpaceWarpEffect}) par-dessus.
     */
    @Override
    public void render(PoseStack stack, MultiBufferSource buffers, SkyRenderContext ctx)
    {
        SpaceEnvironmentModule env = environment;
        environment = null;
        if (env == null) return;
        Matrix4f m = stack.last().pose();
        float partialTick = ctx.partialTick();
        float scale = Math.min(1f, ctx.farPlane() * FAR_PLANE_MARGIN / BACKGROUND_RADIUS);
        SpaceWarpTimeline timeline = SpaceWarpTimeline.at(env, partialTick);
        if (!timeline.skyHidden()) drawSky(buffers, m, env, partialTick, scale, timeline);
        warp.render(buffers, m, timeline, scale);
    }

    /**
     * Ciel du systeme courant : fond, puis fond etoile procedural, etoiles de fond et corps si le bloc froid est
     * connu ; pendant la convergence du saut, les etoiles glissent vers la cible.
     */
    private void drawSky(MultiBufferSource buffers, Matrix4f m, SpaceEnvironmentModule env, float partialTick,
                         float scale, SpaceWarpTimeline timeline)
    {
        drawBackground(buffers, m, BACKGROUND_RADIUS * scale);
        SpaceSystem system = env.synced() ? env.currentSystem() : null;
        if (system == null) return;
        frame.set(env.pose(partialTick, pose));
        double m11 = Math.abs(RenderSystem.getProjectionMatrix().m11());
        projectionScale = Double.isFinite(m11) && m11 > 0.0 ? m11 : DEFAULT_PROJECTION_SCALE;
        days = env.displayDays(partialTick);

        boolean converging = timeline.phase() == SpaceWarpTimeline.Phase.CONVERGE && timeline.convergence() > 0f;
        if (converging) {
            Vector3fc t = timeline.target();
            convergence.set(t.x(), t.y(), t.z(), timeline.convergence(), timeline.convergenceTail());
        }
        drawStarPattern(buffers, m, env.starField(), STAR_PATTERN_RADIUS * scale, converging);
        if (converging) {
            Vector3fc v = timeline.vanishing();
            convergence.set(v.x(), v.y(), v.z(), timeline.convergence(), timeline.convergenceTail());
        }
        drawBackgroundStars(buffers, m, env.backgroundStars(), system, STAR_FIELD_RADIUS * scale, converging);
        drawBodies(buffers, m, system, env.bodyPositions(partialTick, positions), scale);
    }

    /**
     * Couleur spatiale quasi noire hors fluides ; pendant la distorsion du saut, couleur du degrade dans le regard,
     * melangee vers le blanc des flashs ({@link SpaceWarpEffect#fogColor}).
     */
    @Override
    public void fogColor(Camera camera, float partialTick, Vector3f rgb)
    {
        if (camera.getFluidInCamera() != FogType.NONE) return;
        SpaceEnvironmentModule env = find();
        if (env == null) return;
        rgb.set(FOG_RED, FOG_GREEN, FOG_BLUE);
        warp.fogColor(SpaceWarpTimeline.at(env, partialTick), camera.getLookVector(), rgb);
    }

    /** Environment du miroir de dimension du joueur local, ou {@code null}. */
    private static SpaceEnvironmentModule find()
    {
        return HoloModes.find(ClientHoloWorld.find(HoloScope.DIMENSION), SpaceEnvironmentModule.class);
    }

    // ---------------------------------------------------------------- fond

    /** Sphere noire opaque, faces vers l'interieur ({@code SOLID_QUADS} elimine les faces arriere). */
    private void drawBackground(MultiBufferSource buffers, Matrix4f m, float radius)
    {
        VertexConsumer vc = buffers.getBuffer(HoloRenderTypes.SOLID_QUADS);
        int row = BACKGROUND_SLICES + 1;
        for (int j = 0; j < BACKGROUND_STACKS; j++) {
            for (int i = 0; i < BACKGROUND_SLICES; i++) {
                // (i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1) : normale (d longitude x d latitude) vers le centre.
                backgroundVertex(vc, m, j * row + i, radius);
                backgroundVertex(vc, m, j * row + i + 1, radius);
                backgroundVertex(vc, m, (j + 1) * row + i + 1, radius);
                backgroundVertex(vc, m, (j + 1) * row + i, radius);
            }
        }
    }

    private void backgroundVertex(VertexConsumer vc, Matrix4f m, int index, float radius)
    {
        vertex(vc, m, BACKGROUND_X[index] * radius, BACKGROUND_Y[index] * radius, BACKGROUND_Z[index] * radius,
                0f, 0f, 0f, 1f);
    }

    // ---------------------------------------------------------------- fond etoile procedural (T31)

    /**
     * Fond etoile procedural : quads precalcules dans les axes du systeme ({@link #preparePattern}), dessines avec la
     * matrice du ciel tournee par l'inverse de l'orientation du monde ({@code p_W = Q^-1 p_S}, comme
     * {@link SpaceSkyFrame#rotateToWorld}) et mise a l'echelle {@code radius} : fixe dans le repere du systeme, il ne
     * tourne qu'avec l'orientation du monde. Opaque ({@code SOLID_QUADS}), sans allocation. {@code converging} :
     * chaque etoile est placee par {@link #convergence} (cible dans les axes du systeme, deja posee) au lieu de ses
     * coins precalcules.
     */
    private void drawStarPattern(MultiBufferSource buffers, Matrix4f m, SpaceStarField field, float radius,
                                 boolean converging)
    {
        if (field == null) return;
        if (field != pattern) preparePattern(field);
        Quaterniond q = pose.orientation();
        patternRotation.set((float) -q.x, (float) -q.y, (float) -q.z, (float) q.w);
        patternMatrix.set(m).rotate(patternRotation).scale(radius);
        float[] corners = patternCorners;
        float[] colors = patternColors;
        VertexConsumer vc = buffers.getBuffer(HoloRenderTypes.SOLID_QUADS);
        if (converging) {
            for (int i = 0, n = field.size(); i < n; i++) {
                convergence.place(field.x(i), field.y(i), field.z(i), patternHalf[i]);
                float r = colors[3 * i], g = colors[3 * i + 1], b = colors[3 * i + 2];
                for (int k = 0; k < 4; k++) {
                    vertex(vc, patternMatrix, convergence.x(k), convergence.y(k), convergence.z(k), r, g, b, 1f);
                }
            }
            return;
        }
        for (int i = 0, n = field.size(); i < n; i++) {
            int c = 12 * i;
            float r = colors[3 * i], g = colors[3 * i + 1], b = colors[3 * i + 2];
            vertex(vc, patternMatrix, corners[c], corners[c + 1], corners[c + 2], r, g, b, 1f);
            vertex(vc, patternMatrix, corners[c + 3], corners[c + 4], corners[c + 5], r, g, b, 1f);
            vertex(vc, patternMatrix, corners[c + 6], corners[c + 7], corners[c + 8], r, g, b, 1f);
            vertex(vc, patternMatrix, corners[c + 9], corners[c + 10], corners[c + 11], r, g, b, 1f);
        }
    }

    /**
     * Quads du champ {@code field} (une fois par graine) : carre face au centre autour de chaque direction, de demi-cote
     * {@code tan(taille / 2)} sur la sphere unite, meme base et meme sens que {@link #square} ; taille et eclat selon
     * l'eclat relatif de l'etoile.
     */
    private void preparePattern(SpaceStarField field)
    {
        int n = field.size();
        if (patternCorners.length < 12 * n) {
            patternCorners = new float[12 * n];
            patternColors = new float[3 * n];
            patternHalf = new float[n];
        }
        for (int i = 0; i < n; i++) {
            float t = (float) Math.pow(field.brightness(i), PATTERN_GAMMA);
            double size = Math.toRadians(PATTERN_MIN_SIZE + (PATTERN_MAX_SIZE - PATTERN_MIN_SIZE) * t);
            float half = (float) Math.tan(0.5 * size);
            patternHalf[i] = half;
            direction.set(field.x(i), field.y(i), field.z(i));
            basis(direction);
            float ux = bu.x * half, uy = bu.y * half, uz = bu.z * half;
            float vx = bv.x * half, vy = bv.y * half, vz = bv.z * half;
            float cx = direction.x, cy = direction.y, cz = direction.z;
            int c = 12 * i;
            // (-, -), (+, -), (+, +), (-, +) dans (bu, bv) : sens direct vu du centre.
            setCorner(c, cx - ux - vx, cy - uy - vy, cz - uz - vz);
            setCorner(c + 3, cx + ux - vx, cy + uy - vy, cz + uz - vz);
            setCorner(c + 6, cx + ux + vx, cy + uy + vy, cz + uz + vz);
            setCorner(c + 9, cx - ux + vx, cy - uy + vy, cz - uz + vz);
            float brightness = PATTERN_MIN_BRIGHTNESS + (PATTERN_MAX_BRIGHTNESS - PATTERN_MIN_BRIGHTNESS) * t;
            int color = field.color(i);
            patternColors[3 * i] = red(color) * brightness;
            patternColors[3 * i + 1] = green(color) * brightness;
            patternColors[3 * i + 2] = blue(color) * brightness;
        }
        pattern = field;
    }

    private void setCorner(int index, float x, float y, float z)
    {
        patternCorners[index] = x;
        patternCorners[index + 1] = y;
        patternCorners[index + 2] = z;
    }

    // ---------------------------------------------------------------- etoiles de fond

    /**
     * Autres systemes : direction galactique tournee par la pose, taille et eclat selon
     * {@code log10(luminosite / distance^2)} borne. {@code converging} : quad place par {@link #convergence} (cible
     * dans les axes du monde, deja posee).
     */
    private void drawBackgroundStars(MultiBufferSource buffers, Matrix4f m, List<BackgroundStar> stars,
                                     SpaceSystem current, float radius, boolean converging)
    {
        if (stars.isEmpty()) return;
        VertexConsumer vc = buffers.getBuffer(HoloRenderTypes.SOLID_QUADS);
        for (int i = 0, n = stars.size(); i < n; i++) {
            BackgroundStar star = stars.get(i);
            double distance = frame.galacticDirection(current, star, direction);
            if (!(distance > 0.0)) continue;
            double log = Math.log10(star.luminosity() / (distance * distance));
            double t = clamp01((log - STAR_LOG_MIN) / (STAR_LOG_MAX - STAR_LOG_MIN));
            double size = Math.toRadians(STAR_MIN_SIZE + (STAR_MAX_SIZE - STAR_MIN_SIZE) * t);
            float brightness = STAR_MIN_BRIGHTNESS + (1f - STAR_MIN_BRIGHTNESS) * (float) t;
            int color = star.color();
            float r = red(color) * brightness, g = green(color) * brightness, b = blue(color) * brightness;
            if (converging) {
                convergence.place(direction.x, direction.y, direction.z, (float) Math.tan(0.5 * size));
                for (int k = 0; k < 4; k++) {
                    vertex(vc, m, convergence.x(k) * radius, convergence.y(k) * radius, convergence.z(k) * radius, r,
                            g, b, 1f);
                }
                continue;
            }
            basis(direction);
            square(vc, m, direction, radius, radius * (float) Math.tan(0.5 * size), r, g, b);
        }
    }

    // ---------------------------------------------------------------- corps du systeme

    private void drawBodies(MultiBufferSource buffers, Matrix4f m, SpaceSystem system, int count, float scale)
    {
        List<SpaceBody> bodies = system.bodies();
        count = Math.min(count, bodies.size());
        // Textures de tous les corps du systeme courant (chargement a la demande, gardees tant qu'on y est).
        for (int i = 0; i < count; i++) bodyTextures[i] = textures.texture(system, i);
        // Directions et distances, puis tri du plus lointain au plus proche (insertion : au plus MAX_BODIES, 48 corps).
        int visible = 0;
        for (int i = 0; i < count; i++) {
            double d = frame.direction(positions[i], directions[i]);
            distances[i] = d;
            if (!(d > 0.0)) continue;
            int k = visible++;
            while (k > 0 && distances[order[k - 1]] < d) {
                order[k] = order[k - 1];
                k--;
            }
            order[k] = i;
        }
        for (int rank = 0; rank < visible; rank++) {
            int i = order[rank];
            SpaceBody body = bodies.get(i);
            float radius = scale * drawRadius(rank, visible);
            double alpha = SpaceSkyFrame.angularRadius(body.radius(), distances[i]);
            useLevel(lodLevel(i, body.id(), alpha));
            Vector3f w = directions[i];
            basis(w);
            int color = body.color();
            SpaceBodyTextureCache.Texture texture = bodyTextures[i];
            if (texture != null && alpha >= MIN_ANGULAR_RADIUS) bodyBasis(body, w);
            if (body.kind() == SpaceBodyKind.STAR) {
                drawStar(buffers, m, w, radius, alpha, color, texture);
            } else {
                drawLitBody(buffers, m, bodies, count, i, w, radius, alpha, color, texture);
            }
        }
    }

    /**
     * Niveau de detail du corps d'indice {@code index} (id {@code id}) de rayon angulaire {@code alpha} : rayon a
     * l'ecran {@code tan alpha x m11} compare aux seuils {@link #LOD_UP} ; le niveau retenu a la frame precedente ne
     * monte qu'au-dessus de son seuil et ne descend que sous {@link #LOD_DOWN} fois celui-ci (pas de va-et-vient pres
     * d'un seuil). Nouveau corps a cet indice : niveau choisi directement.
     */
    private int lodLevel(int index, int id, double alpha)
    {
        double size = Math.tan(Math.min(alpha, LOD_MAX_ALPHA)) * projectionScale;
        int level = lodIds[index] == id ? lodLevels[index] : -1;
        if (level < 0) level = 0;
        while (level < LOD_UP.length && size > LOD_UP[level]) level++;
        while (level > 0 && size < LOD_DOWN * LOD_UP[level - 1]) level--;
        lodLevels[index] = level;
        lodIds[index] = id;
        return level;
    }

    /** Tables du maillage du niveau {@code level} pour le corps en cours. */
    private void useLevel(int level)
    {
        sectorCos = SECTOR_COS[level];
        sectorSin = SECTOR_SIN[level];
        ringRho = RING_RHO[level];
        sectors = LOD_SECTORS[level];
        rings = LOD_RINGS[level];
    }

    /** Rayon de dessin du corps de rang {@code rank} (0 = le plus lointain) : progression geometrique 105 -> 50. */
    private static float drawRadius(int rank, int count)
    {
        if (count <= 1) return FAR_BODY_RADIUS;
        return (float) (FAR_BODY_RADIUS * Math.pow(NEAR_BODY_RADIUS / FAR_BODY_RADIUS, rank / (double) (count - 1)));
    }

    /**
     * Etoile : disque (ou point) en pleine lumiere, texture teinte par sa couleur si elle est chargee, puis couronne
     * translucide dont l'opacite s'annule au bord.
     * <p>
     * Eclat d'une texture : une texture teintee est eclaircie par {@code 1 / luminance moyenne} (sans qu'aucune
     * composante de la couleur depasse 1), une texture en couleurs est dessinee telle quelle ; l'opacite du halo est
     * multipliee par l'eclat moyen qui en resulte ({@link #haloGain}), si bien que son bord interieur n'est jamais plus
     * lumineux que le disque en moyenne. Un point prend la couleur moyenne du disque texture (pas de saut au seuil).
     */
    private void drawStar(MultiBufferSource buffers, Matrix4f m, Vector3f w, float radius, double alpha, int color,
                          SpaceBodyTextureCache.Texture texture)
    {
        float r = red(color), g = green(color), b = blue(color);
        haloGain = 1f;
        if (texture != null) {
            float gain = 1f;
            if (texture.tinted()) {
                gain = Math.min(1f / Math.max(texture.luminance(), 0.05f), 1f / Math.max(Math.max(r, g), Math.max(b,
                        0.05f)));
                r *= gain;
                g *= gain;
                b *= gain;
            } else {
                r = g = b = 1f;
            }
            haloGain = Math.min(1f, texture.luminance() * gain);
        }
        double shown = Math.max(alpha, MIN_ANGULAR_RADIUS);
        VertexConsumer vc;
        if (alpha < MIN_ANGULAR_RADIUS) {
            vc = buffers.getBuffer(HoloRenderTypes.SOLID_QUADS);
            if (texture == null) {
                square(vc, m, w, radius, radius * (float) Math.tan(MIN_ANGULAR_RADIUS), r, g, b);
            } else {
                int average = texture.averageColor();
                square(vc, m, w, radius, radius * (float) Math.tan(MIN_ANGULAR_RADIUS), r * red(average),
                        g * green(average), b * blue(average));
            }
        } else if (texture != null) {
            vc = buffers.getBuffer(texture.renderType());
            texturedDisk(vc, m, w, radius, alpha, r, g, b, false);
        } else {
            vc = buffers.getBuffer(HoloRenderTypes.SOLID_QUADS);
            disk(vc, m, w, radius, alpha, r, g, b, false);
        }

        double outer = Math.min(MAX_HALO_ANGLE, shown * HALO_SCALE);
        if (outer <= shown) return;
        r = red(color);
        g = green(color);
        b = blue(color);
        for (int k = 0; k <= HALO_RINGS; k++) {
            double angle = shown + (outer - shown) * k / HALO_RINGS;
            haloCos[k] = (float) Math.cos(angle);
            haloSin[k] = (float) Math.sin(angle);
        }
        vc = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        for (int k = 0; k < HALO_RINGS; k++) {
            float inner = haloAlpha(k);
            float next = haloAlpha(k + 1);
            for (int j = 0; j < sectors; j++) {
                capVertex(vc, m, w, radius, haloCos[k], haloSin[k], j, r, g, b, inner);
                capVertex(vc, m, w, radius, haloCos[k + 1], haloSin[k + 1], j, r, g, b, next);
                capVertex(vc, m, w, radius, haloCos[k + 1], haloSin[k + 1], j + 1, r, g, b, next);
                capVertex(vc, m, w, radius, haloCos[k], haloSin[k], j + 1, r, g, b, inner);
            }
        }
    }

    /**
     * Opacite de l'anneau {@code k} du halo : {@code HALO_ALPHA haloGain (1 - t)^2}, {@code t} de 0 (disque) a 1
     * (bord).
     */
    private float haloAlpha(int k)
    {
        float t = 1f - k / (float) HALO_RINGS;
        return HALO_ALPHA * haloGain * t * t;
    }

    /**
     * Planete, lune ou objet mineur : eclaire par l'etoile la plus proche (l'etoile principale hors binaire). Disque a
     * phases (texture si elle est chargee, sinon couleur unie), ou point dont la couleur suit la fraction eclairee
     * visible et dont l'eclat decroit avec la taille apparente.
     */
    private void drawLitBody(MultiBufferSource buffers, Matrix4f m, List<SpaceBody> bodies, int count, int index,
                             Vector3f w, float radius, double alpha, int color, SpaceBodyTextureCache.Texture texture)
    {
        int star = nearestStar(bodies, count, index);
        if (star < 0 || frame.directionBetween(positions[index], positions[star], light) <= 0.0) {
            light.set(-w.x, -w.y, -w.z);
        }
        float r = red(color), g = green(color), b = blue(color);
        if (alpha < MIN_ANGULAR_RADIUS) {
            if (texture != null) {
                // Couleur moyenne de la texture (fois celle du corps si teintee) : pas de saut au seuil du point.
                int average = texture.averageColor();
                if (texture.tinted()) {
                    r *= red(average);
                    g *= green(average);
                    b *= blue(average);
                } else {
                    r = red(average);
                    g = green(average);
                    b = blue(average);
                }
            }
            VertexConsumer vc = buffers.getBuffer(HoloRenderTypes.SOLID_QUADS);
            // Fraction eclairee visible : (1 + cos angle de phase) / 2, angle entre corps -> etoile et corps -> oeil.
            float lit = 0.5f * (1f - light.dot(w));
            float fade = MIN_POINT_BRIGHTNESS
                    + (1f - MIN_POINT_BRIGHTNESS) * (float) Math.sqrt(alpha / MIN_ANGULAR_RADIUS);
            float shade = Math.min(1f, AMBIENT + lit) * fade;
            square(vc, m, w, radius, radius * (float) Math.tan(MIN_ANGULAR_RADIUS), r * shade, g * shade, b * shade);
            return;
        }
        lightU = light.dot(bu);
        lightV = light.dot(bv);
        lightW = -light.dot(w);
        if (texture == null) {
            disk(buffers.getBuffer(HoloRenderTypes.SOLID_QUADS), m, w, radius, alpha, r, g, b, true);
        } else if (texture.tinted()) {
            texturedDisk(buffers.getBuffer(texture.renderType()), m, w, radius, alpha, r, g, b, true);
        } else {
            texturedDisk(buffers.getBuffer(texture.renderType()), m, w, radius, alpha, 1f, 1f, 1f, true);
        }
    }

    /** Indice de l'etoile la plus proche du corps {@code index} (hors lui-meme), ou {@code -1}. */
    private int nearestStar(List<SpaceBody> bodies, int count, int index)
    {
        Vector3d from = positions[index];
        int best = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            if (i == index || bodies.get(i).kind() != SpaceBodyKind.STAR) continue;
            double d = from.distanceSquared(positions[i]);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * Disque polaire de rayon angulaire {@code alpha} centre sur {@code w}, sommets sur la sphere de rayon
     * {@code radius}, maillage du niveau en cours ({@link #useLevel}). {@code lit} : couleur ombree par la normale de
     * sphere ({@link #lightU}, {@link #lightV}, {@link #lightW}), sinon couleur pleine.
     * <p>
     * Normale exacte, perspective comprise : avec {@code s = sin alpha = R / d}, le rayon de vue d'angle {@code beta}
     * touche la sphere a la distance {@code t d}, {@code t = cos beta - sqrt(s^2 - sin^2 beta)}, ou la normale vaut
     * {@code t sin beta / s} (radiale) et {@code (1 - t cos beta) / s} (vers l'oeil). Au bord ({@code beta = alpha}) :
     * {@code (cos alpha, sin alpha)} ; loin ({@code s -> 0}) : {@code (rho, sqrt(1 - rho^2))}, le disque orthographique
     * de T05 ; tout pres ({@code alpha -> 90 deg}) : la calotte visible se reduit et les normales tendent vers l'oeil.
     */
    private void disk(VertexConsumer vc, Matrix4f m, Vector3f w, float radius, double alpha, float r, float g,
                      float b, boolean lit)
    {
        ringTables(alpha, lit);
        for (int k = 0; k < rings; k++) {
            for (int j = 0; j < sectors; j++) {
                // Interieur, exterieur, exterieur suivant, interieur suivant : sens direct vu de l'oeil.
                diskVertex(vc, m, w, radius, k, j, r, g, b, lit);
                diskVertex(vc, m, w, radius, k + 1, j, r, g, b, lit);
                diskVertex(vc, m, w, radius, k + 1, j + 1, r, g, b, lit);
                diskVertex(vc, m, w, radius, k, j + 1, r, g, b, lit);
            }
        }
    }

    /**
     * Tables des anneaux du disque de rayon angulaire {@code alpha} (niveau en cours) : angle de vue (cosinus, sinus)
     * et, si {@code normals}, normale de sphere au point vise ({@link #ringRadial}, {@link #ringDepth} ; voir
     * {@link #disk}).
     */
    private void ringTables(double alpha, boolean normals)
    {
        double s = Math.sin(alpha);
        for (int k = 0; k <= rings; k++) {
            double angle = ringRho[k] * alpha;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            ringCos[k] = (float) cos;
            ringSin[k] = (float) sin;
            if (normals) {
                double root = Math.sqrt(Math.max(0.0, s * s - sin * sin));
                ringRadial[k] = (float) ((cos - root) * sin / s);
                ringDepth[k] = (float) ((sin * sin + cos * root) / s);
            }
        }
    }

    // ---------------------------------------------------------------- disque texture (T30)

    /**
     * Base du disque en cours ({@link #bu}, {@link #bv}, {@code w}) exprimee dans les axes du corps au temps
     * d'affichage : monde -> systeme par la pose ({@code v_S = Q v_W}), puis systeme -> corps par l'inverse de la
     * rotation propre ({@code v_B = q_B^-1 v_S}, {@link SpaceSpin}).
     */
    private void bodyBasis(SpaceBody body, Vector3f w)
    {
        SpaceSpin.orientation(body, days, spin);
        toBody.set(spin).conjugate().mul(pose.orientation());
        toBodyAxes(bu, bodyU);
        toBodyAxes(bv, bodyV);
        toBodyAxes(w, bodyW);
    }

    private void toBodyAxes(Vector3f v, Vector3f dest)
    {
        toBody.transform(v.x, v.y, v.z, rotated);
        dest.set((float) rotated.x, (float) rotated.y, (float) rotated.z);
    }

    /**
     * Disque texture (meme maillage et memes sommets que {@link #disk}) : chaque sommet recoit les coordonnees de
     * texture du point de la sphere qu'il vise (normale exacte {@code radiale (cos t bu + sin t bv) - profondeur w},
     * ramenee dans les axes du corps par {@link #bodyBasis}) et, si {@code lit}, l'ombrage de cette normale. Couleur des
     * sommets : {@code (r, g, b)} fois l'ombrage (blanc pour une texture en couleurs, couleur du corps pour une texture
     * teintee). Couture : dans un quad dont les {@code u} s'etalent sur plus d'un demi-tour, les petits {@code u}
     * passent au-dela de 1 (texture repetee en {@code u}) ; un quad qui contient un pole garde un leger flou (niveau de
     * mipmap grossier sur un quad de quelques degres).
     */
    private void texturedDisk(VertexConsumer vc, Matrix4f m, Vector3f w, float radius, double alpha, float r, float g,
                              float b, boolean lit)
    {
        ringTables(alpha, true);
        int row = sectors + 1;
        for (int k = 0; k <= rings; k++) {
            float radial = ringRadial[k];
            float depth = ringDepth[k];
            for (int j = 0; j <= sectors; j++) {
                float c = sectorCos[j] * radial;
                float s = sectorSin[j] * radial;
                float nx = c * bodyU.x + s * bodyV.x - depth * bodyW.x;
                float ny = c * bodyU.y + s * bodyV.y - depth * bodyW.y;
                float nz = c * bodyU.z + s * bodyV.z - depth * bodyW.z;
                int index = k * row + j;
                // Longitude de +X vers -Z (sens de la rotation directe), latitude vers +Y (pole) ; longitude 0 au centre.
                vertexU[index] = 0.5f + atan2(-nz, nx) * INV_TWO_PI;
                vertexV[index] = 0.5f - atan2(ny, (float) Math.sqrt(nx * nx + nz * nz)) * INV_PI;
                float shade = 1f;
                if (lit) {
                    float dot = c * lightU + s * lightV + depth * lightW;
                    shade = Math.min(1f, AMBIENT + Math.max(0f, dot));
                }
                vertexShade[index] = shade;
            }
        }
        for (int k = 0; k < rings; k++) {
            for (int j = 0; j < sectors; j++) {
                // Interieur, exterieur, exterieur suivant, interieur suivant : sens direct vu de l'oeil (comme disk).
                int i0 = k * row + j;
                int i1 = i0 + row;
                int i2 = i1 + 1;
                int i3 = i0 + 1;
                float u0 = vertexU[i0], u1 = vertexU[i1], u2 = vertexU[i2], u3 = vertexU[i3];
                float low = Math.min(Math.min(u0, u1), Math.min(u2, u3));
                float high = Math.max(Math.max(u0, u1), Math.max(u2, u3));
                if (high - low > 0.5f) {
                    if (u0 < 0.5f) u0 += 1f;
                    if (u1 < 0.5f) u1 += 1f;
                    if (u2 < 0.5f) u2 += 1f;
                    if (u3 < 0.5f) u3 += 1f;
                }
                texturedVertex(vc, m, w, radius, k, j, u0, vertexV[i0], vertexShade[i0], r, g, b);
                texturedVertex(vc, m, w, radius, k + 1, j, u1, vertexV[i1], vertexShade[i1], r, g, b);
                texturedVertex(vc, m, w, radius, k + 1, j + 1, u2, vertexV[i2], vertexShade[i2], r, g, b);
                texturedVertex(vc, m, w, radius, k, j + 1, u3, vertexV[i3], vertexShade[i3], r, g, b);
            }
        }
    }

    /** Sommet de disque texture ({@code POSITION_COLOR_TEX}) : meme position que {@link #capVertex}. */
    private void texturedVertex(VertexConsumer vc, Matrix4f m, Vector3f w, float radius, int ring, int sector, float u,
                                float v, float shade, float r, float g, float b)
    {
        float cos = ringCos[ring];
        float c = sectorCos[sector] * ringSin[ring];
        float s = sectorSin[sector] * ringSin[ring];
        m.transformPosition(radius * (cos * w.x + c * bu.x + s * bv.x), radius * (cos * w.y + c * bu.y + s * bv.y),
                radius * (cos * w.z + c * bu.z + s * bv.z), transformed);
        vc.vertex(transformed.x, transformed.y, transformed.z).color(r * shade, g * shade, b * shade, 1f).uv(u, v)
                .endVertex();
    }

    /**
     * {@code atan2(y, x)} approche (erreur sous {@code 1e-5} rad, 0,0006 degre, loin sous le texel de 0,35 degre d'une
     * texture de 1024 de large) : polynome minimax de {@code atan} sur {@code [0, 1]}, puis octants. Moins couteux que
     * {@code Math.atan2} pour les 9 457 sommets du niveau le plus fin. {@code (0, 0)} rend 0.
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

    private void diskVertex(VertexConsumer vc, Matrix4f m, Vector3f w, float radius, int ring, int sector, float r,
                            float g, float b, boolean lit)
    {
        float shade = 1f;
        if (lit) {
            // n = radial (cos bu + sin bv) + depth (-w) ; n . l dans la base (bu, bv, -w).
            float dot = ringRadial[ring] * (sectorCos[sector] * lightU + sectorSin[sector] * lightV)
                    + ringDepth[ring] * lightW;
            shade = Math.min(1f, AMBIENT + Math.max(0f, dot));
        }
        capVertex(vc, m, w, radius, ringCos[ring], ringSin[ring], sector, r * shade, g * shade, b * shade, 1f);
    }

    /** Sommet de calotte : direction {@code cos w + sin (cos t bu + sin t bv)}, au rayon {@code radius}. */
    private void capVertex(VertexConsumer vc, Matrix4f m, Vector3f w, float radius, float cos, float sin, int sector,
                           float r, float g, float b, float a)
    {
        float c = sectorCos[sector] * sin;
        float s = sectorSin[sector] * sin;
        vertex(vc, m, radius * (cos * w.x + c * bu.x + s * bv.x), radius * (cos * w.y + c * bu.y + s * bv.y),
                radius * (cos * w.z + c * bu.z + s * bv.z), r, g, b, a);
    }

    // ---------------------------------------------------------------- primitives

    /**
     * Base du disque perpendiculaire a {@code w} : {@code bu = normalize(w x a)}, {@code bv = bu x w}, d'ou
     * {@code bu x bv = -w} (vers l'oeil). {@code a} = haut du monde, ou est si {@code w} est presque vertical.
     */
    private void basis(Vector3f w)
    {
        if (Math.abs(w.y) < 0.9f) {
            bu.set(w).cross(0f, 1f, 0f);
        } else {
            bu.set(w).cross(1f, 0f, 0f);
        }
        bu.normalize();
        bv.set(bu).cross(w);
    }

    /** Carre opaque face a l'oeil, centre en {@code radius w}, demi-cote {@code half} (base {@link #basis} faite). */
    private void square(VertexConsumer vc, Matrix4f m, Vector3f w, float radius, float half, float r, float g, float b)
    {
        float cx = w.x * radius, cy = w.y * radius, cz = w.z * radius;
        float ux = bu.x * half, uy = bu.y * half, uz = bu.z * half;
        float vx = bv.x * half, vy = bv.y * half, vz = bv.z * half;
        // (-, -), (+, -), (+, +), (-, +) dans (bu, bv) : sens direct vu de l'oeil.
        vertex(vc, m, cx - ux - vx, cy - uy - vy, cz - uz - vz, r, g, b, 1f);
        vertex(vc, m, cx + ux - vx, cy + uy - vy, cz + uz - vz, r, g, b, 1f);
        vertex(vc, m, cx + ux + vx, cy + uy + vy, cz + uz + vz, r, g, b, 1f);
        vertex(vc, m, cx - ux + vx, cy - uy + vy, cz - uz + vz, r, g, b, 1f);
    }

    /** Sommet transforme sans allocation ({@code VertexConsumer.vertex(Matrix4f, ...)} cree un vecteur). */
    private void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a)
    {
        m.transformPosition(x, y, z, transformed);
        vc.vertex(transformed.x, transformed.y, transformed.z).color(r, g, b, a).uv2(LIGHT).endVertex();
    }

    private static float red(int argb) { return (argb >> 16 & 0xFF) / 255f; }

    private static float green(int argb) { return (argb >> 8 & 0xFF) / 255f; }

    private static float blue(int argb) { return (argb & 0xFF) / 255f; }

    private static double clamp01(double value) { return value < 0.0 ? 0.0 : Math.min(1.0, value); }
}
