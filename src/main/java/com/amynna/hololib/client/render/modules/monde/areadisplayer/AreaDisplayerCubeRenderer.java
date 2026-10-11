package com.amynna.hololib.client.render.modules.monde.areadisplayer;

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
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerCube;
import com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerModule;
import com.amynna.hololib.modules.monde.areadisplayer.AreaGrid;
import com.amynna.hololib.modules.monde.areadisplayer.terrain.AreaTerrainPackets;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renderer de l'{@link AreaDisplayerCube} : dans le repere local du pave (pile placee par le pont monde), d'apres le
 * module (options, proportions) et la vue client du terrain ({@link AreaTerrainClient#view}).
 * <ul>
 *   <li><b>Cadre</b> (option) : douze aretes en bandes fines croisees, couleur d'accent ;</li>
 *   <li><b>terrain</b> : quads du maillage glouton des sections ({@link AreaMesher}), <b>monochrome cyan</b>
 *       ({@link ConfigWindow#ACCENT}) ombre par direction (dessus clair, dessous sombre), translucide ; fluides plus
 *       transparents et plus bleus. {@code RenderType} local {@link Types#TERRAIN} : faces arriere eliminees,
 *       profondeur ecrite, sans tri (le maillage est enorme ; seules les faces exterieures sont vues) ;</li>
 *   <li><b>points</b> : disque face camera par position suivie (interpolee), joueurs verts, entites hostiles rouges,
 *       autres jaunes, selon les options du module ;</li>
 *   <li><b>trainees</b> (options) : bandes croisees du chemin des 5 dernieres secondes, en fondu, sans ecriture de
 *       profondeur ({@link Types#GLOW}) ;</li>
 *   <li><b>nom au regard</b> (famille {@code WORLD}, a moins de {@value #LABEL_DISTANCE} blocs fois la taille) : le
 *       point le plus proche de l'axe du regard, dans un cone de {@value #GAZE_DEGREES} degres (ou de son rayon
 *       apparent si plus grand), montre son nom au-dessus de lui ; « Aucune zone » / « Scan n % » au-dessus du
 *       pave.</li>
 * </ul>
 * Le pave est dessine pour tous ; tailles en blocs a l'echelle par defaut, multipliees par la taille relative du pave.
 * Aucune allocation par frame dans ce code (le {@code Font} vanilla cree son petit objet par texte) ; aucun etat GL
 * modifie.
 */
public final class AreaDisplayerCubeRenderer implements HoloObjectRenderer<AreaDisplayerCube>
{
    /** Demi-epaisseur des aretes, en blocs (a l'echelle par defaut). */
    private static final float THICKNESS = 0.01f;
    private static final int EDGE_COLOR = 0x90000000 | ConfigWindow.ACCENT & 0xFFFFFF;
    /** Couleurs du terrain par code {@code direction + 6 * type} (voir {@link AreaMesher}). */
    private static final int[] TERRAIN_COLORS = new int[12];
    /** Ombrage par direction : +X, -X, +Y, -Y, +Z, -Z. */
    private static final float[] SHADE = {0.8f, 0.8f, 1f, 0.5f, 0.65f, 0.65f};
    private static final int SOLID_ALPHA = 0xB0;
    private static final int FLUID_ALPHA = 0x50;
    private static final int FLUID_RGB = 0x3070FF;

    /** Rayon d'un point, en blocs (a l'echelle par defaut). */
    private static final float DOT_RADIUS = 0.035f;
    private static final int PLAYER_COLOR = 0xFF50F070;
    private static final int HOSTILE_COLOR = 0xFFFF5040;
    private static final int OTHER_COLOR = 0xFFFFD040;
    /** Demi-epaisseur des trainees, en blocs (a l'echelle par defaut). */
    private static final float TRAIL_THICKNESS = 0.006f;
    /** Opacite maximale d'une trainee (point le plus recent). */
    private static final float TRAIL_ALPHA = 0.8f;

    private static final float LABEL_DISTANCE = 24f;
    private static final float GAZE_DEGREES = 1.5f;
    private static final float GAZE_TAN = (float) Math.tan(Math.toRadians(GAZE_DEGREES));
    private static final float TEXT_SCALE = 0.0125f;
    private static final float NAME_GAP = 0.015f;
    private static final int NAME_COLOR = 0xFFFFFFFF;
    private static final int STATUS_COLOR = 0xFF80E0FF;
    private static final String NO_ZONE = "Aucune zone";

    private static final int SEGMENTS = 16;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];
    private static final float[] AXES_SIGN = {-1f, 1f};
    /** Sommet transforme (fil de rendu seulement). */
    private static final Vector3f VERTEX = new Vector3f();

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = 2.0 * Math.PI * i / SEGMENTS;
            COS[i] = (float) Math.cos(a);
            SIN[i] = (float) Math.sin(a);
        }
        for (int code = 0; code < 12; code++) {
            int direction = code % 6;
            boolean fluid = code >= 6;
            int rgb = fluid ? FLUID_RGB : ConfigWindow.ACCENT & 0xFFFFFF;
            float shade = SHADE[direction];
            int r = (int) ((rgb >> 16 & 0xFF) * shade);
            int g = (int) ((rgb >> 8 & 0xFF) * shade);
            int b = (int) ((rgb & 0xFF) * shade);
            TERRAIN_COLORS[code] = (fluid ? FLUID_ALPHA : SOLID_ALPHA) << 24 | r << 16 | g << 8 | b;
        }
    }

    // ---------------------------------------------------------------- frame en cours (fil de rendu)

    private final Matrix4f terrain = new Matrix4f();
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
    private String statusText = "";

    @Override
    public void render(AreaDisplayerCube cube, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        AreaDisplayerModule module = cube.displayer();
        if (module == null) return;
        HoloTransform t = cube.transform();
        float scale = Math.max(1e-3f, (t.scaleX() + t.scaleY() + t.scaleZ()) / 3f);
        size = scale / AreaDisplayerCube.DEFAULT_SCALE;
        invX = 1f / Math.max(1e-3f, t.scaleX());
        invY = 1f / Math.max(1e-3f, t.scaleY());
        invZ = 1f / Math.max(1e-3f, t.scaleZ());
        hx = cube.halfX();
        hy = cube.halfY();
        hz = cube.halfZ();
        Matrix4f matrix = pose.last().pose();
        HoloEditMath.rotation(facing, t).conjugate()
                .mul(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());

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
        terrain(buffers.getBuffer(Types.TERRAIN), view, packedLight);

        int now = AreaTerrainClient.ticks();
        float partial = ctx.partialTick();
        boolean players = module.flag(AreaDisplayerModule.FLAG_PLAYERS);
        boolean entities = module.flag(AreaDisplayerModule.FLAG_ENTITIES);
        boolean playerTrails = players && module.flag(AreaDisplayerModule.FLAG_PLAYER_TRAILS);
        boolean entityTrails = entities && module.flag(AreaDisplayerModule.FLAG_ENTITY_TRAILS);
        if (playerTrails || entityTrails) {
            VertexConsumer glow = buffers.getBuffer(Types.GLOW);
            float thickness = TRAIL_THICKNESS * size / scale;
            for (int i = 0; i < view.trackCount(); i++) {
                AreaView.Track track = view.track(i);
                boolean player = track.kind() == AreaTerrainPackets.KIND_PLAYER;
                if (player ? !playerTrails : !entityTrails) continue;
                trail(glow, matrix, track, now, partial, thickness, packedLight);
            }
        }
        if (!players && !entities) {
            if (world && module.progress() != AreaDisplayerModule.NO_SCAN) {
                status(statusText(module), matrix, buffers, packedLight);
            }
            return;
        }

        // Points, et recherche du point regarde.
        VertexConsumer solid = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        float radius = DOT_RADIUS * size;
        float labelDistance = LABEL_DISTANCE * Math.max(1f, size);
        AreaView.Track gazed = null;
        float bestTan = Float.MAX_VALUE;
        float gazedX = 0f;
        float gazedY = 0f;
        float gazedZ = 0f;
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
            disc(solid, billboard, radius, color, 0x60000000 | color & 0xFFFFFF, packedLight);
            if (!world || track.name() == null) continue;
            // Repere de la vue : camera a l'origine, regard selon -Z.
            float vx = billboard.m30();
            float vy = billboard.m31();
            float vz = billboard.m32();
            if (vz >= 0f || vx * vx + vy * vy + vz * vz > labelDistance * labelDistance) continue;
            float off = (float) Math.sqrt(vx * vx + vy * vy) / -vz;
            float limit = Math.max(GAZE_TAN, 1.5f * radius / -vz);
            if (off <= limit && off < bestTan) {
                bestTan = off;
                gazed = track;
                gazedX = lx;
                gazedY = ly;
                gazedZ = lz;
            }
        }
        if (!world) return;
        if (gazed != null) {
            placeBillboard(matrix, gazedX, gazedY, gazedZ);
            label(gazed.name(), billboard, radius + NAME_GAP * size, NAME_COLOR, buffers, packedLight);
        }
        if (module.progress() != AreaDisplayerModule.NO_SCAN) {
            status(statusText(module), matrix, buffers, packedLight);
        }
    }

    private static int color(int kind)
    {
        return kind == AreaTerrainPackets.KIND_PLAYER ? PLAYER_COLOR
                : kind == AreaTerrainPackets.KIND_HOSTILE ? HOSTILE_COLOR : OTHER_COLOR;
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

    private void terrain(VertexConsumer consumer, AreaView view, int light)
    {
        for (float[] mesh : view.meshes()) {
            if (mesh == null) continue;
            for (int at = 0; at < mesh.length; at += AreaMesher.FLOATS_PER_QUAD) {
                int color = TERRAIN_COLORS[(int) mesh[at + 12]];
                for (int i = 0; i < 4; i++) {
                    vertex(consumer, terrain, mesh[at + 3 * i], mesh[at + 3 * i + 1], mesh[at + 3 * i + 2], color,
                            light);
                }
            }
        }
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
                            ua == 1 ? t : 0f, ua == 2 ? t : 0f, EDGE_COLOR, light);
                    band(consumer, matrix, start[0], start[1], start[2], dx, dy, dz, va == 0 ? t : 0f,
                            va == 1 ? t : 0f, va == 2 ? t : 0f, EDGE_COLOR, light);
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
        float x = billboard.m30();
        float y = billboard.m31();
        float z = billboard.m32();
        if (x * x + y * y + z * z > distance * distance) return;
        label(text, billboard, NAME_GAP * size, STATUS_COLOR, buffers, light);
    }

    /** Texte centre au-dessus du billboard {@code m}, a {@code lift} blocs. */
    private void label(String text, Matrix4f m, float lift, int color, MultiBufferSource buffers, int light)
    {
        Font font = Minecraft.getInstance().font;
        float textScale = TEXT_SCALE * size;
        textMatrix.set(m).translate(0f, lift, 0f).scale(-textScale, -textScale, textScale);
        font.drawInBatch(text, -0.5f * font.width(text), -8f, color, false, textMatrix, buffers,
                Font.DisplayMode.NORMAL, 0, light);
    }

    // ---------------------------------------------------------------- primitives

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

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, int color,
                               int light)
    {
        matrix.transformPosition(x, y, z, VERTEX);
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
         * Terrain : translucide, faces arriere eliminees, profondeur ecrite, sans tri a l'envoi (des dizaines de
         * milliers de quads ; ordre du maillage).
         */
        static final RenderType TERRAIN = create(HoloLib.MODID + ":area_terrain_quads",
                DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_LIGHTMAP_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(LIGHTMAP)
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
