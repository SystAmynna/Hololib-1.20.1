package com.amynna.hololib.client.render.modules.monde.areadisplayer;

import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.modules.monde.areadisplayer.AreaMesher;
import com.amynna.hololib.client.modules.monde.areadisplayer.AreaTerrainClient;
import com.amynna.hololib.client.modules.monde.areadisplayer.AreaView;
import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.holo.edit.HoloEditMath;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerCube;
import com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerModule;
import com.amynna.hololib.modules.monde.areadisplayer.AreaGrid;
import com.amynna.hololib.modules.monde.areadisplayer.terrain.AreaTerrainPackets;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;


/**
 * Renderer de l'{@link AreaDisplayerCube} : dans le repere local du pave (pile placee par le pont monde), d'apres le
 * module (options, proportions) et la vue client du terrain ({@link AreaTerrainClient#view}).
 * <ul>
 *   <li><b>Cadre</b> (option) : douze aretes en bandes fines croisees, couleur d'accent ;</li>
 *   <li><b>terrain</b> : maillage glouton par tuile de 64 voxels ({@link AreaMesher}), garde sur le GPU
 *       ({@link AreaTileGpu}) et dessine tuile par tuile sans re-emettre de vertex, <b>tout l'hologramme</b> (aucun
 *       budget) : seules les tuiles hors du champ de la camera (sphere englobante dans le repere de la vue) sont
 *       omises. <b>Monochrome cyan</b> ({@link ConfigWindow#ACCENT}), ombre par direction (dessus clair, dessous
 *       sombre) cuit dans les sommets ; couleur du theme et transparence posees par {@code setShaderColor} ; fluides
 *       plus transparents et plus bleus. Option <b>Culling</b> ({@code FLAG_CULLING}) : une seule couche (pre-passe
 *       de profondeur puis couleur seulement sur la surface la plus proche, {@link Types#TERRAIN_DEPTH} /
 *       {@link Types#TERRAIN_COLOR}) ; sinon translucide ({@link Types#TERRAIN}), tuiles de la plus lointaine a la
 *       plus proche ;</li>
 *   <li><b>points</b> : disque face camera par position suivie (interpolee), joueurs verts, entites hostiles rouges,
 *       autres jaunes, selon les options du module ; le rayon grossit quand la camera s'approche
 *       ({@link #dotRadius(float)}) et reste tres petit de loin ;</li>
 *   <li><b>trainees</b> (options) : bandes croisees du chemin des 5 dernieres secondes, en fondu, sans ecriture de
 *       profondeur ({@link Types#GLOW}) ;</li>
 *   <li><b>noms</b> (famille {@code WORLD}) : le point le plus proche de l'axe du regard, dans un cone de
 *       {@value #GAZE_DEGREES} degres (ou de son rayon apparent si plus grand) et a moins de {@value #LABEL_DISTANCE}
 *       blocs fois la taille, montre son nom au-dessus de lui (plus grand ; echelle proportionnelle au rayon du
 *       point, {@link #NAME_TEXT_RATIO}) ; les joueurs a moins de
 *       {@value #NEAR_NAMES} blocs fois la taille montrent aussi le leur ({@value #MAX_NEAR_NAMES} au plus), sur un
 *       fond sombre ; « Aucune zone » / « Scan n % » au-dessus du pave.</li>
 * </ul>
 * <b>Premier plan</b> : points, trainees et noms sont ramenes vers l'oeil le long de leur rayon (homothetie centree sur
 * {@link #EYE}, pas sur l'origine de la pile : le bobbing est dans la projection), devant la sphere
 * englobante du pave ({@link #priorityLimit}) : toujours devant le terrain holographique, mais caches par les vrais
 * blocs entre la camera et le pave.
 * Le pave est dessine pour tous ; tailles en blocs a l'echelle par defaut, multipliees par la taille relative du pave.
 * Aucune allocation par frame dans ce code (le {@code Font} vanilla cree son petit objet par texte) ; aucun etat GL
 * modifie.
 */
public final class AreaDisplayerCubeRenderer implements HoloObjectRenderer<AreaDisplayerCube>
{
    /** Demi-epaisseur des aretes, en blocs (a l'echelle par defaut). */
    private static final float THICKNESS = 0.01f;
    private static final HoloColor EDGE_COLOR = HoloColor.primary(0x90);
    private static final HoloColor SOLID = HoloColor.primary(0xFF);
    private static final HoloColor FLUID = HoloColor.secondary(0xFF).darker(0.1f);
    /** Opacite du fluide par rapport a celle du solide (plus transparent). */
    private static final float FLUID_RATIO = 0x50 / (float) 0xB0;
    /** Opacite en dessous de laquelle le terrain n'est pas dessine. */
    private static final float MIN_OPACITY = 0.004f;

    /** Tuiles envoyees au GPU au plus par frame (evite un a-coup au chargement d'une grande zone). */
    private static final int UPLOADS_PER_FRAME = 6;
    /** Marge sur le champ de vision (variations du FOV : sprint, effets), pour ne jamais couper trop tot. */
    private static final float FOV_MARGIN = 1.3f;

