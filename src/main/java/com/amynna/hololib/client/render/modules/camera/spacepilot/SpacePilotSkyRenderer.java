package com.amynna.hololib.client.render.modules.camera.spacepilot;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.modules.camera.spacepilot.SpacePilotClient;
import com.amynna.hololib.client.render.modules.dimension.space.SpaceWarpEffect;
import com.amynna.hololib.client.render.sky.SkyRenderContext;
import com.amynna.hololib.client.render.sky.SkyRenderer;
import com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickForward;
import com.amynna.hololib.modules.camera.spacepilot.SpacePilotModule;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.math.SpacePose;
import com.amynna.hololib.modules.dimension.space.math.SpaceSkyFrame;
import com.amynna.hololib.modules.dimension.space.math.SpaceUnits;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceGalaxy.BackgroundStar;
import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import com.amynna.hololib.modules.dimension.space.model.SpaceOrbit;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

/**
 * Surcouche du ciel de Space Pilot (T18 ; {@code docs/space/04-mathematiques.md} par. 9, decisions D15, C46, C47) :
 * anneaux autour des corps du systeme courant, orbites, double anneau autour des etoiles de fond, nom de l'objet
 * regarde, et (T19) lien pointille entre l'axe avant du monde et le corps de reference ({@link #drawReferenceLink}).
 * Lit le module Space Pilot du <b>joueur local</b> ({@link SpacePilotClient#module()}) et le miroir de
 * l'Environment : un autre joueur ne voit jamais la surcouche, meme en inspection (C45).
 * <p>
 * Rien n'est dessine sans Environment synchronise (bloc froid connu) ni systeme courant, sans module Space Pilot chez
 * le joueur local, si son interrupteur general ({@code enabled}, Quick Action) est coupe, ou si les cinq parties
 * ({@code orbits}, {@code bodies}, {@code stars}, {@code names}, {@code refLink}) le sont. Rien de tout cela ne depend
 * du pilotage. Coupee pendant la distorsion du saut (T23 : intensite {@code SpaceWarpEffect.strength} non nulle,
 * fondus d'entree et de sortie compris, la surcouche est devant la sphere blanche). Le reperage de la cible du saut
 * et la roue de charge (T22) sont dessines par {@link SpaceJumpRenderer}, dans la vue.
 * <p>
 * Meme repere et meme temps que {@code SpaceSkyRenderer} : pose interpolee {@code pose(partialTick)} et positions
 * {@code bodyPositions(partialTick)} au temps d'affichage du module, directions dans les axes du monde (repere du
 * {@link PoseStack} du ciel). Tout est pose sur la sphere de rayon {@link #OVERLAY_RADIUS} : plus pres de l'oeil que
 * les corps (50 a 105 blocs), les etoiles de fond (110) et le fond (120) dessines avant par {@code SpaceSkyRenderer}
 * (enregistre avant ce renderer, meme passe) ; le test de profondeur passe donc toujours, et la surcouche
 * n'ecrit pas la profondeur ({@link OverlayType}). Le terrain la recouvre comme le reste du ciel (profondeur videe
 * apres la passe).
 * <p>
 * Tailles constantes a l'ecran : un pixel de reference vaut un pixel de l'ecran jusqu'a 1 080 pixels de haut, puis
 * grandit avec la fenetre (memes proportions sur un grand ecran) ; converti en angle par l'echelle verticale de la
 * projection ({@code m11} : FOV et longue-vue comptent) et la hauteur de la fenetre, a chaque frame.
 * <p>
 * Aucune allocation par frame dans ce code (tableaux d'echantillons statiques, vecteurs reutilises ; le {@code Font}
 * vanilla cree ses petits objets de sortie pour le seul nom affiche) ; seulement des {@code RenderType} (aucun etat GL
 * modifie a la main). Fil de rendu.
 */
public final class SpacePilotSkyRenderer implements SkyRenderer
{
    // ---------------------------------------------------------------- placement

    /** Rayon de la sphere de la surcouche, en blocs : devant le corps le plus proche (50 blocs). */
    static final float OVERLAY_RADIUS = 40f;
    /** Hauteur d'ecran jusqu'a laquelle un pixel de reference vaut un pixel (au-dela : proportionnel). */
    private static final double REFERENCE_HEIGHT = 1080.0;
    /** Echelle verticale de la projection a defaut ({@code 1 / tan(35 deg)}, FOV 70). */
    private static final double DEFAULT_PROJECTION_SCALE = 1.0 / Math.tan(Math.toRadians(35.0));
    /** Marge du test de champ de vision (radians) : rotations du bobbing, arrondis. */
    private static final double CULL_MARGIN = Math.toRadians(5.0);

    // ---------------------------------------------------------------- anneaux des corps (C46)

    /** Rayon apparent de l'anneau : {@code max(alpha_corps x 1,6, alpha_min)}. */
    private static final double BODY_RING_FACTOR = 1.6;
    /** {@code alpha_min} : rayon minimal de l'anneau, en pixels de reference. */
    private static final double BODY_RING_MIN_PX = 10.0;
    /** Epaisseur de l'anneau d'un corps, en pixels de reference. */
    private static final double BODY_RING_THICKNESS_PX = 1.5;
    /** Plafond du rayon de l'anneau (radians) : un corps qui remplit le ciel garde un anneau autour de lui. */
    private static final double MAX_RING_ANGLE = Math.toRadians(100.0);
    private static final float BODY_RING_ALPHA = 0.85f;
    private static final int BODY_RING_SEGMENTS = 64;

    // ---------------------------------------------------------------- orbites (par. 9)

    /** Echantillons de l'ellipse (anomalie excentrique reguliere) : autant de segments. */
    private static final int ORBIT_SAMPLES = 128;
    /**
     * Epaisseur du trait, en pixels de reference (au moins autant de pixels reels) : 1,5 pour qu'un trait oblique
     * couvre toujours un pixel entier par colonne, sans pointilles au rasterisage.
     */
    private static final double ORBIT_THICKNESS_PX = 1.5;
    /**
     * Fondu d'une orbite minuscule selon le rayon apparent de son apoapse (pixels de reference) : invisible sous
     * {@link #ORBIT_MIN_PX}, pleine au-dessus de {@link #ORBIT_FULL_PX} (pas de bascule d'une frame a l'autre).
     */
    private static final double ORBIT_MIN_PX = 3.0;
    private static final double ORBIT_FULL_PX = 6.0;
    /**
     * Angle maximal d'une corde dessinee : au-dela, le segment est subdivise (en anomalie excentrique, puis en arc de
     * grand cercle). Sert aussi de marge au test de champ d'une corde.
     */
    private static final double MAX_CHORD_ANGLE = Math.toRadians(3.0);
    private static final float COS_MAX_CHORD = (float) Math.cos(MAX_CHORD_ANGLE);
    /** Subdivisions au plus d'un segment de l'ellipse, puis d'un arc (3 degres x 64 couvrent 180 degres). */
    private static final int MAX_SUBDIVISIONS = 64;
    /** Corde ignoree si ses extremites sont quasi opposees (moins de 1,8 degre) : le segment traverse l'oeil. */
    private static final float COS_THROUGH_EYE = -0.9995f;
    /** Opacite du trait (couleur de l'objet qui orbite, {@link #kindColor}). */
    private static final float ORBIT_ALPHA = 0.4f;
    /**
     * Pointilles d'une orbite a travers l'anneau d'un autre corps repere : tiret et espace, en pixels de reference
     * (longueur angulaire constante a l'ecran). Les tirets partent du bord de l'anneau traverse.
     */
    private static final double ORBIT_DASH_PX = 4.0;
    private static final double ORBIT_SPACE_PX = 10.0;
    /** Plafond des tirets d'un morceau de corde (longue-vue) : au-dela, le reste du morceau est plein. */
    private static final int MAX_PIECE_DASHES = 256;
    /** Morceau de corde plus court que cette fraction ignore (bornes confondues). */
    private static final double MIN_PIECE = 1.0e-9;

