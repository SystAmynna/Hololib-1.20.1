package com.amynna.hololib.client.render.modules.dimension.space;

import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.client.holo.HoloModes;
import com.amynna.hololib.client.render.sky.SkyRenderContext;
import com.amynna.hololib.client.render.sky.SkyRenderer;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.math.SpacePose;
import com.amynna.hololib.modules.dimension.space.math.SpaceSkyFrame;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceGalaxy.BackgroundStar;
import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

/**
 * Ciel du module {@code hololib:space_environment} : remplace visuellement le ciel vanilla par le systeme courant vu
 * depuis la pose du monde ({@code docs/space/04-mathematiques.md} par. 2 a 4). Dessine seulement si le miroir de
 * dimension contient un Environment synchronise ; sinon le ciel vanilla reste intact.
 * <p>
 * Ordre de dessin (profondeur de la passe du ciel, chaque objet plus proche que le precedent) :
 * <ol>
 *     <li>fond : sphere opaque noire, faces tournees vers l'interieur, a {@link #BACKGROUND_RADIUS} blocs ;</li>
 *     <li>etoiles de fond (les autres systemes) : quads de taille angulaire fixe a {@link #STAR_FIELD_RADIUS}
 *     blocs ;</li>
 *     <li>corps du systeme tries du plus lointain au plus proche, a des rayons de dessin distincts repartis
 *     geometriquement de {@link #FAR_BODY_RADIUS} a {@link #NEAR_BODY_RADIUS} blocs.</li>
 * </ol>
 * Un corps est un maillage polaire centre sur sa direction : ses sommets sont poses <b>sur la sphere</b> de son rayon
 * de dessin (calotte d'angle egal a son rayon angulaire apparent, jusqu'a 90 degres : au plancher d'altitude, une
 * planete est une demi-sphere de ciel bordee par un horizon) plutot que sur un plan. Le long d'un rayon de vue, la
 * profondeur est alors celle du rayon de dessin : l'occultation entre corps reste exacte quelle que soit leur taille,
 * et aucun sommet ne depasse le plan lointain. Les planetes et lunes recoivent la normale <b>exacte</b> de la sphere
 * qu'elles representent au point vise par chaque sommet, perspective comprise (phases ; de pres on ne voit qu'une
 * calotte du corps, dont les normales restent proches de la verticale), eclairee par l'etoile la plus proche ; les
 * etoiles sont en pleine lumiere, avec un halo translucide. Sous {@link #MIN_ANGULAR_RADIUS}, un corps devient un
 * point de taille fixe dont l'eclat decroit.
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
    /** Etoiles de fond : devant le fond (les faces de la sphere grossiere descendent a environ 115 blocs). */
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
    /** Anneaux du niveau le plus fin (taille des tableaux par corps). */
    private static final int MAX_RINGS = 48;
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
    private final float[] haloSin = new float[HALO_RINGS + 1];
    /** Base du disque en cours : {@code bu x bv = -w} (tournee vers l'oeil, sens direct vu de l'oeil). */
    private final Vector3f bu = new Vector3f();
    private final Vector3f bv = new Vector3f();
    private final Vector3f light = new Vector3f();
    private final Vector3f direction = new Vector3f();
    private final Vector3f transformed = new Vector3f();
    /** Eclairage du corps en cours, dans la base {@code (bu, bv, -w)}. */
    private float lightU;
    private float lightV;
    private float lightW;

    public SpaceSkyRenderer()
    {
        for (int i = 0; i < directions.length; i++) directions[i] = new Vector3f();
        Arrays.fill(lodLevels, -1);
    }

    // ---------------------------------------------------------------- SkyRenderer

    @Override
    public boolean shouldRender(SkyRenderContext ctx)
    {
        SpaceEnvironmentModule env = find();
        environment = env != null && env.synced() && env.currentSystem() != null ? env : null;
        return environment != null;
    }

    @Override
    public void render(PoseStack stack, MultiBufferSource buffers, SkyRenderContext ctx)
    {
        SpaceEnvironmentModule env = environment;
        environment = null;
        if (env == null) return;
        SpaceSystem system = env.currentSystem();
        if (system == null) return;
        float partialTick = ctx.partialTick();
        frame.set(env.pose(partialTick, pose));
        Matrix4f m = stack.last().pose();
        float scale = Math.min(1f, ctx.farPlane() * FAR_PLANE_MARGIN / BACKGROUND_RADIUS);
        double m11 = Math.abs(RenderSystem.getProjectionMatrix().m11());
        projectionScale = Double.isFinite(m11) && m11 > 0.0 ? m11 : DEFAULT_PROJECTION_SCALE;

        drawBackground(buffers, m, BACKGROUND_RADIUS * scale);
        drawBackgroundStars(buffers, m, env.backgroundStars(), system, STAR_FIELD_RADIUS * scale);
        drawBodies(buffers, m, system, env.bodyPositions(partialTick, positions), scale);
    }

    @Override
    public void fogColor(Camera camera, float partialTick, Vector3f rgb)
    {
        if (camera.getFluidInCamera() != FogType.NONE) return;
        if (find() == null) return;
        rgb.set(FOG_RED, FOG_GREEN, FOG_BLUE);
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

    // ---------------------------------------------------------------- etoiles de fond

    /**
     * Autres systemes : direction galactique tournee par la pose, taille et eclat selon
     * {@code log10(luminosite / distance^2)} borne.
     */
    private void drawBackgroundStars(MultiBufferSource buffers, Matrix4f m, List<BackgroundStar> stars,
                                     SpaceSystem current, float radius)
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
            basis(direction);
            int color = star.color();
            square(vc, m, direction, radius, radius * (float) Math.tan(0.5 * size),
                    red(color) * brightness, green(color) * brightness, blue(color) * brightness);
        }
    }

    // ---------------------------------------------------------------- corps du systeme

    private void drawBodies(MultiBufferSource buffers, Matrix4f m, SpaceSystem system, int count, float scale)
    {
        List<SpaceBody> bodies = system.bodies();
        count = Math.min(count, bodies.size());
        // Directions et distances, puis tri du plus lointain au plus proche (insertion : au plus 32 corps).
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
            if (body.kind() == SpaceBodyKind.STAR) {
                drawStar(buffers, m, w, radius, alpha, color);
            } else {
                drawLitBody(buffers, m, bodies, count, i, w, radius, alpha, color);
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

    /** Etoile : disque (ou point) en pleine lumiere, puis couronne translucide dont l'opacite s'annule au bord. */
    private void drawStar(MultiBufferSource buffers, Matrix4f m, Vector3f w, float radius, double alpha, int color)
    {
        float r = red(color), g = green(color), b = blue(color);
        double shown = Math.max(alpha, MIN_ANGULAR_RADIUS);
        VertexConsumer vc = buffers.getBuffer(HoloRenderTypes.SOLID_QUADS);
        if (alpha < MIN_ANGULAR_RADIUS) {
            square(vc, m, w, radius, radius * (float) Math.tan(MIN_ANGULAR_RADIUS), r, g, b);
        } else {
            disk(vc, m, w, radius, alpha, r, g, b, false);
        }

        double outer = Math.min(MAX_HALO_ANGLE, shown * HALO_SCALE);
        if (outer <= shown) return;
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

    /** Opacite de l'anneau {@code k} du halo : {@code HALO_ALPHA (1 - t)^2}, {@code t} de 0 (disque) a 1 (bord). */
    private static float haloAlpha(int k)
    {
        float t = 1f - k / (float) HALO_RINGS;
        return HALO_ALPHA * t * t;
    }

    /**
     * Planete ou lune : eclairee par l'etoile la plus proche (l'etoile principale hors binaire). Disque a phases, ou
     * point dont la couleur suit la fraction eclairee visible et dont l'eclat decroit avec la taille apparente.
     */
    private void drawLitBody(MultiBufferSource buffers, Matrix4f m, List<SpaceBody> bodies, int count, int index,
                             Vector3f w, float radius, double alpha, int color)
    {
        int star = nearestStar(bodies, count, index);
        if (star < 0 || frame.directionBetween(positions[index], positions[star], light) <= 0.0) {
            light.set(-w.x, -w.y, -w.z);
        }
        float r = red(color), g = green(color), b = blue(color);
        VertexConsumer vc = buffers.getBuffer(HoloRenderTypes.SOLID_QUADS);
        if (alpha < MIN_ANGULAR_RADIUS) {
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
        disk(vc, m, w, radius, alpha, r, g, b, true);
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
        double s = Math.sin(alpha);
        for (int k = 0; k <= rings; k++) {
            double angle = ringRho[k] * alpha;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            ringCos[k] = (float) cos;
            ringSin[k] = (float) sin;
            if (lit) {
                double root = Math.sqrt(Math.max(0.0, s * s - sin * sin));
                ringRadial[k] = (float) ((cos - root) * sin / s);
                ringDepth[k] = (float) ((sin * sin + cos * root) / s);
            }
        }
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