    /** Rayon d'un point, en blocs (a l'echelle par defaut) : tres petit au loin, grand de pres. */
    private static final float DOT_MIN = 0.008f;
    private static final float DOT_MAX = 0.035f;
    /** Distances (blocs a l'echelle par defaut) : rayon maximal en dessous de {@code DOT_NEAR}, minimal au-dela de {@code DOT_FAR}. */
    private static final float DOT_NEAR = 2f;
    private static final float DOT_FAR = 16f;
    /** Bornes du rayon apparent d'un point (radians) : toujours visible, ne couvre jamais l'ecran. */
    private static final float MIN_ANGULAR = 0.003f;
    private static final float MAX_ANGULAR = 0.015f;
    /** Anneau sombre autour d'un point : rayon exterieur (en rayons du point) et couleur (noir d'ombre). */
    private static final float RING_OUTER = 1.5f;
    private static final int RING_COLOR = 0x90000000;
    /** Opacite du bord du disque d'un point. */
    private static final int DOT_RIM_ALPHA = 0xB0;
    /** Tolerance de visee d'un point : multiple de son rayon apparent. */
    private static final float GAZE_RADIUS_FACTOR = 3f;
    /** Tolerance de la piste deja regardee (evite le clignotement) et priorite a ecart egal. */
    private static final float GAZE_STICKY = 1.5f;
    private static final float GAZE_STICKY_BONUS = 0.6f;
    private static final HoloColor PLAYER_COLOR = HoloColor.selection(0xFF);
    private static final HoloColor HOSTILE_COLOR = HoloColor.critical(0xFF).darker(0.01f);
    private static final HoloColor OTHER_COLOR = HoloColor.action(0xFF);
    /** Demi-epaisseur des trainees, en blocs (a l'echelle par defaut). */
    private static final float TRAIL_THICKNESS = 0.006f;
    /** Opacite maximale d'une trainee (point le plus recent). */
    private static final float TRAIL_ALPHA = 0.8f;

    private static final float LABEL_DISTANCE = 24f;
    private static final float GAZE_DEGREES = 2.5f;
    private static final float GAZE_TAN = (float) Math.tan(Math.toRadians(GAZE_DEGREES));
    private static final float TEXT_SCALE = 0.009f;
    /** Agrandissement du nom du point regarde. */
    private static final float GAZED_TEXT = 1.2f;
    /** Ecart du texte d'etat au-dessus du pave (blocs a l'echelle par defaut). */
    private static final float NAME_GAP = 0.015f;
    /** Echelle du texte d'un nom : multiple du rayon du point (le nom suit la taille du point). */
    private static final float NAME_TEXT_RATIO = 0.4f;
    /** Hauteur du nom au-dessus du centre du point : multiple du rayon du point. */
    private static final float NAME_LIFT = 1.75f;
    /** Fond des noms (noir translucide, comme les noms d'entites). */
    private static final int NAME_BACKGROUND = 0x70000000;
    /** Les joueurs a moins de cette distance (blocs a l'echelle par defaut) montrent leur nom. */
    private static final float NEAR_NAMES = 8f;
    /** Noms de joueurs proches dessines au plus par frame. */
    private static final int MAX_NEAR_NAMES = 16;
    /** Distance minimale de dessin des points (au-dela du plan proche). */
    private static final float MIN_PRIORITY_DISTANCE = 0.12f;
    private static final HoloColor NAME_COLOR = HoloColor.special(0xFF);
    private static final HoloColor NEAR_NAME_COLOR = HoloColor.special(0xFF).darker(0.3f);
    private static final HoloColor STATUS_COLOR = HoloColor.primary(0xFF).lighter(0.4f);
    private static final String NO_ZONE = "Aucune zone";