    // ---------------------------------------------------------------- etoiles de fond (C46)

    /** Double anneau : rayons interieur et exterieur (pixels de reference), epaisseur de chacun. */
    private static final double STAR_INNER_PX = 6.0;
    private static final double STAR_OUTER_PX = 9.0;
    private static final double STAR_RING_THICKNESS_PX = 1.0;
    /**
     * Rayon minimal de l'anneau interieur (radians) : reste hors du carre de l'etoile (0,2 degre au plus) meme a la
     * longue-vue.
     */
    private static final double STAR_INNER_MIN_ANGLE = Math.toRadians(0.3);
    private static final float STAR_ALPHA = 0.6f;
    private static final int STAR_RING_SEGMENTS = 16;

    // ---------------------------------------------------------------- lien vers le referentiel (T19, par. 9)

    /**
     * Rayon angulaire du cercle avant du joystick (radians) : le lien part de son bord, qu'il soit affiche (pilotage)
     * ou non, pour ne pas traverser le cercle de visee.
     */
    private static final double FORWARD_RING_ANGLE =
            Math.atan((double) SpaceJoystickForward.RADIUS / SpaceJoystickForward.DISTANCE);
    /** Tiret et espace du pointille, epaisseur, ecart aux deux anneaux : pixels de reference (constants a l'ecran). */
    private static final double LINK_DASH_PX = 6.0;
    private static final double LINK_SPACE_PX = 4.0;
    private static final double LINK_THICKNESS_PX = 1.5;
    private static final double LINK_GAP_PX = 3.0;
    /** Plafond du nombre de tirets (longue-vue sur un lien de 180 degres) : au-dela, les tirets s'allongent. */
    private static final int MAX_LINK_DASHES = 512;
    /** Opacite du lien (couleur du corps de reference, {@link #kindColor}). */
    private static final float LINK_ALPHA = 0.8f;

    // ---------------------------------------------------------------- visibilite (D15, C47)

    /**
     * Lune reperee si la distance du monde a son parent est au plus ce facteur fois le plus grand {@code a} de ses
     * lunes (C47 revu par l'utilisatrice : 10, au lieu de 3).
     */
    private static final double MOON_REACH_FACTOR = 10.0;
    /** Objet mineur repere a moins de 0,5 UA de lui. */
    private static final double MINOR_REACH_KM = 0.5 * SpaceUnits.AU_KM;
    /** Fondu sur les derniers 20 % du seuil. */
    private static final double FADE_FRACTION = 0.2;

    // ---------------------------------------------------------------- nom au regard

    /** Ecart entre l'anneau et le debut du texte, en pixels de reference. */
    private static final double NAME_GAP_PX = 4.0;
    /** Plafond du decalage angulaire du nom (gros corps : le nom reste pres du regard). */
    private static final double NAME_MAX_OFFSET = Math.toRadians(30.0);
    /** Fond du texte (lisible sur un halo d'etoile), ARGB. */
    private static final int NAME_BACKGROUND = 0x60000000;
    /** Opacite minimale du nom d'un corps en fondu (le {@code Font} rend opaque une opacite sous 4). */
    private static final int NAME_MIN_ALPHA = 0x40;
    /** Decalage vertical du texte (unites de police) : ligne centree sur le point d'ancrage. */
    private static final float NAME_Y = -4f;

    // ---------------------------------------------------------------- couleurs (RGB, lisibles sur fond noir)

    /** Planetes et objets mineurs : bleu. Anneau, orbite et nom. */
    private static final int PLANET_COLOR = 0x4FA8FF;
    /** Etoiles du systeme courant (principale et compagnes) : jaune. */
    private static final int STAR_COLOR = 0xFFD84A;
    /** Lunes : gris clair. */
    private static final int MOON_COLOR = 0xB8B8B8;
    /** Etoiles de fond (autres systemes) : double cercle et nom blancs. */
    private static final int DISTANT_STAR_COLOR = 0xFFFFFF;

    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    // ---------------------------------------------------------------- tables precalculees

    private static final float[] BODY_RING_COS = new float[BODY_RING_SEGMENTS + 1];
    private static final float[] BODY_RING_SIN = new float[BODY_RING_SEGMENTS + 1];
    private static final float[] STAR_RING_COS = new float[STAR_RING_SEGMENTS + 1];
    private static final float[] STAR_RING_SIN = new float[STAR_RING_SEGMENTS + 1];
    /** Anomalie excentrique des echantillons de l'orbite, {@code ORBIT_SAMPLES + 1} valeurs (la derniere ferme). */
    private static final double[] ORBIT_COS = new double[ORBIT_SAMPLES + 1];
    private static final double[] ORBIT_SIN = new double[ORBIT_SAMPLES + 1];

    static
    {
        circle(BODY_RING_COS, BODY_RING_SIN, BODY_RING_SEGMENTS);
        circle(STAR_RING_COS, STAR_RING_SIN, STAR_RING_SEGMENTS);
        for (int k = 0; k <= ORBIT_SAMPLES; k++) {
            double angle = 2.0 * Math.PI * k / ORBIT_SAMPLES;
            ORBIT_COS[k] = Math.cos(angle);
            ORBIT_SIN[k] = Math.sin(angle);
        }
        ORBIT_COS[ORBIT_SAMPLES] = ORBIT_COS[0];
        ORBIT_SIN[ORBIT_SAMPLES] = ORBIT_SIN[0];
    }

    private static void circle(float[] cos, float[] sin, int segments)
    {
        for (int j = 0; j <= segments; j++) {
            double angle = 2.0 * Math.PI * j / segments;
            cos[j] = (float) Math.cos(angle);
            sin[j] = (float) Math.sin(angle);
        }
        cos[segments] = cos[0];
        sin[segments] = sin[0];
    }

    // ---------------------------------------------------------------- etat de la frame (reutilise)

    /** Trouves par {@link #shouldRender}, utilises par {@link #render} de la meme frame. */
    private SpaceEnvironmentModule environment;
    private SpacePilotModule pilot;

    private final SpacePose pose = new SpacePose();
    private final SpaceSkyFrame frame = new SpaceSkyFrame();
    private final Vector3d[] positions = SpaceOrbits.newPositionArray();
    /** Par corps (indice du systeme) : direction monde, distance (km), opacite de reperage ({@code 0} = masque). */
    private final Vector3f[] directions = new Vector3f[SpaceLimits.MAX_BODIES];
    private final double[] distances = new double[SpaceLimits.MAX_BODIES];
    private final float[] fades = new float[SpaceLimits.MAX_BODIES];
    /** Par corps : plus grand demi-grand axe (km) de ses lunes, {@code 0} sans lune. */
    private final double[] moonReach = new double[SpaceLimits.MAX_BODIES];

