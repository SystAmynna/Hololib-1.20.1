package com.amynna.hololib.client.render.modules.monde.spaceconfigurator;

import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.holo.edit.HoloEditMath;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.modules.dimension.space.math.SpaceOrbits;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceConfiguratorModule;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceOrbitHandle;
import com.amynna.hololib.modules.monde.spacemap.SpaceMapModule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Renderer de la {@link SpaceOrbitHandle} : l'ellipse de l'orbite du corps selectionne, parent au foyer
 * ({@code docs/space/04-mathematiques.md} par. 7). Le pont monde applique translation, rotation et echelle
 * {@code (a, a, b)} ; le renderer annule l'echelle sur sa matrice et trace en blocs, dans le repere tourne de la
 * poignee, la courbe {@code (a (cos t - e), 0, b sin t)} avec {@code a = scaleX}, {@code e} lu dans
 * {@code scaleZ / scaleX} (borne comme le fera le serveur) et {@code b = a sqrt(1 - e^2)} : pendant une edition a
 * l'echelle, l'ellipse montre deja l'orbite qui sera enregistree. Epaisseur constante en blocs.
 * <p>
 * Dessin : deux rubans croises (dans le plan de l'orbite et selon sa normale), un segment foyer -> periapse (sens de
 * l'orientation) et un petit octaedre au foyer. La poignee est posee au temps fige {@code t_edit} du Configurator
 * (T42) alors que la Space Map suit le temps vivant : si le parent bouge dans la vue de la Map (vue centree sur un autre
 * corps), l'ellipse est decalee de son deplacement affiche entre {@code t_edit} et le temps vivant pour rester autour
 * de lui (la poignee elle-meme, ses gizmos et sa visee, ne bougent pas) ; nul si le parent est au centre de la vue
 * (« Orbite ») ou immobile.
 * <p>
 * {@link HoloRenderTypes#TRANSLUCENT_QUADS}, aucune allocation, aucun etat GL modifie.
 */
public final class SpaceOrbitHandleRenderer implements HoloObjectRenderer<SpaceOrbitHandle>
{
    private static final int SEGMENTS = 96;
    /** Demi-epaisseur de l'ellipse, en blocs. */
    private static final float THICKNESS = 0.006f;
    /** Demi-epaisseur du segment foyer -> periapse, en blocs. */
    private static final float AXIS_THICKNESS = 0.003f;
    /** Rayon de l'octaedre du foyer, en blocs. */
    private static final float CORE = 0.015f;
    private static final int COLOR = 0xE0FFE040;
    private static final int AXIS_COLOR = 0xA0FFE040;
    private static final int CORE_COLOR = 0xFFFFF0A0;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];

    static
    {
        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = i * Mth.TWO_PI / SEGMENTS;
            COS[i] = Mth.cos(angle);
            SIN[i] = Mth.sin(angle);
        }
    }

    private final Matrix4f matrix = new Matrix4f();
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f offset = new Vector3f();
    private final Vector3d[] frozen = SpaceOrbits.newPositionArray();
    private final Vector3d[] live = SpaceOrbits.newPositionArray();
    /** Sommet transforme : {@code VertexConsumer.vertex(Matrix4f, ...)} alloue un vecteur par sommet. */
    private final Vector3f transformed = new Vector3f();

    @Override
    public void render(SpaceOrbitHandle handle, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        HoloTransform t = handle.transform();
        float sx = Math.max(1e-6f, t.scaleX());
        float sy = Math.max(1e-6f, t.scaleY());
        float sz = Math.max(1e-6f, t.scaleZ());
        float e = (float) SpaceOrbitHandle.eccentricity(t);
        float a = sx;
        float b = a * Mth.sqrt(1f - e * e);
        // Repere tourne de la poignee, en blocs (echelle annulee).
        matrix.set(pose.last().pose()).scale(1f / sx, 1f / sy, 1f / sz);
        animationOffset(handle, t, ctx.partialTick());
        float ox = offset.x;
        float oy = offset.y;
        float oz = offset.z;

        VertexConsumer consumer = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        for (int i = 0; i < SEGMENTS; i++) {
            float ax = ox + a * (COS[i] - e);
            float az = oz + b * SIN[i];
            float bx = ox + a * (COS[i + 1] - e);
            float bz = oz + b * SIN[i + 1];
            // Normale exterieure de l'ellipse (dans le plan) : (b cos t, 0, a sin t) normalisee.
            float nax = b * COS[i];
            float naz = a * SIN[i];
            float la = THICKNESS / Math.max(1e-9f, Mth.sqrt(nax * nax + naz * naz));
            float nbx = b * COS[i + 1];
            float nbz = a * SIN[i + 1];
            float lb = THICKNESS / Math.max(1e-9f, Mth.sqrt(nbx * nbx + nbz * nbz));
            vertex(consumer, ax - nax * la, oy, az - naz * la, COLOR, packedLight);
            vertex(consumer, bx - nbx * lb, oy, bz - nbz * lb, COLOR, packedLight);
            vertex(consumer, bx + nbx * lb, oy, bz + nbz * lb, COLOR, packedLight);
            vertex(consumer, ax + nax * la, oy, az + naz * la, COLOR, packedLight);
            vertex(consumer, ax, oy - THICKNESS, az, COLOR, packedLight);
            vertex(consumer, bx, oy - THICKNESS, bz, COLOR, packedLight);
            vertex(consumer, bx, oy + THICKNESS, bz, COLOR, packedLight);
            vertex(consumer, ax, oy + THICKNESS, az, COLOR, packedLight);
        }
        // Segment foyer -> periapse (+X), deux bandes croisees.
        float periapsis = a * (1f - e);
        vertex(consumer, ox, oy - AXIS_THICKNESS, oz, AXIS_COLOR, packedLight);
        vertex(consumer, ox + periapsis, oy - AXIS_THICKNESS, oz, AXIS_COLOR, packedLight);
        vertex(consumer, ox + periapsis, oy + AXIS_THICKNESS, oz, AXIS_COLOR, packedLight);
        vertex(consumer, ox, oy + AXIS_THICKNESS, oz, AXIS_COLOR, packedLight);
        vertex(consumer, ox, oy, oz - AXIS_THICKNESS, AXIS_COLOR, packedLight);
        vertex(consumer, ox + periapsis, oy, oz - AXIS_THICKNESS, AXIS_COLOR, packedLight);
        vertex(consumer, ox + periapsis, oy, oz + AXIS_THICKNESS, AXIS_COLOR, packedLight);
        vertex(consumer, ox, oy, oz + AXIS_THICKNESS, AXIS_COLOR, packedLight);
        octahedron(consumer, ox, oy, oz, packedLight);
    }

    /**
     * {@link #offset} : deplacement affiche du parent entre {@code t_edit} et le temps vivant de la Space Map, dans le
     * repere tourne de la poignee (blocs), dans la vue courante de la Map (positions relatives a son centre au meme
     * temps) ; nul sans Map, sans corps a orbite selectionne, ou pour un parent au centre de la vue.
     */
    private void animationOffset(SpaceOrbitHandle handle, HoloTransform t, float partialTick)
    {
        offset.set(0f, 0f, 0f);
        SpaceConfiguratorModule module = handle.configurator();
        SpaceMapModule map = module != null ? module.map() : null;
        SpaceSystem system = map != null && map.cube() != null ? map.shownSystem() : null;
        SpaceBody body = system != null && map.selectedBody() != 0 ? system.body(map.selectedBody()) : null;
        if (body == null || body.orbit() == null) return;
        int parent = system.parentIndex(system.indexOf(body.id()));
        if (parent < 0 || parent == map.centerIndex()) return;
        map.bodyPoints(module.editTime(), frozen);
        map.bodyPoints(map.viewDays(partialTick), live);
        // Positions en unites du cube (demi-arete comprise) : echelle du cube seulement.
        HoloTransform cube = map.cube().transform();
        offset.set((float) (live[parent].x - frozen[parent].x) * cube.scaleX(),
                (float) (live[parent].y - frozen[parent].y) * cube.scaleY(),
                (float) (live[parent].z - frozen[parent].z) * cube.scaleZ());
        // Repere du cube -> monde -> repere tourne de la poignee.
        HoloEditMath.rotation(rotation, cube).transform(offset);
        HoloEditMath.rotation(rotation, t).conjugate().transform(offset);
    }

    /** Huit faces triangulaires (quads au dernier sommet double) autour de {@code (x, y, z)}. */
    private void octahedron(VertexConsumer consumer, float x, float y, float z, int light)
    {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    vertex(consumer, x + sx * CORE, y, z, CORE_COLOR, light);
                    vertex(consumer, x, y + sy * CORE, z, CORE_COLOR, light);
                    vertex(consumer, x, y, z + sz * CORE, CORE_COLOR, light);
                    vertex(consumer, x, y, z + sz * CORE, CORE_COLOR, light);
                }
            }
        }
    }

    private void vertex(VertexConsumer consumer, float x, float y, float z, int color, int light)
    {
        matrix.transformPosition(x, y, z, transformed);
        consumer.vertex(transformed.x, transformed.y, transformed.z)
                .color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >>> 24).uv2(light).endVertex();
    }
}