    private static final int SEGMENTS = 16;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];
    private static final float[] AXES_SIGN = {-1f, 1f};
    /** Sommet transforme (fil de rendu seulement). */
    private static final Vector3f VERTEX = new Vector3f();
    /**
     * Position de l'oeil dans l'espace des sommets de la frame (fil de rendu) : ce n'est pas l'origine de la pile, car le
     * balancement de la marche, la secousse et la nausee sont dans la matrice de projection (voir {@link #computeEye}).
     */
    private static final Vector3f EYE = new Vector3f();
    private static final Matrix4f CLIP = new Matrix4f();
    private static final Matrix3f EYE_SYSTEM = new Matrix3f();

    // Couleurs du theme de la frame en cours (fil de rendu), posees par {@link #applyTheme}.
    private static int edgeColor;
    private static int playerColor;
    private static int hostileColor;
    private static int otherColor;
    private static int nameColor;
    private static int nearNameColor;
    private static int statusColor;
    /**
     * Distance maximale (repere de la vue) des sommets des points et des trainees : au-dela, le sommet est ramene vers
     * la camera le long de son rayon (meme position a l'ecran, profondeur plus proche : devant le terrain du pave).
     * {@link Float#MAX_VALUE} = aucun ecretage. Distance mesuree depuis {@link #EYE}.
     */
    private static float clamp = Float.MAX_VALUE;

    /** Resout les couleurs dans le theme de l'objet (debut de frame). */
    private static void applyTheme(HoloTheme theme)
    {
        edgeColor = EDGE_COLOR.resolve(theme);
        playerColor = PLAYER_COLOR.resolve(theme);
        hostileColor = HOSTILE_COLOR.resolve(theme);
        otherColor = OTHER_COLOR.resolve(theme);
        nameColor = NAME_COLOR.resolve(theme);
        nearNameColor = NEAR_NAME_COLOR.resolve(theme);
        statusColor = STATUS_COLOR.resolve(theme);
    }

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = 2.0 * Math.PI * i / SEGMENTS;
            COS[i] = (float) Math.cos(a);
            SIN[i] = (float) Math.sin(a);
        }
    }

    // ---------------------------------------------------------------- frame en cours (fil de rendu)

    private final Matrix4f terrain = new Matrix4f();
    private final Matrix4f tileMatrix = new Matrix4f();
    private final Matrix4f drawMatrix = new Matrix4f();
    /** Tuiles visibles de la frame et leurs cles de tri (distance, indice) ; agrandis au besoin, jamais par frame. */
    private AreaView.Tile[] visibleTiles = new AreaView.Tile[256];
    private long[] sortKeys = new long[256];
    /** Tableau de tri (meme taille, reutilise). */
    private AreaView.Tile[] sorted = new AreaView.Tile[256];
    private int visibleCount;
    /** Noms de joueurs proches a dessiner (piste, position locale) : remplis puis vides a chaque frame. */
    private final AreaView.Track[] nameTracks = new AreaView.Track[MAX_NEAR_NAMES];
    private final float[] namePositions = new float[3 * MAX_NEAR_NAMES];
    private float worldScale;
    private float tanX;
    private float tanY;
    private final Matrix4f billboard = new Matrix4f();
    private final Matrix4f textMatrix = new Matrix4f();
    private final Quaternionf facing = new Quaternionf();
    private final float[] point = new float[3];
    private final float[] previous = new float[3];
    private final float[] half = new float[3];
    private final float[] start = new float[3];
    private float size;
    private float invX;
    private float invY;
    private float invZ;
    /** Conversion voxels -> repere local : {@code local = voxel * unit - half}. */
    private float unit;
    private float hx;
    private float hy;
    private float hz;
    /** Texte d'avancement en cache (une chaine par valeur). */
    private int statusValue = -1;
    /** Piste regardee a la frame precedente. */
    private AreaView.Track lastGazed;
    private String statusText = "";

    @Override
    public void render(AreaDisplayerCube cube, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        HoloTheme theme = HoloThemes.resolve(cube);
        applyTheme(theme);
        AreaDisplayerModule module = cube.displayer();
        if (module == null) return;
        HoloTransform t = cube.transform();
        float scale = Math.max(1e-3f, (t.scaleX() + t.scaleY() + t.scaleZ()) / 3f);
        size = scale / AreaDisplayerCube.DEFAULT_SCALE;
        worldScale = scale;
        invX = 1f / Math.max(1e-3f, t.scaleX());
        invY = 1f / Math.max(1e-3f, t.scaleY());
        invZ = 1f / Math.max(1e-3f, t.scaleZ());
        hx = cube.halfX();
        hy = cube.halfY();
        hz = cube.halfZ();
        Matrix4f matrix = pose.last().pose();
        HoloEditMath.rotation(facing, t).conjugate()
                .mul(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        clamp = Float.MAX_VALUE;
        computeEye();

        if (module.flag(AreaDisplayerModule.FLAG_FRAME)) {
            edges(buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS), matrix, THICKNESS * size / scale,
                    packedLight);
        }

        AreaGrid grid = module.grid();
        AreaView view = AreaTerrainClient.view(module.markerId());
        boolean world = ctx.family() == HoloAnchor.Family.WORLD;
        if (grid == null || view == null || view.grid() == null || !view.grid().sameZone(grid)) {
            if (world) status(grid == null ? NO_ZONE : statusText(module), matrix, buffers, packedLight);
            return;
        }
        unit = 2f / grid.maxSize();
        terrain.set(matrix).translate(-hx, -hy, -hz).scale(unit);
        terrain(buffers, view, theme, grid, world, module.flag(AreaDisplayerModule.FLAG_CULLING),
                1f - module.transparency() / 100f);
        float front = world ? priorityLimit(matrix, t) : Float.MAX_VALUE;

        int now = AreaTerrainClient.ticks();
        float partial = ctx.partialTick();
        boolean players = module.flag(AreaDisplayerModule.FLAG_PLAYERS);
        boolean entities = module.flag(AreaDisplayerModule.FLAG_ENTITIES);
        boolean playerTrails = players && module.flag(AreaDisplayerModule.FLAG_PLAYER_TRAILS);
        boolean entityTrails = entities && module.flag(AreaDisplayerModule.FLAG_ENTITY_TRAILS);
        if (playerTrails || entityTrails) {
            VertexConsumer glow = buffers.getBuffer(Types.GLOW);
            float thickness = TRAIL_THICKNESS * size / scale;
            clamp = front;
            for (int i = 0; i < view.trackCount(); i++) {
                AreaView.Track track = view.track(i);
                boolean player = track.kind() == AreaTerrainPackets.KIND_PLAYER;
                if (player ? !playerTrails : !entityTrails) continue;
                trail(glow, matrix, track, now, partial, thickness, packedLight);
            }
            clamp = Float.MAX_VALUE;
        }
        if (!players && !entities) {
            if (world && module.progress() != AreaDisplayerModule.NO_SCAN) {
                status(statusText(module), matrix, buffers, packedLight);
            }
            return;
        }

        // Points, et recherche du point regarde.
        VertexConsumer solid = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        float labelDistance = LABEL_DISTANCE * Math.max(1f, size);
        AreaView.Track gazed = null;
        float bestTan = Float.MAX_VALUE;
        float gazedX = 0f;
        float gazedY = 0f;
        float gazedZ = 0f;
        int names = 0;
        float nearDistance = NEAR_NAMES * Math.max(1f, size);
        clamp = front;
        for (int i = 0; i < view.trackCount(); i++) {
            AreaView.Track track = view.track(i);
            boolean player = track.kind() == AreaTerrainPackets.KIND_PLAYER;
            if (player ? !players : !entities) continue;
            track.position(now, partial, point);
            float lx = point[0] * unit - hx;
            float ly = point[1] * unit - hy;
            float lz = point[2] * unit - hz;
            placeBillboard(matrix, lx, ly, lz);
            int color = color(track.kind());
            // Repere de la vue : oeil a EYE, regard selon -Z.
            float vx = billboard.m30() - EYE.x;
            float vy = billboard.m31() - EYE.y;
            float vz = billboard.m32() - EYE.z;
            float distance2 = vx * vx + vy * vy + vz * vz;
            float radius = dotRadius((float) Math.sqrt(distance2));
            dot(solid, billboard, radius, color, packedLight);
            if (!world || track.name() == null || vz >= 0f) continue;
            if (player && names < MAX_NEAR_NAMES && distance2 <= nearDistance * nearDistance) {
                nameTracks[names] = track;
                namePositions[3 * names] = lx;
                namePositions[3 * names + 1] = ly;
                namePositions[3 * names + 2] = lz;
                names++;
            }
            if (distance2 > labelDistance * labelDistance) continue;
            float off = (float) Math.sqrt(vx * vx + vy * vy) / -vz;
            boolean sticky = track == lastGazed;
            float limit = Math.max(GAZE_TAN, GAZE_RADIUS_FACTOR * radius / -vz) * (sticky ? GAZE_STICKY : 1f);
            float score = sticky ? off * GAZE_STICKY_BONUS : off;
            if (off <= limit && score < bestTan) {
                bestTan = score;
                gazed = track;
                gazedX = lx;
                gazedY = ly;
                gazedZ = lz;
            }
        }
        clamp = Float.MAX_VALUE;
        lastGazed = gazed;
        if (!world) return;
        for (int i = 0; i < names; i++) {
            AreaView.Track track = nameTracks[i];
            nameTracks[i] = null;
            if (track == gazed) continue;
            placeBillboard(matrix, namePositions[3 * i], namePositions[3 * i + 1], namePositions[3 * i + 2]);
            float r = dotRadius(billboard);
            label(track.name(), billboard, NAME_LIFT * r, NAME_TEXT_RATIO * r, nearNameColor, front, buffers,
                    packedLight);
        }
        if (gazed != null) {
            placeBillboard(matrix, gazedX, gazedY, gazedZ);
            float r = dotRadius(billboard);
            label(gazed.name(), billboard, NAME_LIFT * r, NAME_TEXT_RATIO * r * GAZED_TEXT, nameColor, front,
                    buffers, packedLight);
        }
        if (module.progress() != AreaDisplayerModule.NO_SCAN) {
            status(statusText(module), matrix, buffers, packedLight);
        }
    }

    private static int color(int kind)
    {
        return kind == AreaTerrainPackets.KIND_PLAYER ? playerColor
                : kind == AreaTerrainPackets.KIND_HOSTILE ? hostileColor : otherColor;
    }

    private String statusText(AreaDisplayerModule module)
    {
        int value = module.progress();
        if (value == AreaDisplayerModule.NO_SCAN) return "";
        if (value != statusValue) {
            statusValue = value;
            statusText = "Scan " + value + " %";
        }
        return statusText;
    }

    // ---------------------------------------------------------------- terrain

    /**
     * Terrain : envoie au GPU les tuiles dont le maillage a change (au plus {@value #UPLOADS_PER_FRAME} par frame),
     * retient celles dont la sphere englobante est dans le champ de la camera ({@code cull}, famille monde seulement :
     * le repere de la vue y a la camera a l'origine, regard selon -Z), triees par distance, puis les dessine. Les
     * sommets du lot immediat sont envoyes avant (ordre de dessin), l'etat de rendu est restaure en {@code finally}.
     * <ul>
     *   <li>{@code single} (option Culling) : <b>une seule couche</b>. Pour le solide puis pour le fluide, une
     *       pre-passe n'ecrit que la profondeur, puis la couleur n'est melangee que ou la profondeur est celle de la
     *       surface la plus proche : une face cachee par une autre n'est jamais visible, quel que soit l'ordre de
     *       dessin (tuiles les plus proches d'abord) ;</li>
     *   <li>sinon : effet translucide, solide puis fluide, tuiles de la plus lointaine a la plus proche.</li>
     * </ul>
     */
    private void terrain(MultiBufferSource buffers, AreaView view, HoloTheme theme, AreaGrid grid, boolean cull,
                         boolean single, float opacity)
    {
        int count = view.tileCount();
        if (count == 0) return;
        int uploads = UPLOADS_PER_FRAME;
        for (int i = 0; i < count && uploads > 0; i++) {
            AreaView.Tile tile = view.tile(i);
            if (!tile.needsUpload()) continue;
            AreaTileGpu.upload(tile);
            uploads--;
        }
        float secX = 1f;
        float secY = 1f;
        Minecraft minecraft = Minecraft.getInstance();
        if (cull) {
            tanY = (float) Math.tan(Math.toRadians(minecraft.options.fov().get()) * 0.5) * FOV_MARGIN;
            float aspect = (float) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
            tanX = tanY * Math.max(1f, aspect);
            secX = (float) Math.sqrt(1f + tanX * tanX);
            secY = (float) Math.sqrt(1f + tanY * tanY);
        }
        collect(view, grid, cull, secX, secY);
        if (visibleCount == 0) return;
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
        try {
            Matrix4f projection = RenderSystem.getProjectionMatrix();
            int solidRgb = SOLID.rgb(theme);
            int fluidRgb = FLUID.rgb(theme);
            for (int pass = 0; pass < 2; pass++) {
                boolean fluid = pass == 1;
                int rgb = fluid ? fluidRgb : solidRgb;
                float alpha = fluid ? opacity * FLUID_RATIO : opacity;
                if (alpha < MIN_OPACITY) continue;
                if (single) {
                    // Les plus proches d'abord (rejet precoce par la profondeur).
                    drawTiles(Types.TERRAIN_DEPTH, fluid, true, 0xFFFFFF, 1f, projection);
                    drawTiles(Types.TERRAIN_COLOR, fluid, true, rgb, alpha, projection);
                } else {
                    drawTiles(Types.TERRAIN, fluid, false, rgb, alpha, projection);
                }
            }
        } finally {
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            Arrays.fill(visibleTiles, 0, visibleCount, null);
        }
    }

    /**
     * Remplit {@link #visibleTiles} (tuiles qui ont des quads et sont dans le champ), triees de la plus proche a la
     * plus lointaine quand le repere de la vue est connu ({@code cull}).
     */
    private void collect(AreaView view, AreaGrid grid, boolean cull, float secX, float secY)
    {
        int count = view.tileCount();
        if (visibleTiles.length < count) {
            visibleTiles = new AreaView.Tile[count + count / 2];
            sortKeys = new long[visibleTiles.length];
            sorted = new AreaView.Tile[visibleTiles.length];
        }
        visibleCount = 0;
        for (int i = 0; i < count; i++) {
            AreaView.Tile tile = view.tile(i);
            if (!AreaTileGpu.has(tile, false) && !AreaTileGpu.has(tile, true)) continue;
            long key = visibleCount;
            if (cull) {
                if (!visible(tile, grid, secX, secY)) continue;
                // Centre de la tuile dans le repere de la vue : distance au carre, monotone en bits (positive).
                key |= (long) Float.floatToIntBits(VERTEX.lengthSquared()) << 32;
            }
            visibleTiles[visibleCount] = tile;
            sortKeys[visibleCount++] = key;
        }
        if (!cull) return;
        // Tri sans allocation : cle = distance au carre (bits) << 32 | rang dans visibleTiles.
        Arrays.sort(sortKeys, 0, visibleCount);
        for (int i = 0; i < visibleCount; i++) sorted[i] = visibleTiles[(int) sortKeys[i]];
        System.arraycopy(sorted, 0, visibleTiles, 0, visibleCount);
        Arrays.fill(sorted, 0, visibleCount, null);
    }

    /**
     * Une passe : {@code type} pose son etat, puis dessine les tuiles visibles (solide ou fluide) dans l'ordre de
     * {@link #visibleTiles} (proche -> lointain) ou a l'envers ({@code nearFirst} faux).
     */
    private void drawTiles(RenderType type, boolean fluid, boolean nearFirst, int rgb, float alpha,
                           Matrix4f projection)
    {
        type.setupRenderState();
        try {
            ShaderInstance shader = RenderSystem.getShader();
            RenderSystem.setShaderColor((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f,
                    alpha);
            for (int n = 0; n < visibleCount; n++) {
                AreaView.Tile tile = visibleTiles[nearFirst ? n : visibleCount - 1 - n];
                if (!AreaTileGpu.has(tile, fluid)) continue;
                tileMatrix.set(terrain).translate(tile.originX(), tile.originY(), tile.originZ());
                drawMatrix.set(RenderSystem.getModelViewMatrix()).mul(tileMatrix);
                AreaTileGpu.draw(tile, fluid, drawMatrix, projection, shader);
            }
        } finally {
            VertexBuffer.unbind();
            type.clearRenderState();
        }
    }

    /** Vrai si la sphere englobante de la tuile coupe le champ de la camera (repere de la vue). */
    private boolean visible(AreaView.Tile tile, AreaGrid grid, float secX, float secY)
    {
        int span = AreaMesher.TILE;
        float ex = Math.min(span, grid.sizeX() - tile.originX());
        float ey = Math.min(span, grid.sizeY() - tile.originY());
        float ez = Math.min(span, grid.sizeZ() - tile.originZ());
        terrain.transformPosition(tile.originX() + 0.5f * ex, tile.originY() + 0.5f * ey, tile.originZ() + 0.5f * ez,
                VERTEX);
        VERTEX.sub(EYE);
        float radius = 0.5f * (float) Math.sqrt(ex * ex + ey * ey + ez * ez) * unit * worldScale;
        float depth = -VERTEX.z;
        return depth >= -radius && Math.abs(VERTEX.x) <= depth * tanX + radius * secX
                && Math.abs(VERTEX.y) <= depth * tanY + radius * secY;
    }

    // ---------------------------------------------------------------- trainees

    /** Bandes croisees entre les points de la trainee encore recents, puis jusqu'a la position affichee. */
    private void trail(VertexConsumer consumer, Matrix4f matrix, AreaView.Track track, int now, float partial,
                       float thickness, int light)
    {
        int count = track.trailSize();
        int rgb = color(track.kind()) & 0xFFFFFF;
        boolean started = false;
        for (int i = 0; i <= count; i++) {
            int age;
            if (i < count) {
                age = now - track.trailPoint(i, point);
            } else {
                track.position(now, partial, point);
                age = 0;
            }
            if (age >= AreaView.TRAIL_TICKS) continue;
            float x = point[0] * unit - hx;
            float y = point[1] * unit - hy;
            float z = point[2] * unit - hz;
            if (started) {
                float alpha = TRAIL_ALPHA * (1f - (float) age / AreaView.TRAIL_TICKS);
                segment(consumer, matrix, previous[0], previous[1], previous[2], x, y, z, thickness,
                        Mth.clamp((int) (alpha * 255f), 0, 255) << 24 | rgb, light);
            }
            previous[0] = x;
            previous[1] = y;
            previous[2] = z;
            started = true;
        }
    }

    /** Segment de {@code a} a {@code b} en deux bandes perpendiculaires (sans allocation). */
    private static void segment(VertexConsumer consumer, Matrix4f matrix, float ax, float ay, float az, float bx,
                                float by, float bz, float t, int color, int light)
    {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-6f) return;
        // Premiere largeur : d x haut (ou d x X si d est vertical), seconde : d x premiere.
        float ux;
        float uy;
        float uz;
        if (Math.abs(dy) > 0.99f * length) {
            ux = 0f;
            uy = dz;
            uz = -dy;
        } else {
            ux = -dz;
            uy = 0f;
            uz = dx;
        }
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux *= t / ul;
        uy *= t / ul;
        uz *= t / ul;
        float vx = (dy * uz - dz * uy) / length;
        float vy = (dz * ux - dx * uz) / length;
        float vz = (dx * uy - dy * ux) / length;
        band(consumer, matrix, ax, ay, az, dx, dy, dz, ux, uy, uz, color, light);
        band(consumer, matrix, ax, ay, az, dx, dy, dz, vx, vy, vz, color, light);
    }

    // ---------------------------------------------------------------- cadre

    /** Douze aretes du pave en bandes croisees. */
    private void edges(VertexConsumer consumer, Matrix4f matrix, float t, int light)
    {
        float[] half = this.half;
        half[0] = hx;
        half[1] = hy;
        half[2] = hz;
        float[] start = this.start;
        for (int axis = 0; axis < 3; axis++) {
            int ua = (axis + 1) % 3;
            int va = (axis + 2) % 3;
            for (float su : AXES_SIGN) {
                for (float sv : AXES_SIGN) {
                    start[axis] = -half[axis];
                    start[ua] = su * half[ua];
                    start[va] = sv * half[va];
                    float dx = axis == 0 ? 2f * half[0] : 0f;
                    float dy = axis == 1 ? 2f * half[1] : 0f;
                    float dz = axis == 2 ? 2f * half[2] : 0f;
                    band(consumer, matrix, start[0], start[1], start[2], dx, dy, dz, ua == 0 ? t : 0f,
                            ua == 1 ? t : 0f, ua == 2 ? t : 0f, edgeColor, light);
                    band(consumer, matrix, start[0], start[1], start[2], dx, dy, dz, va == 0 ? t : 0f,
                            va == 1 ? t : 0f, va == 2 ? t : 0f, edgeColor, light);
                }
            }
        }
    }

    // ---------------------------------------------------------------- textes

    /** Texte d'etat au-dessus du pave (famille monde, a portee de lecture). */
    private void status(String text, Matrix4f matrix, MultiBufferSource buffers, int light)
    {
        if (text.isEmpty()) return;
        placeBillboard(matrix, 0f, hy, 0f);
        float distance = LABEL_DISTANCE * Math.max(1f, size);
        float x = billboard.m30() - EYE.x;
        float y = billboard.m31() - EYE.y;
        float z = billboard.m32() - EYE.z;
        if (x * x + y * y + z * z > distance * distance) return;
        label(text, billboard, NAME_GAP * size, TEXT_SCALE * size, statusColor, Float.MAX_VALUE, buffers, light);
    }

    /**
     * Texte centre au-dessus du billboard {@code m}, a {@code lift} blocs, sur un fond sombre, a l'echelle
     * {@code textScale} (blocs par unite de police). Si le billboard est plus loin que {@code limit} (repere de la vue),
     * toute la matrice est ramenee vers la camera (meme position a l'ecran, devant le terrain du pave).
     */
    private void label(String text, Matrix4f m, float lift, float textScale, int color, float limit,
                       MultiBufferSource buffers, int light)
    {
        Font font = Minecraft.getInstance().font;
        float dx = m.m30() - EYE.x;
        float dy = m.m31() - EYE.y;
        float dz = m.m32() - EYE.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > limit) {
            // Homothetie centree sur l'oeil : meme position a l'ecran, plus pres de la camera.
            textMatrix.translation(EYE).scale(limit / distance).translate(-EYE.x, -EYE.y, -EYE.z).mul(m);
        } else {
            textMatrix.set(m);
        }
        textMatrix.translate(0f, lift, 0f).scale(-textScale, -textScale, textScale);
        font.drawInBatch(text, -0.5f * font.width(text), -9f, color, false, textMatrix, buffers,
                Font.DisplayMode.NORMAL, NAME_BACKGROUND, light);
    }

    /**
     * Distance maximale (repere de la vue) des points et des trainees : juste avant la sphere englobante du pave, donc
     * devant tout son terrain, mais pas avant le plan proche. Les vrais blocs entre la camera et le pave les cachent
     * toujours.
     */
    private float priorityLimit(Matrix4f matrix, HoloTransform t)
    {
        float cx = matrix.m30() - EYE.x;
        float cy = matrix.m31() - EYE.y;
        float cz = matrix.m32() - EYE.z;
        float rx = hx * t.scaleX();
        float ry = hy * t.scaleY();
        float rz = hz * t.scaleZ();
        float radius = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        return Math.max(MIN_PRIORITY_DISTANCE,
                (float) Math.sqrt(cx * cx + cy * cy + cz * cz) - 1.02f * radius);
    }

    // ---------------------------------------------------------------- primitives

    /**
     * Position de l'oeil dans l'espace des sommets. En 1.20.1 le balancement de la marche, la secousse des degats et la
     * nausee sont multiplies dans la <b>matrice de projection</b> (apres notre pile) : l'oeil n'est donc pas l'origine de
     * l'espace des sommets. C'est le point dont la projection est {@code (x, y, w) = (0, 0, 0)} : on resout ce systeme
     * 3 x 3 avec {@code projection * modelView}. Projection non perspective : origine.
     */
    private static void computeEye()
    {
        CLIP.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
        // Lignes x, y et w de la matrice (le premier indice de JOML est la colonne).
        EYE_SYSTEM.set(CLIP.m00(), CLIP.m01(), CLIP.m03(), CLIP.m10(), CLIP.m11(), CLIP.m13(), CLIP.m20(), CLIP.m21(),
                CLIP.m23());
        if (Math.abs(EYE_SYSTEM.determinant()) < 1e-12f) {
            EYE.zero();
            return;
        }
        EYE.set(-CLIP.m30(), -CLIP.m31(), -CLIP.m33());
        EYE_SYSTEM.invert().transform(EYE);
        if (!Float.isFinite(EYE.x + EYE.y + EYE.z)) EYE.zero();
    }

    /** Rayon d'un point au billboard {@code m}. */
    private float dotRadius(Matrix4f m)
    {
        float dx = m.m30() - EYE.x;
        float dy = m.m31() - EYE.y;
        float dz = m.m32() - EYE.z;
        return dotRadius((float) Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /**
     * Rayon d'un point a {@code distance} blocs de l'oeil : grand de pres ({@link #DOT_MAX} sous {@link #DOT_NEAR}),
     * tres petit de loin ({@link #DOT_MIN} au-dela de {@link #DOT_FAR}), progressif entre les deux ; distances et rayons
     * a l'echelle du pave. Borne en apparence : jamais sous {@link #MIN_ANGULAR}, jamais au-dessus de
     * {@link #MAX_ANGULAR} (radians).
     */
    private float dotRadius(float distance)
    {
        float t = (DOT_FAR * size - distance) / ((DOT_FAR - DOT_NEAR) * size);
        t = t <= 0f ? 0f : t >= 1f ? 1f : t * t * (3f - 2f * t);
        float radius = size * (DOT_MIN + (DOT_MAX - DOT_MIN) * t);
        return Math.max(MIN_ANGULAR * distance, Math.min(MAX_ANGULAR * distance, radius));
    }

    private void placeBillboard(Matrix4f matrix, float x, float y, float z)
    {
        billboard.set(matrix).translate(x, y, z).scale(invX, invY, invZ).rotate(facing);
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

    /**
     * Point de rayon {@code r} dans le plan XY du billboard : disque net (centre plein, bord a peine plus
     * transparent) entoure d'un fin anneau sombre qui le detache du terrain ; les deux ne se chevauchent pas (aucun
     * scintillement avec le tri des quads translucides).
     */
    private static void dot(VertexConsumer consumer, Matrix4f m, float r, int color, int light)
    {
        int rim = DOT_RIM_ALPHA << 24 | color & 0xFFFFFF;
        for (int i = 0; i < SEGMENTS; i += 2) {
            vertex(consumer, m, 0f, 0f, 0f, color, light);
            vertex(consumer, m, r * COS[i], r * SIN[i], 0f, rim, light);
            vertex(consumer, m, r * COS[i + 1], r * SIN[i + 1], 0f, rim, light);
            vertex(consumer, m, r * COS[i + 2], r * SIN[i + 2], 0f, rim, light);
        }
        float outer = r * RING_OUTER;
        for (int i = 0; i < SEGMENTS; i++) {
            vertex(consumer, m, r * COS[i], r * SIN[i], 0f, RING_COLOR, light);
            vertex(consumer, m, outer * COS[i], outer * SIN[i], 0f, RING_COLOR, light);
            vertex(consumer, m, outer * COS[i + 1], outer * SIN[i + 1], 0f, RING_COLOR, light);
            vertex(consumer, m, r * COS[i + 1], r * SIN[i + 1], 0f, RING_COLOR, light);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, int color,
                               int light)
    {
        matrix.transformPosition(x, y, z, VERTEX);
        if (clamp != Float.MAX_VALUE) {
            float dx = VERTEX.x - EYE.x;
            float dy = VERTEX.y - EYE.y;
            float dz = VERTEX.z - EYE.z;
            float distance2 = dx * dx + dy * dy + dz * dz;
            if (distance2 > clamp * clamp) {
                // Homothetie centree sur l'oeil : meme pixel, profondeur plus proche.
                float k = clamp / (float) Math.sqrt(distance2);
                VERTEX.set(EYE.x + dx * k, EYE.y + dy * k, EYE.z + dz * k);
            }
        }
        consumer.vertex(VERTEX.x, VERTEX.y, VERTEX.z)
                .color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >>> 24).uv2(light).endVertex();
    }

    // ---------------------------------------------------------------- RenderType

    /**
     * {@code RenderType} locaux (sous-classe pour acceder aux etats proteges de {@code RenderStateShard} ; jamais
     * instanciee).
     */
    private static final class Types extends RenderType
    {
        /**
         * Terrain sans culling : translucide, faces arriere eliminees, profondeur ecrite (tuiles triees par distance
         * avant l'envoi, pas les quads d'une tuile).
         */
        static final RenderType TERRAIN = create(HoloLib.MODID + ":area_terrain_quads",
                DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_LIGHTMAP_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false));

        /** Terrain, pre-passe de l'option Culling : profondeur seule (couleur masquee, sans melange). */
        static final RenderType TERRAIN_DEPTH = create(HoloLib.MODID + ":area_terrain_depth",
                DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_LIGHTMAP_SHADER)
                        .setLightmapState(LIGHTMAP)
                        .setWriteMaskState(DEPTH_WRITE)
                        .createCompositeState(false));

        /**
         * Terrain, passe couleur de l'option Culling : melange, couleur seule, tiree un peu vers la camera pour passer
         * la ou la pre-passe a ecrit la profondeur de la meme surface.
         */
        static final RenderType TERRAIN_COLOR = create(HoloLib.MODID + ":area_terrain_color",
                DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_LIGHTMAP_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(LIGHTMAP)
                        .setLayeringState(POLYGON_OFFSET_LAYERING)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        /** Trainees : translucides, double face, sans ecriture de profondeur (elles ne cachent rien). */
        static final RenderType GLOW = create(HoloLib.MODID + ":area_glow_quads",
                DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_LIGHTMAP_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                      boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear)
        {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