    /** Regard de la camera et sa droite, axes du monde. */
    private final Vector3f look = new Vector3f();
    private final Vector3f right = new Vector3f();
    /** Base de l'anneau en cours : {@code bu x bv = -w}. */
    private final Vector3f bu = new Vector3f();
    private final Vector3f bv = new Vector3f();
    private final Vector3f direction = new Vector3f();
    private final Vector3f sampleA = new Vector3f();
    private final Vector3f sampleB = new Vector3f();
    private final Vector3f subA = new Vector3f();
    private final Vector3f subB = new Vector3f();
    private final Vector3f arcA = new Vector3f();
    private final Vector3f arcB = new Vector3f();
    private final Vector3f cross = new Vector3f();
    private final Vector3f transformed = new Vector3f();
    private final Quaterniond orbitRotation = new Quaterniond();
    private final Vector3d orbitPoint = new Vector3d();
    /** Nom retenu : texte, direction, rayon de son anneau (radians), couleur ARGB. */
    private String nameText;
    private final Vector3f nameDirection = new Vector3f();
    private double nameRing;
    private int nameColor;
    private final Vector3f side = new Vector3f();
    private final Vector3f anchor = new Vector3f();
    private final Matrix4f textMatrix = new Matrix4f();
    /** Lien : axe avant {@code F_W} (double puis float), tangente du grand cercle vers le referentiel, tiret. */
    private final Vector3d forwardAxis = new Vector3d();
    private final Vector3f linkFrom = new Vector3f();
    private final Vector3f linkTangent = new Vector3f();
    private final Vector3f linkA = new Vector3f();
    private final Vector3f linkB = new Vector3f();

    /**
     * Decoupe des orbites : par corps, cosinus du rayon de son anneau ({@link #bodyRing}), cosinus du filtre (anneau
     * + une corde) ; corps dont l'anneau est dessine (pointilles) ; candidats d'une corde ; bornes des
     * morceaux d'une corde (parametre {@code t} de la corde).
     */
    private final double[] ringCos = new double[SpaceLimits.MAX_BODIES];
    private final double[] clipCos = new double[SpaceLimits.MAX_BODIES];
    private final int[] dashBodies = new int[SpaceLimits.MAX_BODIES];
    private int dashCount;
    private final int[] candidates = new int[SpaceLimits.MAX_BODIES];
    private final double[] breaks = new double[2 + 3 * (SpaceLimits.MAX_BODIES + 1)];
    /** Vrai pendant {@link #drawOrbits} : {@link #chord} decoupe ; corps qui parcourt l'orbite ({@code -1} : aucun). */
    private boolean orbitClip;
    private int ownBody = -1;
    /** Pointilles en cours (depuis l'entree dans un anneau) et avancement dans la periode (radians). */
    private boolean dashRun;
    private double dashPhase;
    /** Tiret et periode des pointilles d'orbite (radians), pour la frame. */
    private double orbitDash;
    private double orbitDashPeriod;
    private final Vector3f pieceA = new Vector3f();
    private final Vector3f pieceB = new Vector3f();
    private final Vector3f dashA = new Vector3f();
    private final Vector3f dashB = new Vector3f();

    /** Tangente d'un pixel de reference au centre de l'ecran, et d'une unite de police (echelle du GUI). */
    private double pixelTan;
    private double fontTan;
    /** Demi-angle de la diagonale du champ de vision, marge comprise (radians). */
    private double halfDiagonal;
    /** Cosinus du test de champ d'une corde d'orbite. */
    private float chordCull;
    /** Rayon minimal et demi-epaisseur de l'anneau d'un corps (radians), pour la frame. */
    private double bodyRingMin;
    private double bodyRingHalf;

    public SpacePilotSkyRenderer()
    {
        for (int i = 0; i < directions.length; i++) directions[i] = new Vector3f();
    }

    // ---------------------------------------------------------------- SkyRenderer

    /**
     * Module Space Pilot du joueur local actif, au moins une partie allumee, Environment synchronise avec un systeme
     * courant, pas de distorsion du saut affichee ({@link SpaceWarpEffect#strength} nulle, T23).
     */
    @Override
    public boolean shouldRender(SkyRenderContext ctx)
    {
        environment = null;
        pilot = null;
        SpacePilotModule module = SpacePilotClient.module();
        if (module == null || !module.enabled()) return false;
        if (!module.orbits() && !module.bodies() && !module.stars() && !module.names() && !module.refLink()) {
            return false;
        }
        SpaceEnvironmentModule env = SpaceEnvironmentModule.client();
        if (env == null || !env.synced() || env.currentSystem() == null) return false;
        if (SpaceWarpEffect.strength(env, ctx.partialTick()) > 0f) return false;
        environment = env;
        pilot = module;
        return true;
    }

    /** Orbites, lien vers le referentiel, anneaux des corps, anneaux des etoiles, puis le nom (dessus). */
    @Override
    public void render(PoseStack stack, MultiBufferSource buffers, SkyRenderContext ctx)
    {
        SpaceEnvironmentModule env = environment;
        SpacePilotModule module = pilot;
        environment = null;
        pilot = null;
        if (env == null || module == null) return;
        SpaceSystem system = env.currentSystem();
        if (system == null) return;
        Matrix4f m = stack.last().pose();
        float partialTick = ctx.partialTick();
        Camera camera = ctx.camera();
        frame.set(env.pose(partialTick, pose));
        screen(camera);

        List<SpaceBody> bodies = system.bodies();
        int count = Math.min(env.bodyPositions(partialTick, positions), bodies.size());
        visibility(env, system, count);

        boolean names = module.names();
        nameText = null;
        float bestDot = names ? (float) Math.cos(Math.toRadians(module.nameAngle())) : 2f;

        VertexConsumer vc = buffers.getBuffer(OverlayType.OVERLAY_QUADS);
        if (module.orbits()) drawOrbits(vc, m, system, count, module.bodies());
        if (module.refLink()) drawReferenceLink(vc, m, env, system, count);
        boolean rings = module.bodies();
        for (int i = 0; i < count; i++) {
            float fade = fades[i];
            if (fade <= 0f) continue;
            SpaceBody body = bodies.get(i);
            Vector3f w = directions[i];
            double ring = bodyRing(body, distances[i]);
            if (rings) drawBodyRing(vc, m, w, ring, kindColor(body.kind()), fade);
            if (names) {
                float dot = w.dot(look);
                if (dot > bestDot) {
                    bestDot = dot;
                    pickName(body.name(), w, ring, nameColor(kindColor(body.kind()), fade));
                }
            }
        }
        if (module.stars() || names) bestDot = backgroundStars(vc, m, env, system, module.stars(), names, bestDot);
        if (nameText != null) drawName(buffers, m, camera);
        nameText = null;
    }

    // ---------------------------------------------------------------- frame

