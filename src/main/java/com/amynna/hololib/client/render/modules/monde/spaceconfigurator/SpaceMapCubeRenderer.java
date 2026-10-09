package com.amynna.hololib.client.render.modules.monde.spaceconfigurator;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.holo.edit.HoloEditMath;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.math.SpaceUnits;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceLimits;
import com.amynna.hololib.modules.dimension.space.model.SpaceOrbit;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem.SystemHeader;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceConfiguratorModule;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceMapCube;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceSystemScale;
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
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renderer du {@link SpaceMapCube} : dessine tout le contenu du cube dans son repere local {@code [-1, 1]^3} (pile
 * placee par le pont monde : translation, orientation et echelle du transform deja appliquees).
 * <p>
 * <b>Toujours</b> : douze aretes en bandes fines croisees (deux quads perpendiculaires par arete, couleur d'accent
 * attenuee) et six faces tres legerement teintees pour lire le volume.
 * <p>
 * <b>Mode galaxie</b> ({@code docs/space/04-mathematiques.md} par. 7) :
 * <ul>
 *   <li>graduations : pas {@code nice(H / 4)} al sur trois aretes qui partent du coin haut-gauche-avant
 *       {@code (-1, 1, 1)} (la fenetre est devant la face {@code +Z}, sa base cache le bas du cube) : arete du haut
 *       le long de {@code +X} (traits vers le haut), arete verticale avant gauche le long de {@code +Y} (traits vers
 *       la gauche, valeur comptee depuis le bas), arete du haut a gauche le long de {@code -Z} (traits vers la
 *       gauche). Chaque regle compte la distance depuis son debut ; etiquettes « 5 al » (zero omis) ;</li>
 *   <li>etoiles : billboard face camera, taille constante en blocs (echelle du cube compensee), rayon et couleur
 *       selon la luminosite et la couleur de l'etoile principale (coeur plein + halo degrade) ; systeme courant de
 *       l'Environment entoure d'un anneau blanc, systeme selectionne en surbrillance (anneau jaune, halo renforce,
 *       nom jaune) ; un systeme hors du cadrage n'est pas dessine (« Recadrer » le ramene) ;</li>
 *   <li>noms et etiquettes : texte billboard ({@code font.drawInBatch}) : orientation et echelle de l'objet
 *       compensees, puis rotation de la camera, puis echelle {@code (-s, -s, s)} comme les etiquettes de nom vanilla
 *       ; seulement en famille {@code WORLD} et a moins de {@value #LABEL_DISTANCE} blocs.</li>
 * </ul>
 * <b>Mode systeme</b> ({@link #renderSystem}, doc 04 par. 7, echelle de {@code SpaceSystemScale}) :
 * <ul>
 *   <li>graduations en UA (memes regles que la galaxie, demi-etendue {@code H_sys}) ;</li>
 *   <li>orbites : ellipses en rubans croises (un ruban dans le plan de l'orbite, un perpendiculaire), couleur du corps
 *       attenuee, autour de la position affichee du parent ; l'orbite du corps selectionne est dessinee par sa poignee
 *       ({@code SpaceOrbitHandleRenderer}) ;</li>
 *   <li>corps : billboards a leurs positions de Kepler au temps d'affichage du module ({@code t_edit}, ou le temps
 *       anime), rayon affiche borne en unites du cube (il suit l'echelle du cube), etoiles avec halo, selection
 *       entouree d'un anneau jaune, noms comme en mode galaxie.</li>
 * </ul>
 * <p>
 * <b>Ordre et profondeur</b> : aretes, graduations, coeurs et anneaux des etoiles dans
 * {@link HoloRenderTypes#TRANSLUCENT_QUADS} (ecrit la profondeur), puis le texte, puis halos et faces dans un
 * {@code RenderType} translucide <b>sans ecriture de profondeur</b> ({@link GlowType#GLOW_QUADS}) : une face ou un
 * halo ne cache jamais une etoile ni une etiquette, quel que soit le tri des quads (une grande face translucide qui
 * ecrit la profondeur masquait les etoiles proches d'elle en vue oblique). Changer de {@code RenderType} envoie le lot
 * precedent : les faces sont donc toujours dessinees apres le contenu.
 * <p>
 * Le renderer lit la copie synchronisee du module ({@code cube.configurator()}) et ne fait que la mise en page. Tout ce
 * qui ne change qu'a la synchronisation (positions locales, rayons, couleurs, largeurs des noms, graduations et leurs
 * chaines) est mis en cache par module ({@link Layout}), reconstruit quand la liste d'en-tetes ou le cadrage change.
 * Aucune allocation par frame dans ce code (le {@code Font} vanilla cree son petit objet de sortie a chaque texte,
 * comme pour les etiquettes de nom), aucun etat GL modifie.
 */
public final class SpaceMapCubeRenderer implements HoloObjectRenderer<SpaceMapCube>
{
    // ---------------------------------------------------------------- cube

    /** Demi-epaisseur des aretes et des traits, en blocs. */
    private static final float THICKNESS = 0.01f;
    private static final int EDGE_COLOR = 0x90000000 | ConfigWindow.ACCENT & 0xFFFFFF;
    /** Teinte des faces (alpha faible). */
    private static final int FACE_COLOR = 0x14000000 | ConfigWindow.ACCENT & 0xFFFFFF;
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
    private static final int TICK_COLOR = 0xC0000000 | ConfigWindow.ACCENT & 0xFFFFFF;
    private static final int TICK_LABEL_COLOR = 0xFF000000 | ConfigWindow.ACCENT & 0xFFFFFF;

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
    /** Demi-epaisseur des anneaux, en blocs. */
    private static final float RING_THICKNESS = 0.004f;
    private static final int CURRENT_RING_COLOR = 0xE0FFFFFF;
    private static final int HIGHLIGHT = 0xFFFFE040;
    /** Tolerance du test « dans le cube ». */
    private static final float INSIDE_EPSILON = 1e-4f;
    /** Segments des disques et anneaux (pair : le disque est dessine en quads de deux secteurs). */
    private static final int SEGMENTS = 24;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];
    /** Sommet transforme (fil de rendu seulement) : {@code VertexConsumer.vertex(Matrix4f, ...)} alloue un vecteur par sommet. */
    private static final Vector3f VERTEX = new Vector3f();

    static
    {
        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = i * Mth.TWO_PI / SEGMENTS;
            COS[i] = Mth.cos(angle);
            SIN[i] = Mth.sin(angle);
        }
    }

    // ---------------------------------------------------------------- texte

    /** Echelle du texte, en blocs par pixel de police (etiquettes de nom vanilla : 0,025). */
    private static final float TEXT_SCALE = 0.0125f;
    /** Echelle des etiquettes des graduations. */
    private static final float TICK_TEXT_SCALE = 0.01f;
    /** Distance maximale de lecture des textes, en blocs. */
    private static final float LABEL_DISTANCE = 24f;
    private static final float LABEL_DISTANCE_SQR = LABEL_DISTANCE * LABEL_DISTANCE;
    /** Ecart entre le haut du coeur d'une etoile et le bas de son nom, en blocs. */
    private static final float NAME_GAP = 0.015f;
    private static final int NAME_COLOR = 0xFFFFFFFF;

    // ---------------------------------------------------------------- etat de dessin (fil de rendu)

    // ---------------------------------------------------------------- mode systeme

    /** Segments d'une orbite dessinee. */
    private static final int ORBIT_SEGMENTS = 64;
    /** Demi-epaisseur des orbites, en blocs. */
    private static final float ORBIT_THICKNESS = 0.004f;
    private static final int ORBIT_ALPHA = 0x90;
    private static final String UNIT_LIGHT_YEAR = " al";
    private static final String UNIT_AU = " UA";

    /** Mise en page par module (cle faible : un module oublie libere son cache). */
    private final Map<SpaceConfiguratorModule, Layout> layouts = new WeakHashMap<>();
    /** Positions affichees des corps (unites du cube), tableau reutilise. */
    private final Vector3d[] positions = SpaceOrbits.newPositionArray();
    private final Vector3d work = new Vector3d();
    /** Matrice d'un billboard (unite = bloc, plan XY face a la camera). */
    private final Matrix4f billboard = new Matrix4f();
    /** Matrice d'un texte. */
    private final Matrix4f textMatrix = new Matrix4f();
    /** Rotation qui ramene le repere de l'objet a celui de la camera : {@code R^-1 . C}. */
    private final Quaternionf facing = new Quaternionf();

    @Override
    public void render(SpaceMapCube cube, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        HoloTransform t = cube.transform();
        float scale = Math.max(1e-3f, (t.scaleX() + t.scaleY() + t.scaleZ()) / 3f);
        Matrix4f matrix = pose.last().pose();
        edges(buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS), matrix, THICKNESS / scale, packedLight);
        SpaceConfiguratorModule module = cube.configurator();
        if (module != null) {
            if (module.mode() == SpaceConfiguratorModule.Mode.SYSTEM) {
                SpaceSystem system = module.editedSystem();
                if (system != null) renderSystem(cube, module, system, pose, buffers, packedLight, ctx);
            } else if (module.frameHalf() > 0.0) {
                renderGalaxy(cube, module, matrix, buffers, packedLight, ctx);
            }
        }
        faces(buffers.getBuffer(GlowType.GLOW_QUADS), matrix, packedLight);
    }

    // ---------------------------------------------------------------- mode galaxie

    private void renderGalaxy(SpaceMapCube cube, SpaceConfiguratorModule module, Matrix4f matrix,
                              MultiBufferSource buffers, int light, HoloDrawContext ctx)
    {
        Layout layout = layout(module);
        layout.ensureGraduations(module.frameHalf(), UNIT_LIGHT_YEAR, Minecraft.getInstance().font);
        HoloTransform t = cube.transform();
        float scale = Math.max(1e-3f, (t.scaleX() + t.scaleY() + t.scaleZ()) / 3f);
        float invX = 1f / Math.max(1e-3f, t.scaleX());
        float invY = 1f / Math.max(1e-3f, t.scaleY());
        float invZ = 1f / Math.max(1e-3f, t.scaleZ());
        HoloEditMath.rotation(facing, t).conjugate()
                .mul(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        int selected = module.selectedSystem();
        int current = module.currentSystemId();

        // Lot avec profondeur : graduations, coeurs et anneaux.
        VertexConsumer solid = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        ticks(solid, matrix, layout, THICKNESS / scale, TICK_LENGTH / scale, light);
        for (int i = 0; i < layout.count; i++) {
            if (!layout.inside[i]) continue;
            placeBillboard(matrix, layout.qx[i], layout.qy[i], layout.qz[i], invX, invY, invZ);
            float r = layout.radius[i];
            disc(solid, billboard, r, layout.core[i], layout.color[i], light);
            int id = layout.ids[i];
            if (id == current) ring(solid, billboard, Math.max(RING_MIN, r * CURRENT_RING_FACTOR),
                    CURRENT_RING_COLOR, light);
            if (id == selected) ring(solid, billboard, Math.max(RING_MIN + 0.02f, r * SELECTED_RING_FACTOR),
                    HIGHLIGHT, light);
        }

        // Textes : famille monde seulement, a portee de lecture.
        if (ctx.family() == HoloAnchor.Family.WORLD) {
            Font font = Minecraft.getInstance().font;
            for (int i = 0; i < layout.count; i++) {
                if (!layout.inside[i]) continue;
                placeBillboard(matrix, layout.qx[i], layout.qy[i], layout.qz[i], invX, invY, invZ);
                if (!readable(billboard)) continue;
                textMatrix.set(billboard).translate(0f, layout.radius[i] + NAME_GAP, 0f)
                        .scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
                font.drawInBatch(layout.names[i], -0.5f * layout.widths[i], -8f,
                        layout.ids[i] == selected ? HIGHLIGHT : NAME_COLOR, false, textMatrix, buffers,
                        Font.DisplayMode.NORMAL, 0, light);
            }
            tickLabels(font, matrix, layout, buffers, scale, invX, invY, invZ, light);
        }

        // Lot sans profondeur : halos.
        VertexConsumer glow = buffers.getBuffer(GlowType.GLOW_QUADS);
        for (int i = 0; i < layout.count; i++) {
            if (!layout.inside[i]) continue;
            placeBillboard(matrix, layout.qx[i], layout.qy[i], layout.qz[i], invX, invY, invZ);
            halo(glow, billboard, layout.radius[i], layout.color[i],
                    layout.ids[i] == selected ? SELECTED_HALO_ALPHA : HALO_ALPHA, light);
        }
    }

    /**
     * Mode systeme : dessin du systeme edite ({@code module.editedSystem()}, copie complete) dans le repere du cube,
     * au temps d'affichage du module. Appele entre les aretes (deja dans {@link HoloRenderTypes#TRANSLUCENT_QUADS}) et
     * les faces ({@link GlowType#GLOW_QUADS}) ; la pile n'est pas modifiee.
     */
    private void renderSystem(SpaceMapCube cube, SpaceConfiguratorModule module, SpaceSystem system,
                              PoseStack pose, MultiBufferSource buffers, int light, HoloDrawContext ctx)
    {
        Font font = Minecraft.getInstance().font;
        double half = module.systemHalf();
        double magnifier = module.moonMagnifier();
        Layout layout = layoutOf(module);
        layout.ensureBodies(system, half, magnifier, font);
        layout.ensureGraduations(half, UNIT_AU, font);
        SpaceSystemScale.positions(system, module.displayDays(ctx.partialTick()), half, magnifier, positions, work);

        Matrix4f matrix = pose.last().pose();
        HoloTransform t = cube.transform();
        float scale = Math.max(1e-3f, (t.scaleX() + t.scaleY() + t.scaleZ()) / 3f);
        float invX = 1f / Math.max(1e-3f, t.scaleX());
        float invY = 1f / Math.max(1e-3f, t.scaleY());
        float invZ = 1f / Math.max(1e-3f, t.scaleZ());
        HoloEditMath.rotation(facing, t).conjugate()
                .mul(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        int selected = module.selectedBody();
        // L'orbite du corps selectionne est dessinee par sa poignee.
        int skipped = module.orbitHandle() != null ? selected : 0;

        // Lot avec profondeur : graduations, orbites, corps et anneau de selection.
        VertexConsumer solid = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        ticks(solid, matrix, layout, THICKNESS / scale, TICK_LENGTH / scale, light);
        float width = ORBIT_THICKNESS / scale;
        for (int i = 0; i < layout.bodyCount; i++) {
            if (layout.parent[i] < 0 || layout.bodyIds[i] == skipped) continue;
            Vector3d p = positions[layout.parent[i]];
            orbit(solid, matrix, layout, i, (float) p.x, (float) p.y, (float) p.z, width, light);
        }
        for (int i = 0; i < layout.bodyCount; i++) {
            Vector3d p = positions[i];
            placeBillboard(matrix, (float) p.x, (float) p.y, (float) p.z, invX, invY, invZ);
            float r = layout.bodyRadius[i] * scale;
            disc(solid, billboard, r, layout.bodyCore[i], layout.bodyColor[i], light);
            if (layout.bodyIds[i] == selected) ring(solid, billboard, r + 0.02f, HIGHLIGHT, light);
        }

        // Textes : famille monde seulement, a portee de lecture.
        if (ctx.family() == HoloAnchor.Family.WORLD) {
            for (int i = 0; i < layout.bodyCount; i++) {
                Vector3d p = positions[i];
                placeBillboard(matrix, (float) p.x, (float) p.y, (float) p.z, invX, invY, invZ);
                if (!readable(billboard)) continue;
                textMatrix.set(billboard).translate(0f, layout.bodyRadius[i] * scale + NAME_GAP, 0f)
                        .scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
                font.drawInBatch(layout.bodyNames[i], -0.5f * layout.bodyWidths[i], -8f,
                        layout.bodyIds[i] == selected ? HIGHLIGHT : NAME_COLOR, false, textMatrix, buffers,
                        Font.DisplayMode.NORMAL, 0, light);
            }
            tickLabels(font, matrix, layout, buffers, scale, invX, invY, invZ, light);
        }

        // Lot sans profondeur : halos des etoiles.
        VertexConsumer glow = buffers.getBuffer(GlowType.GLOW_QUADS);
        for (int i = 0; i < layout.bodyCount; i++) {
            if (!layout.star[i]) continue;
            Vector3d p = positions[i];
            placeBillboard(matrix, (float) p.x, (float) p.y, (float) p.z, invX, invY, invZ);
            halo(glow, billboard, layout.bodyRadius[i] * scale, layout.bodyColor[i],
                    layout.bodyIds[i] == selected ? SELECTED_HALO_ALPHA : HALO_ALPHA, light);
        }
    }

    /**
     * Orbite du corps {@code i} autour du point {@code (px, py, pz)} (parent affiche, unites du cube) : deux rubans
     * continus de demi-largeur {@code w}, l'un dans le plan de l'orbite, l'autre selon sa normale.
     */
    private static void orbit(VertexConsumer consumer, Matrix4f matrix, Layout layout, int i, float px, float py,
                              float pz, float w, int light)
    {
        int color = layout.orbitColor[i];
        float[] points = layout.orbitPoints;
        float[] sides = layout.orbitSides;
        int base = i * (ORBIT_SEGMENTS + 1) * 3;
        float nx = layout.orbitNormals[i * 3] * w;
        float ny = layout.orbitNormals[i * 3 + 1] * w;
        float nz = layout.orbitNormals[i * 3 + 2] * w;
        for (int k = 0; k < ORBIT_SEGMENTS; k++) {
            int a = base + k * 3;
            int b = a + 3;
            float ax = px + points[a];
            float ay = py + points[a + 1];
            float az = pz + points[a + 2];
            float bx = px + points[b];
            float by = py + points[b + 1];
            float bz = pz + points[b + 2];
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
     * {@link #billboard} : repere de bloc face a la camera, centre au point local {@code q} du cube. Chaine
     * {@code M . T(q) . S^-1 . R^-1 . C} : l'echelle et l'orientation de l'objet sont annulees, puis la rotation de la
     * camera appliquee (comme les etiquettes de nom vanilla), d'ou une taille constante en blocs.
     */
    private void placeBillboard(Matrix4f matrix, float qx, float qy, float qz, float invX, float invY, float invZ)
    {
        billboard.set(matrix).translate(qx, qy, qz).scale(invX, invY, invZ).rotate(facing);
    }

    /** Vrai si le centre du billboard courant est a moins de {@value #LABEL_DISTANCE} blocs de la camera. */
    private static boolean readable(Matrix4f m)
    {
        // Pile du monde : translation = position relative a la camera (apres sa rotation, distance conservee).
        return m.m30() * m.m30() + m.m31() * m.m31() + m.m32() * m.m32() <= LABEL_DISTANCE_SQR;
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
                band(consumer, matrix, x, y, z, ox, oy, oz, t * dir[0], t * dir[1], t * dir[2], TICK_COLOR, light);
                band(consumer, matrix, x, y, z, ox, oy, oz, t * side[0], t * side[1], t * side[2], TICK_COLOR,
                        light);
            }
        }
    }

    /** Etiquettes des graduations (zero omis), au-dela du bout des traits. */
    private void tickLabels(Font font, Matrix4f matrix, Layout layout, MultiBufferSource buffers, float scale,
                            float invX, float invY, float invZ, int light)
    {
        float h = SpaceMapCube.HALF_SIZE;
        float offset = (TICK_LENGTH + TICK_LABEL_GAP) / scale;
        for (int r = 0; r < 3; r++) {
            float[] start = RULER_START[r];
            float[] dir = RULER_DIR[r];
            float[] out = RULER_OUT[r];
            for (int k = 1; k <= layout.ticks; k++) {
                float s = layout.tickPos[k] * h;
                placeBillboard(matrix, start[0] * h + dir[0] * s + out[0] * offset,
                        start[1] * h + dir[1] * s + out[1] * offset, start[2] * h + dir[2] * s + out[2] * offset,
                        invX, invY, invZ);
                if (!readable(billboard)) continue;
                textMatrix.set(billboard).scale(-TICK_TEXT_SCALE, -TICK_TEXT_SCALE, TICK_TEXT_SCALE);
                font.drawInBatch(layout.tickLabels[k], -0.5f * layout.tickWidths[k], -4f, TICK_LABEL_COLOR, false,
                        textMatrix, buffers, Font.DisplayMode.NORMAL, 0, light);
            }
        }
    }

    // ---------------------------------------------------------------- mise en page (cache)

    /** Cache du module (cree au premier dessin). */
    private Layout layoutOf(SpaceConfiguratorModule module)
    {
        Layout layout = layouts.get(module);
        if (layout == null) {
            layout = new Layout();
            layouts.put(module, layout);
        }
        return layout;
    }

    /** Cache du mode galaxie, reconstruit si la liste d'en-tetes (remplacee a chaque synchro) ou le cadrage change. */
    private Layout layout(SpaceConfiguratorModule module)
    {
        Layout layout = layoutOf(module);
        List<SystemHeader> headers = module.headers();
        double cx = module.frameX();
        double cy = module.frameY();
        double cz = module.frameZ();
        double half = module.frameHalf();
        if (headers != layout.headers || cx != layout.cx || cy != layout.cy || cz != layout.cz
                || half != layout.half) {
            layout.stars(headers, cx, cy, cz, half, Minecraft.getInstance().font);
        }
        return layout;
    }

    /** Mise en page d'un module : tout ce qui ne change qu'a la synchronisation. */
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
        /** Rayon du coeur, en blocs. */
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
            double step = SpaceConfiguratorModule.nice(half / 4.0);
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

        // ------------------------------------------------------------ mode systeme

        private SpaceSystem system;
        private double systemHalf = Double.NaN;
        private double systemMagnifier = Double.NaN;
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
        /** Points des orbites relatifs au parent (unites du cube, loupe comprise), {@code SEGMENTS + 1} par corps. */
        private final float[] orbitPoints = new float[SpaceLimits.MAX_BODIES * (ORBIT_SEGMENTS + 1) * 3];
        /** Direction unitaire dans le plan de l'orbite, perpendiculaire a la courbe, en chaque point. */
        private final float[] orbitSides = new float[SpaceLimits.MAX_BODIES * (ORBIT_SEGMENTS + 1) * 3];
        /** Normale unitaire du plan de chaque orbite. */
        private final float[] orbitNormals = new float[SpaceLimits.MAX_BODIES * 3];
        private final Vector3d point = new Vector3d();
        private final Vector3d normal = new Vector3d();
        private final Quaterniond orientation = new Quaterniond();

        /**
         * Corps du systeme edite (rayons affiches, couleurs, noms) et courbes des orbites relativement au parent,
         * recalcules seulement quand la copie (nouvelle instance a chaque synchro), l'echelle ou la loupe change.
         */
        void ensureBodies(SpaceSystem edited, double half, double magnifier, Font font)
        {
            if (edited == system && half == systemHalf && magnifier == systemMagnifier) return;
            system = edited;
            systemHalf = half;
            systemMagnifier = magnifier;
            List<SpaceBody> bodies = edited.bodies();
            bodyCount = Math.min(bodies.size(), SpaceLimits.MAX_BODIES);
            for (int i = 0; i < bodyCount; i++) {
                SpaceBody body = bodies.get(i);
                bodyIds[i] = body.id();
                SpaceOrbit orbit = body.orbit();
                parent[i] = orbit != null ? edited.parentIndex(i) : -1;
                star[i] = body.kind() == SpaceBodyKind.STAR;
                bodyRadius[i] = (float) SpaceSystemScale.displayRadius(body.radius());
                bodyColor[i] = body.color() | 0xFF000000;
                bodyCore[i] = mixWhite(bodyColor[i], star[i] ? CORE_WHITE : 0.25f);
                orbitColor[i] = ORBIT_ALPHA << 24 | body.color() & 0xFFFFFF;
                bodyNames[i] = body.name();
                bodyWidths[i] = font.width(bodyNames[i]);
                if (parent[i] >= 0) curve(i, orbit, SpaceSystemScale.factor(body, magnifier) * SpaceMapCube.HALF_SIZE
                        / (SpaceUnits.AU_KM * half));
            }
            for (int i = bodyCount; i < bodyNames.length; i++) bodyNames[i] = null;
        }

        /** Points de l'orbite (anomalie excentrique reguliere), normale du plan et directions laterales. */
        private void curve(int i, SpaceOrbit orbit, double unit)
        {
            int base = i * (ORBIT_SEGMENTS + 1) * 3;
            for (int k = 0; k <= ORBIT_SEGMENTS; k++) {
                SpaceOrbits.orbitPoint(orbit, k * SpaceUnits.TWO_PI / ORBIT_SEGMENTS, point);
                orbitPoints[base + k * 3] = (float) (point.x * unit);
                orbitPoints[base + k * 3 + 1] = (float) (point.y * unit);
                orbitPoints[base + k * 3 + 2] = (float) (point.z * unit);
            }
            orientation.rotationYXZ(-Math.toRadians(orbit.yaw()), Math.toRadians(orbit.pitch()),
                    Math.toRadians(orbit.roll()));
            orientation.transform(normal.set(0.0, 1.0, 0.0));
            orbitNormals[i * 3] = (float) normal.x;
            orbitNormals[i * 3 + 1] = (float) normal.y;
            orbitNormals[i * 3 + 2] = (float) normal.z;
            for (int k = 0; k <= ORBIT_SEGMENTS; k++) {
                // Tangente par difference centree (courbe fermee : le point SEGMENTS est le point 0).
                int next = base + ((k + 1) % ORBIT_SEGMENTS) * 3;
                int previous = base + ((k + ORBIT_SEGMENTS - 1) % ORBIT_SEGMENTS) * 3;
                double tx = orbitPoints[next] - orbitPoints[previous];
                double ty = orbitPoints[next + 1] - orbitPoints[previous + 1];
                double tz = orbitPoints[next + 2] - orbitPoints[previous + 2];
                // Laterale = tangente x normale, unitaire.
                point.set(ty * normal.z - tz * normal.y, tz * normal.x - tx * normal.z, tx * normal.y - ty * normal.x);
                double length = point.length();
                if (length > 0.0) point.div(length);
                int at = base + k * 3;
                orbitSides[at] = (float) point.x;
                orbitSides[at + 1] = (float) point.y;
                orbitSides[at + 2] = (float) point.z;
            }
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
                band(consumer, matrix, x, y, z, dx, dy, dz, t * u[0], t * u[1], t * u[2], EDGE_COLOR, light);
                band(consumer, matrix, x, y, z, dx, dy, dz, t * v[0], t * v[1], t * v[2], EDGE_COLOR, light);
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
                        FACE_COLOR, light);
                vertex(consumer, matrix, cx + h * (u[0] - v[0]), cy + h * (u[1] - v[1]), cz + h * (u[2] - v[2]),
                        FACE_COLOR, light);
                vertex(consumer, matrix, cx + h * (u[0] + v[0]), cy + h * (u[1] + v[1]), cz + h * (u[2] + v[2]),
                        FACE_COLOR, light);
                vertex(consumer, matrix, cx + h * (-u[0] + v[0]), cy + h * (-u[1] + v[1]), cz + h * (-u[2] + v[2]),
                        FACE_COLOR, light);
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

    /** Anneau fin de rayon {@code r} dans le plan XY du billboard. */
    private static void ring(VertexConsumer consumer, Matrix4f m, float r, int color, int light)
    {
        float in = r - RING_THICKNESS;
        float out = r + RING_THICKNESS;
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