    /** Echelles de l'ecran de la frame : pixel de reference, unite de police, champ de vision ; regard et droite. */
    private void screen(Camera camera)
    {
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        double m11 = Math.abs(projection.m11());
        double m00 = Math.abs(projection.m00());
        if (!Double.isFinite(m11) || !(m11 > 0.0)) m11 = DEFAULT_PROJECTION_SCALE;
        if (!Double.isFinite(m00) || !(m00 > 0.0)) m00 = m11;
        Window window = Minecraft.getInstance().getWindow();
        double height = Math.max(1, window.getHeight());
        pixelTan = 2.0 * Math.max(1.0, height / REFERENCE_HEIGHT) / (m11 * height);
        fontTan = 2.0 * Math.max(1.0, window.getGuiScale()) / (m11 * height);
        halfDiagonal = Math.atan(Math.sqrt(1.0 / (m00 * m00) + 1.0 / (m11 * m11))) + CULL_MARGIN;
        chordCull = cosLimit(halfDiagonal + MAX_CHORD_ANGLE);
        bodyRingMin = pixels(BODY_RING_MIN_PX);
        bodyRingHalf = pixels(0.5 * BODY_RING_THICKNESS_PX);
        orbitDash = pixels(ORBIT_DASH_PX);
        orbitDashPeriod = orbitDash + pixels(ORBIT_SPACE_PX);
        look.set(camera.getLookVector());
        right.set(camera.getLeftVector()).negate();
    }

    /** Angle d'un nombre de pixels de reference compte depuis le centre de l'ecran. */
    private double pixels(double px) { return Math.atan(px * pixelTan); }

    /** Cosinus de {@code angle}, ou {@code -2} (aucun test) si l'angle atteint 180 degres. */
    private static float cosLimit(double angle) { return angle >= Math.PI ? -2f : (float) Math.cos(angle); }

    /** Vrai si un objet de direction {@code w} et de rayon angulaire {@code extent} peut toucher l'ecran. */
    private boolean inView(Vector3f w, double extent) { return w.dot(look) >= cosLimit(halfDiagonal + extent); }

    // ---------------------------------------------------------------- visibilite (D15, C47)

    /**
     * Directions, distances et opacite de reperage de chaque corps : etoiles et planetes toujours, lune si le monde
     * est a moins de {@code 10 x} ({@link #MOON_REACH_FACTOR}) le plus grand {@code a} des lunes de son parent, objet
     * mineur a moins de 0,5 UA de lui, referentiel toujours ; fondu lineaire sur les derniers 20 % du seuil.
     */
    private void visibility(SpaceEnvironmentModule env, SpaceSystem system, int count)
    {
        List<SpaceBody> bodies = system.bodies();
        Arrays.fill(moonReach, 0, count, 0.0);
        for (int i = 0; i < count; i++) {
            SpaceBody body = bodies.get(i);
            if (body.kind() != SpaceBodyKind.MOON) continue;
            int parent = system.parentIndex(i);
            if (parent < 0 || parent >= count) continue;
            moonReach[parent] = Math.max(moonReach[parent], body.orbit().semiMajorAxis() * SpaceUnits.AU_KM);
        }
        int reference = env.referenceBodyId();
        for (int i = 0; i < count; i++) {
            double d = frame.direction(positions[i], directions[i]);
            distances[i] = d;
            fades[i] = d > 0.0 ? fade(system, bodies.get(i), i, count, reference) : 0f;
        }
    }

    private float fade(SpaceSystem system, SpaceBody body, int index, int count, int reference)
    {
        if (body.id() == reference) return 1f;
        switch (body.kind()) {
            case MOON -> {
                int parent = system.parentIndex(index);
                if (parent < 0 || parent >= count) return 1f;
                double reach = MOON_REACH_FACTOR * moonReach[parent];
                return ramp(frame.position().distance(positions[parent]), reach);
            }
            case MINOR -> {
                return ramp(distances[index], MINOR_REACH_KM);
            }
            default -> {
                return 1f;
            }
        }
    }

    /** {@code 1} jusqu'a {@code 80 %} du seuil, {@code 0} au-dela du seuil, lineaire entre les deux. */
    private static float ramp(double distance, double threshold)
    {
        if (!(threshold > 0.0)) return 0f;
        double start = threshold * (1.0 - FADE_FRACTION);
        if (distance <= start) return 1f;
        if (distance >= threshold) return 0f;
        return (float) ((threshold - distance) / (threshold * FADE_FRACTION));
    }

    // ---------------------------------------------------------------- orbites

    /**
     * Ellipse de chaque corps repere autour de la position <b>actuelle</b> de son parent, {@link #ORBIT_SAMPLES}
     * points reguliers en anomalie excentrique dans le plan de l'orbite ({@code R_o}, par. 3), chacun projete sur la
     * sphere du ciel ; calcul en {@code double} relatif a la pose ({@link SpaceSkyFrame#direction}). Une orbite qui
     * parait minuscule s'efface en fondu ({@link #ORBIT_MIN_PX} a {@link #ORBIT_FULL_PX}) au lieu de basculer.
     * <p>
     * Un segment vu sous plus de {@link #MAX_CHORD_ANGLE} (il passe pres de l'oeil : orbite du corps voisin) est
     * subdivise en anomalie excentrique, puis chaque morceau encore trop ouvert est dessine en arc de grand cercle (la
     * projection exacte d'un segment droit) : aucun morceau n'est coupe selon un seuil d'angle, qui changeait
     * d'une frame a l'autre quand la grille d'echantillons defilait devant le monde (clignotement, T18).
     */
    private void drawOrbits(VertexConsumer vc, Matrix4f m, SpaceSystem system, int count, boolean rings)
    {
        List<SpaceBody> bodies = system.bodies();
        float halfWidth = (float) (OVERLAY_RADIUS * 0.5 * ORBIT_THICKNESS_PX * pixelTan);
        clipCircles(bodies, count, rings);
        orbitClip = true;
        try {
            for (int i = 0; i < count; i++) {
                drawOrbit(vc, m, system, bodies, count, i, halfWidth);
            }
        } finally {
            orbitClip = false;
            ownBody = -1;
        }
    }

    /**
     * Cercles de decoupe de la frame : anneau de chaque corps vu (meme rayon que {@link #drawBodyRing}, qu'il soit
     * dessine ou non) ; liste des corps dont l'anneau est dessine ({@code rings} et repere), seuls a donner des
     * pointilles.
     */
    private void clipCircles(List<SpaceBody> bodies, int count, boolean rings)
    {
        dashCount = 0;
        for (int i = 0; i < count; i++) {
            if (!(distances[i] > 0.0)) continue;
            double ring = bodyRing(bodies.get(i), distances[i]);
            ringCos[i] = Math.cos(ring);
            double outer = ring + MAX_CHORD_ANGLE;
            clipCos[i] = outer >= Math.PI ? -2.0 : Math.cos(outer);
            if (rings && fades[i] > 0f) dashBodies[dashCount++] = i;
        }
    }

    /** Orbite du corps {@code i} (voir {@link #drawOrbits}). */
    private void drawOrbit(VertexConsumer vc, Matrix4f m, SpaceSystem system, List<SpaceBody> bodies, int count,
                           int i, float halfWidth)
    {
        float fade = fades[i];
        if (fade <= 0f) return;
        SpaceBody body = bodies.get(i);
        SpaceOrbit orbit = body.orbit();
        int parent = system.parentIndex(i);
        if (orbit == null || parent < 0 || parent >= count) return;
        double a = orbit.semiMajorAxis() * SpaceUnits.AU_KM;
        double e = orbit.eccentricity();
        double parentDistance = distances[parent];
        if (parentDistance > 0.0) {
            double px = a * (1.0 + e) / parentDistance / pixelTan;
            fade *= (float) Math.min(1.0, (px - ORBIT_MIN_PX) / (ORBIT_FULL_PX - ORBIT_MIN_PX));
            if (!(fade > 0f)) return;
        }
        double b = a * Math.sqrt(1.0 - e * e);
        orbitRotation.rotationYXZ(-Math.toRadians(orbit.yaw()), Math.toRadians(orbit.pitch()),
                Math.toRadians(orbit.roll()));
        int color = kindColor(body.kind());
        float r = red(color);
        float g = green(color);
        float bl = blue(color);
        float alpha = ORBIT_ALPHA * fade;
        Vector3d origin = positions[parent];
        ownBody = distances[i] > 0.0 ? i : -1;
        dashRun = false;
        Vector3f from = sampleA;
        Vector3f to = sampleB;
        boolean previous = orbitSample(origin, a, b, e, ORBIT_COS[0], ORBIT_SIN[0], from);
        for (int k = 1; k <= ORBIT_SAMPLES; k++) {
            boolean current = orbitSample(origin, a, b, e, ORBIT_COS[k], ORBIT_SIN[k], to);
            if (previous && current) {
                if (from.dot(to) >= COS_MAX_CHORD) {
                    chord(vc, m, from, to, halfWidth, r, g, bl, alpha);
                } else {
                    subdivide(vc, m, origin, a, b, e, k, from, to, halfWidth, r, g, bl, alpha);
                }
            } else {
                dashRun = false;
            }
            Vector3f swap = from;
            from = to;
            to = swap;
            previous = current;
        }
    }

    /** Direction du point d'anomalie excentrique {@code (cos E, sin E)} ; faux si confondu avec le monde. */
    private boolean orbitSample(Vector3d origin, double a, double b, double e, double cosE, double sinE, Vector3f dest)
    {
        orbitPoint.set(a * (cosE - e), 0.0, b * sinE);
        orbitRotation.transform(orbitPoint);
        return frame.direction(origin.x + orbitPoint.x, origin.y + orbitPoint.y, origin.z + orbitPoint.z, dest) > 0.0;
    }

    /**
     * Segment {@code k - 1 -> k} trop ouvert : redecoupe en {@code n} morceaux reguliers en anomalie excentrique
     * (suit la courbe pres de l'oeil), chacun dessine par {@link #arc}.
     */
    private void subdivide(VertexConsumer vc, Matrix4f m, Vector3d origin, double a, double b, double e, int k,
                           Vector3f from, Vector3f to, float halfWidth, float r, float g, float bl, float alpha)
    {
        int n = Math.min(MAX_SUBDIVISIONS, (int) Math.ceil(angle(from.dot(to)) / MAX_CHORD_ANGLE));
        double start = 2.0 * Math.PI * (k - 1) / ORBIT_SAMPLES;
        double step = 2.0 * Math.PI / ORBIT_SAMPLES / n;
        subA.set(from);
        boolean previous = true;
        for (int j = 1; j <= n; j++) {
            boolean current;
            Vector3f next;
            if (j == n) {
                next = to;
                current = true;
            } else {
                double E = start + j * step;
                next = subB;
                current = orbitSample(origin, a, b, e, Math.cos(E), Math.sin(E), subB);
            }
            if (previous && current) {
                arc(vc, m, subA, next, halfWidth, r, g, bl, alpha);
            } else {
                dashRun = false;
            }
            subA.set(next);
            previous = current;
        }
    }

    /**
     * Segment droit de l'espace entre deux directions : sa projection est l'arc de grand cercle le plus court ;
     * decoupe en cordes d'au plus {@link #MAX_CHORD_ANGLE}. Ignore seulement si les directions sont quasi opposees
     * (le segment passe a travers l'oeil).
     */
    private void arc(VertexConsumer vc, Matrix4f m, Vector3f from, Vector3f to, float halfWidth, float r, float g,
                     float b, float a)
    {
        float dot = from.dot(to);
        if (dot >= COS_MAX_CHORD) {
            chord(vc, m, from, to, halfWidth, r, g, b, a);
            return;
        }
        if (dot < COS_THROUGH_EYE) {
            dashRun = false;
            return;
        }
        double theta = angle(dot);
        double sinTheta = Math.sin(theta);
        int n = Math.min(MAX_SUBDIVISIONS, (int) Math.ceil(theta / MAX_CHORD_ANGLE));
        arcA.set(from);
        for (int i = 1; i <= n; i++) {
            if (i == n) {
                arcB.set(to);
            } else {
                double t = i / (double) n;
                float wa = (float) (Math.sin((1.0 - t) * theta) / sinTheta);
                float wb = (float) (Math.sin(t * theta) / sinTheta);
                arcB.set(from).mul(wa).fma(wb, to).normalize();
            }
            chord(vc, m, arcA, arcB, halfWidth, r, g, b, a);
            arcA.set(arcB);
        }
    }

    /** Angle entre deux directions unitaires de produit scalaire {@code dot}. */
    private static double angle(float dot) { return Math.acos(Math.max(-1f, Math.min(1f, dot))); }

    /**
     * Corde d'au plus {@link #MAX_CHORD_ANGLE} : decoupee par les anneaux pendant les orbites ({@link #orbitChord}),
     * trait plein sinon (lien du referentiel).
     */
    private void chord(VertexConsumer vc, Matrix4f m, Vector3f from, Vector3f to, float halfWidth, float r, float g,
                       float b, float a)
    {
        if (orbitClip) {
            orbitChord(vc, m, from, to, halfWidth, r, g, b, a);
        } else {
            strip(vc, m, from, to, halfWidth, r, g, b, a);
        }
    }

    // ---------------------------------------------------------------- decoupe des orbites par les anneaux

    /**
     * Corde d'orbite decoupee par les cercles angulaires des anneaux ({@code docs/space/04-mathematiques.md} par. 9) :
     * rien dans l'anneau du corps qui parcourt l'orbite ({@link #ownBody}), pointilles dans l'anneau d'un autre corps
     * dont l'anneau est dessine, trait plein ailleurs.
     * <p>
     * Methode exacte : le point {@code P(t) = A + t (B - A)} de la corde est dans le cercle de centre {@code w} et de
     * rayon {@code rho} si {@code P.w >= cos(rho) |P|}. Les bornes ou cette condition change sont les racines de
     * {@code (P.w)^2 - cos^2(rho) |P|^2} et de {@code P.w} (polynomes de {@code t}) ; la corde est coupee a toutes
     * ces bornes, puis chaque morceau est classe a son milieu. Les bornes varient continument avec la pose : pas de
     * morceau qui saute d'une frame a l'autre. Seuls les corps dont le cercle (elargi d'une corde) contient le milieu
     * de la corde sont testes. Le test de champ ne vient qu'a l'emission : l'avancement des pointilles ne depend pas
     * de la camera.
     */
    private void orbitChord(VertexConsumer vc, Matrix4f m, Vector3f from, Vector3f to, float halfWidth, float r,
                            float g, float b, float a)
    {
        double mx = from.x + to.x;
        double my = from.y + to.y;
        double mz = from.z + to.z;
        double mid = Math.sqrt(mx * mx + my * my + mz * mz);
        int n = 0;
        breaks[n++] = 0.0;
        breaks[n++] = 1.0;
        int own = ownBody >= 0 && near(ownBody, mx, my, mz, mid) ? ownBody : -1;
        if (own >= 0) n = addBreaks(from, to, own, n);
        int candidateCount = 0;
        for (int k = 0; k < dashCount; k++) {
            int j = dashBodies[k];
            if (j == ownBody || !near(j, mx, my, mz, mid)) continue;
            candidates[candidateCount++] = j;
            n = addBreaks(from, to, j, n);
        }
        if (own < 0 && candidateCount == 0) {
            // Aucun cercle a proximite : trait plein entier (une corde entierement dans un cercle n'a aucune borne
            // interieure mais doit etre classee : on ne prend ce raccourci que sans candidat).
            dashRun = false;
            strip(vc, m, from, to, halfWidth, r, g, b, a);
            return;
        }
        sortBreaks(n);
        double dx = (double) to.x - from.x;
        double dy = (double) to.y - from.y;
        double dz = (double) to.z - from.z;
        for (int k = 0; k + 1 < n; k++) {
            double t0 = breaks[k];
            double t1 = breaks[k + 1];
            if (!(t1 - t0 > MIN_PIECE)) continue;
            double tm = 0.5 * (t0 + t1);
            double px = from.x + tm * dx;
            double py = from.y + tm * dy;
            double pz = from.z + tm * dz;
            if (own >= 0 && inside(own, px, py, pz)) {
                dashRun = false;
                continue;
            }
            boolean dashed = false;
            for (int c = 0; c < candidateCount && !dashed; c++) dashed = inside(candidates[c], px, py, pz);
            point(from, dx, dy, dz, t0, pieceA);
            point(from, dx, dy, dz, t1, pieceB);
            if (dashed) {
                dashes(vc, m, pieceA, pieceB, halfWidth, r, g, b, a);
            } else {
                dashRun = false;
                strip(vc, m, pieceA, pieceB, halfWidth, r, g, b, a);
            }
        }
    }

    /** Vrai si le milieu de la corde ({@code m}, de norme {@code length}) est dans le cercle elargi d'une corde. */
    private boolean near(int body, double mx, double my, double mz, double length)
    {
        Vector3f w = directions[body];
        return mx * w.x + my * w.y + mz * w.z >= clipCos[body] * length;
    }

    /** Vrai si la direction de {@code p} (non normee) est dans l'anneau du corps : {@code p.w >= cos(rho) |p|}. */
    private boolean inside(int body, double px, double py, double pz)
    {
        Vector3f w = directions[body];
        return px * w.x + py * w.y + pz * w.z >= ringCos[body] * Math.sqrt(px * px + py * py + pz * pz);
    }

    /**
     * Ajoute les parametres {@code t} de {@code ]0, 1[} ou la corde {@code A -> B} entre ou sort du cercle du corps :
     * racines de {@code q(t) = (P.w)^2 - c^2 |P|^2} (formule stable) et de {@code P.w} (changement de demi-espace,
     * utile si le rayon depasse 90 degres).
     *
     * @return le nouveau nombre de bornes
     */
    private int addBreaks(Vector3f from, Vector3f to, int body, int n)
    {
        // Tout en double : q(0) = (A.w)^2 - c^2 |A|^2 est une difference de deux nombres proches de 1 (petits
        // cercles), un produit en float y decalait les bornes d'environ 1e-3 corde.
        Vector3f w = directions[body];
        double wx = w.x;
        double wy = w.y;
        double wz = w.z;
        double ax = from.x;
        double ay = from.y;
        double az = from.z;
        double c2 = ringCos[body] * ringCos[body];
        double dx = to.x - ax;
        double dy = to.y - ay;
        double dz = to.z - az;
        double f0 = ax * wx + ay * wy + az * wz;
        double f1 = dx * wx + dy * wy + dz * wz;
        double g0 = ax * ax + ay * ay + az * az;
        double g1 = ax * dx + ay * dy + az * dz;
        double g2 = dx * dx + dy * dy + dz * dz;
        double qa = f1 * f1 - c2 * g2;
        double qb = 2.0 * (f0 * f1 - c2 * g1);
        double qc = f0 * f0 - c2 * g0;
        double scale = Math.abs(qa) + Math.abs(qb) + Math.abs(qc);
        if (scale > 0.0) {
            if (Math.abs(qa) <= 1.0e-12 * scale) {
                if (qb != 0.0) n = addBreak(-qc / qb, n);
            } else {
                double disc = qb * qb - 4.0 * qa * qc;
                if (disc >= 0.0) {
                    double q = -0.5 * (qb + Math.copySign(Math.sqrt(disc), qb));
                    n = addBreak(q / qa, n);
                    if (q != 0.0) n = addBreak(qc / q, n);
                }
            }
        }
        if (f1 != 0.0) n = addBreak(-f0 / f1, n);
        return n;
    }

    /** Tri par insertion des {@code n} bornes (peu nombreuses, sans allocation). */
    private void sortBreaks(int n)
    {
        for (int i = 1; i < n; i++) {
            double t = breaks[i];
            int k = i;
            while (k > 0 && breaks[k - 1] > t) {
                breaks[k] = breaks[k - 1];
                k--;
            }
            breaks[k] = t;
        }
    }

    private int addBreak(double t, int n)
    {
        if (t > 0.0 && t < 1.0 && n < breaks.length) breaks[n++] = t;
        return n;
    }

    /** {@code dest = normalize(A + t d)}. */
    private static void point(Vector3f from, double dx, double dy, double dz, double t, Vector3f dest)
    {
        dest.set((float) (from.x + t * dx), (float) (from.y + t * dy), (float) (from.z + t * dz)).normalize();
    }

    /**
     * Pointilles sur le morceau {@code A -> B} (au plus une corde) : tirets de {@link #orbitDash}, periode
     * {@link #orbitDashPeriod}, en continuite avec le morceau precedent de la meme traversee ({@link #dashPhase}) ; une
     * nouvelle traversee commence par un tiret au bord de l'anneau.
     */
    private void dashes(VertexConsumer vc, Matrix4f m, Vector3f from, Vector3f to, float halfWidth, float r, float g,
                        float b, float a)
    {
        if (!dashRun) {
            dashRun = true;
            dashPhase = 0.0;
        }
        double cx = (double) from.y * to.z - (double) from.z * to.y;
        double cy = (double) from.z * to.x - (double) from.x * to.z;
        double cz = (double) from.x * to.y - (double) from.y * to.x;
        double dot = (double) from.x * to.x + (double) from.y * to.y + (double) from.z * to.z;
        double length = Math.atan2(Math.sqrt(cx * cx + cy * cy + cz * cz), dot);
        if (!(length > 0.0)) return;
        double dx = (double) to.x - from.x;
        double dy = (double) to.y - from.y;
        double dz = (double) to.z - from.z;
        double s = 0.0;
        for (int i = 0; i < 2 * MAX_PIECE_DASHES && s < length; i++) {
            double phase = dashPhase;
            boolean on = phase < orbitDash;
            double step = Math.min(length - s, (on ? orbitDash : orbitDashPeriod) - phase);
            if (on && step > 0.0) {
                point(from, dx, dy, dz, s / length, dashA);
                point(from, dx, dy, dz, (s + step) / length, dashB);
                strip(vc, m, dashA, dashB, halfWidth, r, g, b, a);
            }
            s += step;
            dashPhase = phase + step;
            if (dashPhase >= orbitDashPeriod - 1.0e-12) dashPhase = 0.0;
        }
    }

    // ---------------------------------------------------------------- primitives de trait

    /**
     * Trait fin entre deux directions proches (au plus {@link #MAX_CHORD_ANGLE}), sur la sphere de la surcouche : la
     * corde se projette exactement comme le segment de l'espace (meme plan par l'oeil), et le quad, decale selon la
     * normale de ce plan, est vu de face. Ignore si les deux extremites sont hors du champ.
     */
    private void strip(VertexConsumer vc, Matrix4f m, Vector3f from, Vector3f to, float halfWidth, float r, float g,
                       float b, float a)
    {
        if (from.dot(look) < chordCull && to.dot(look) < chordCull) return;
        cross.set(from).cross(to);
        float length = cross.length();
        if (!(length > 1.0e-7f)) return;
        cross.mul(halfWidth / length);
        float k = OVERLAY_RADIUS;
        vertex(vc, m, k * from.x - cross.x, k * from.y - cross.y, k * from.z - cross.z, r, g, b, a);
        vertex(vc, m, k * from.x + cross.x, k * from.y + cross.y, k * from.z + cross.z, r, g, b, a);
        vertex(vc, m, k * to.x + cross.x, k * to.y + cross.y, k * to.z + cross.z, r, g, b, a);
        vertex(vc, m, k * to.x - cross.x, k * to.y - cross.y, k * to.z - cross.z, r, g, b, a);
    }

    // ---------------------------------------------------------------- lien vers le referentiel (T19)

    /**
     * Lien pointille (par. 9) sur l'arc de grand cercle le plus court entre l'axe avant du monde {@code F_W} (centre du
     * cercle avant du joystick, reglage de l'Environment) et la direction du corps de reference
     * ({@code referenceBodyId}, relu a chaque frame : le lien suit un changement de referentiel). Il part du bord du
     * cercle avant ({@link #FORWARD_RING_ANGLE}) et s'arrete au bord de l'anneau du corps ({@link #bodyRing}), avec un
     * ecart de {@link #LINK_GAP_PX} pixels de chaque cote ; rien si ces bords se recouvrent (avant dans le corps) ou si
     * les deux directions sont quasi opposees (grand cercle indefini). Tirets de longueur angulaire constante a l'ecran,
     * comptes depuis l'axe avant (ils ne defilent pas quand le referentiel bouge) ; chaque tiret est dessine par
     * {@link #arc}. Couleur du corps de reference ({@link #kindColor}).
     */
    private void drawReferenceLink(VertexConsumer vc, Matrix4f m, SpaceEnvironmentModule env, SpaceSystem system,
                                   int count)
    {
        int index = system.indexOf(env.referenceBodyId());
        if (index < 0 || index >= count || !(distances[index] > 0.0)) return;
        SpaceBody body = system.bodies().get(index);
        Vector3f target = directions[index];
        env.settings().forward(forwardAxis);
        linkFrom.set((float) forwardAxis.x, (float) forwardAxis.y, (float) forwardAxis.z).normalize();
        float dot = linkFrom.dot(target);
        if (dot < COS_THROUGH_EYE) return;
        double gap = pixels(LINK_GAP_PX);
        double start = FORWARD_RING_ANGLE + gap;
        double end = angle(dot) - bodyRing(body, distances[index]) - gap;
        if (!(end > start)) return;
        linkTangent.set(target).fma(-dot, linkFrom);
        float length = linkTangent.length();
        if (!(length > 1.0e-6f)) return;
        linkTangent.mul(1f / length);
        double dash = pixels(LINK_DASH_PX);
        double period = dash + pixels(LINK_SPACE_PX);
        int dashes = (int) Math.ceil((end - start) / period);
        if (dashes > MAX_LINK_DASHES) {
            double stretch = (end - start) / (MAX_LINK_DASHES * period);
            dash *= stretch;
            period *= stretch;
            dashes = MAX_LINK_DASHES;
        }
        float halfWidth = (float) (OVERLAY_RADIUS * 0.5 * LINK_THICKNESS_PX * pixelTan);
        int color = kindColor(body.kind());
        float r = red(color);
        float g = green(color);
        float b = blue(color);
        for (int i = 0; i < dashes; i++) {
            double from = start + i * period;
            double to = Math.min(end, from + dash);
            if (!(to > from)) break;
            greatCircle(from, linkA);
            greatCircle(to, linkB);
            arc(vc, m, linkA, linkB, halfWidth, r, g, b, LINK_ALPHA);
        }
    }

    /** Point du grand cercle du lien a l'angle {@code angle} (radians) de l'axe avant, vers le referentiel. */
    private void greatCircle(double angle, Vector3f dest)
    {
        dest.set(linkFrom).mul((float) Math.cos(angle)).fma((float) Math.sin(angle), linkTangent);
    }

    // ---------------------------------------------------------------- anneaux des corps

    /** Rayon de l'anneau d'un corps (radians) : {@code max(alpha x 1,6, alpha_min)}, plafonne. */
    private double bodyRing(SpaceBody body, double distance)
    {
        double alpha = SpaceSkyFrame.angularRadius(body.radius(), distance);
        return Math.min(MAX_RING_ANGLE, Math.max(alpha * BODY_RING_FACTOR, bodyRingMin));
    }

    private void drawBodyRing(VertexConsumer vc, Matrix4f m, Vector3f w, double ring, int color, float fade)
    {
        if (!inView(w, ring + bodyRingHalf)) return;
        basis(w);
        ring(vc, m, w, ring, bodyRingHalf, BODY_RING_COS, BODY_RING_SIN, BODY_RING_SEGMENTS,
                red(color), green(color), blue(color), BODY_RING_ALPHA * fade);
    }

    // ---------------------------------------------------------------- etoiles de fond

    /**
     * Double anneau autour de chaque etoile de fond ({@code draw}) et recherche du nom ({@code names}) ; nom d'une
     * etoile = nom de son systeme (T15).
     *
     * @return le meilleur produit scalaire regard / objet apres les etoiles
     */
    private float backgroundStars(VertexConsumer vc, Matrix4f m, SpaceEnvironmentModule env, SpaceSystem current,
                                  boolean draw, boolean names, float bestDot)
    {
        List<BackgroundStar> stars = env.backgroundStars();
        if (stars.isEmpty()) return bestDot;
        double inner = Math.max(STAR_INNER_MIN_ANGLE, pixels(STAR_INNER_PX));
        double outer = inner + pixels(STAR_OUTER_PX - STAR_INNER_PX);
        double half = pixels(0.5 * STAR_RING_THICKNESS_PX);
        float cull = cosLimit(halfDiagonal + outer + half);
        float starRed = red(DISTANT_STAR_COLOR);
        float starGreen = green(DISTANT_STAR_COLOR);
        float starBlue = blue(DISTANT_STAR_COLOR);
        for (int i = 0, n = stars.size(); i < n; i++) {
            BackgroundStar star = stars.get(i);
            if (!(frame.galacticDirection(current, star, direction) > 0.0)) continue;
            float dot = direction.dot(look);
            if (draw && dot >= cull) {
                basis(direction);
                ring(vc, m, direction, inner, half, STAR_RING_COS, STAR_RING_SIN, STAR_RING_SEGMENTS,
                        starRed, starGreen, starBlue, STAR_ALPHA);
                ring(vc, m, direction, outer, half, STAR_RING_COS, STAR_RING_SIN, STAR_RING_SEGMENTS,
                        starRed, starGreen, starBlue, STAR_ALPHA);
            }
            if (names && dot > bestDot) {
                bestDot = dot;
                pickName(star.name(), direction, outer, 0xFF000000 | DISTANT_STAR_COLOR);
            }
        }
        return bestDot;
    }

    // ---------------------------------------------------------------- nom au regard

    private void pickName(String text, Vector3f w, double ring, int argb)
    {
        nameText = text;
        nameDirection.set(w);
        nameRing = ring;
        nameColor = argb;
    }

    /** Couleur du nom d'un corps : celle de son anneau ({@code rgb}), opaque sauf en fondu. */
    private static int nameColor(int rgb, float fade)
    {
        int alpha = Math.max(NAME_MIN_ALPHA, Math.round(255f * Math.min(1f, fade)));
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    /**
     * Couleur de reperage d'un corps du systeme courant selon son type (anneau, orbite, nom) : etoile jaune, lune
     * grise, planete et objet mineur bleus.
     */
    private static int kindColor(SpaceBodyKind kind)
    {
        return switch (kind) {
            case STAR -> STAR_COLOR;
            case MOON -> MOON_COLOR;
            case PLANET, MINOR -> PLANET_COLOR;
        };
    }

    /**
     * Nom a droite de l'anneau (droite de la camera), ligne centree verticalement, face camera
     * ({@code camera.rotation}, comme les etiquettes vanilla) ; taille d'une unite de police = echelle du GUI en
     * pixels, a la profondeur du point d'ancrage.
     */
    private void drawName(MultiBufferSource buffers, Matrix4f m, Camera camera)
    {
        Vector3f w = nameDirection;
        double offset = Math.min(nameRing, NAME_MAX_OFFSET) + pixels(NAME_GAP_PX);
        side.set(right).fma(-right.dot(w), w);
        float length = side.length();
        if (length < 1.0e-3f) return;
        side.mul(1f / length);
        float c = (float) Math.cos(offset);
        float s = (float) Math.sin(offset);
        anchor.set(w).mul(c).fma(s, side).mul(OVERLAY_RADIUS);
        float depth = anchor.dot(look);
        if (depth < 1f) return;
        float scale = (float) (depth * fontTan);
        textMatrix.set(m).translate(anchor.x, anchor.y, anchor.z).rotate(camera.rotation())
                .scale(-scale, -scale, scale);
        Font font = Minecraft.getInstance().font;
        font.drawInBatch(nameText, 0f, NAME_Y, nameColor, false, textMatrix, buffers, Font.DisplayMode.NORMAL,
                NAME_BACKGROUND, LIGHT);
    }

    // ---------------------------------------------------------------- primitives

    /**
     * Anneau de rayon angulaire {@code angle} et de demi-epaisseur {@code half} (radians) autour de {@code w}, sur la
     * sphere de la surcouche (base {@link #basis} faite).
     */
    private void ring(VertexConsumer vc, Matrix4f m, Vector3f w, double angle, double half, float[] cos, float[] sin,
                      int segments, float r, float g, float b, float a)
    {
        double innerAngle = Math.max(0.0, angle - half);
        double outerAngle = angle + half;
        float ic = (float) Math.cos(innerAngle) * OVERLAY_RADIUS;
        float is = (float) Math.sin(innerAngle) * OVERLAY_RADIUS;
        float oc = (float) Math.cos(outerAngle) * OVERLAY_RADIUS;
        float os = (float) Math.sin(outerAngle) * OVERLAY_RADIUS;
        for (int j = 0; j < segments; j++) {
            ringVertex(vc, m, w, ic, is, cos[j], sin[j], r, g, b, a);
            ringVertex(vc, m, w, oc, os, cos[j], sin[j], r, g, b, a);
            ringVertex(vc, m, w, oc, os, cos[j + 1], sin[j + 1], r, g, b, a);
            ringVertex(vc, m, w, ic, is, cos[j + 1], sin[j + 1], r, g, b, a);
        }
    }

    /** Sommet {@code c w + s (ct bu + st bv)} ({@code c}, {@code s} deja multiplies par le rayon). */
    private void ringVertex(VertexConsumer vc, Matrix4f m, Vector3f w, float c, float s, float ct, float st, float r,
                            float g, float b, float a)
    {
        float u = ct * s;
        float v = st * s;
        vertex(vc, m, c * w.x + u * bu.x + v * bv.x, c * w.y + u * bu.y + v * bv.y, c * w.z + u * bu.z + v * bv.z,
                r, g, b, a);
    }

    /**
     * Base perpendiculaire a {@code w} : {@code bu = normalize(w x a)}, {@code bv = bu x w} ; {@code a} = haut du
     * monde, ou est si {@code w} est presque vertical (copie de {@code SpaceSkyRenderer}).
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

    /** Sommet transforme sans allocation ({@code VertexConsumer.vertex(Matrix4f, ...)} cree un vecteur). */
    private void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a)
    {
        m.transformPosition(x, y, z, transformed);
        vc.vertex(transformed.x, transformed.y, transformed.z).color(r, g, b, a).uv2(LIGHT).endVertex();
    }

    private static float red(int argb) { return (argb >> 16 & 0xFF) / 255f; }

    private static float green(int argb) { return (argb >> 8 & 0xFF) / 255f; }

    private static float blue(int argb) { return (argb & 0xFF) / 255f; }

    // ---------------------------------------------------------------- RenderType

    /**
     * {@code RenderType} local (sous-classe pour acceder aux etats proteges de {@code RenderStateShard} ; jamais
     * instanciee) : quads translucides double face, <b>sans ecriture de profondeur</b> (test de profondeur garde : la
     * surcouche est devant tout le ciel deja dessine) et sans tri a l'envoi (rien ne s'occulte). Partage avec
     * {@link SpaceJumpRenderer} (T22).
     */
    static final class OverlayType extends RenderType
    {
        static final RenderType OVERLAY_QUADS = create(HoloLib.MODID + ":space_pilot_overlay_quads",
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
