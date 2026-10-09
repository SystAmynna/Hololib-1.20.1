package com.amynna.hololib.client.render.modules.monde.spaceconfigurator;

import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceConfiguratorModule;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceMapHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Renderer de la {@link SpaceMapHandle} : petit marqueur pose sur l'etoile selectionnee, dans le repere local de la
 * poignee (translation appliquee par le pont monde ; rotation et echelle neutres, l'outil ne permet que la
 * translation). Trois bras croises le long des axes (deux bandes perpendiculaires chacun), qui depassent l'anneau de
 * selection dessine par le cube, et un petit octaedre au centre.
 * <p>
 * Couleur de la selection du cube (jaune) ; orange si le systeme est hors du cadrage (la poignee est alors bornee au
 * bord du cube, « Recadrer » le ramene). {@link HoloRenderTypes#TRANSLUCENT_QUADS}, aucune allocation, aucun etat GL
 * modifie.
 */
public final class SpaceHandleRenderer implements HoloObjectRenderer<SpaceMapHandle>
{
    /** Demi-longueur des bras, en blocs. */
    private static final float ARM = SpaceMapHandle.RADIUS;
    /** Demi-epaisseur des bras, en blocs. */
    private static final float THICKNESS = 0.005f;
    /** Rayon de l'octaedre central, en blocs. */
    private static final float CORE = 0.018f;
    private static final int COLOR = 0xE0FFE040;
    private static final int CORE_COLOR = 0xFFFFF0A0;
    private static final int OUTSIDE_COLOR = 0xE0FF8040;
    private static final int OUTSIDE_CORE_COLOR = 0xFFFFC090;
    /** Axes unitaires X, Y, Z. */
    private static final float[][] AXES = {{1f, 0f, 0f}, {0f, 1f, 0f}, {0f, 0f, 1f}};
    /** Sommet transforme (fil de rendu seulement) : {@code VertexConsumer.vertex(Matrix4f, ...)} alloue un vecteur par sommet. */
    private static final Vector3f VERTEX = new Vector3f();

    @Override
    public void render(SpaceMapHandle handle, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        SpaceConfiguratorModule module = handle.configurator();
        boolean outside = module != null && module.selectedOutsideFrame();
        int color = outside ? OUTSIDE_COLOR : COLOR;
        int core = outside ? OUTSIDE_CORE_COLOR : CORE_COLOR;
        Matrix4f matrix = pose.last().pose();
        VertexConsumer consumer = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        for (int i = 0; i < 3; i++) {
            float[] a = AXES[i];
            float[] u = AXES[(i + 1) % 3];
            float[] v = AXES[(i + 2) % 3];
            float x = -ARM * a[0];
            float y = -ARM * a[1];
            float z = -ARM * a[2];
            float dx = 2f * ARM * a[0];
            float dy = 2f * ARM * a[1];
            float dz = 2f * ARM * a[2];
            band(consumer, matrix, x, y, z, dx, dy, dz, THICKNESS * u[0], THICKNESS * u[1], THICKNESS * u[2], color,
                    packedLight);
            band(consumer, matrix, x, y, z, dx, dy, dz, THICKNESS * v[0], THICKNESS * v[1], THICKNESS * v[2], color,
                    packedLight);
        }
        octahedron(consumer, matrix, core, packedLight);
    }

    /** Huit faces triangulaires (quads au dernier sommet double). */
    private static void octahedron(VertexConsumer consumer, Matrix4f matrix, int color, int light)
    {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    vertex(consumer, matrix, sx * CORE, 0f, 0f, color, light);
                    vertex(consumer, matrix, 0f, sy * CORE, 0f, color, light);
                    vertex(consumer, matrix, 0f, 0f, sz * CORE, color, light);
                    vertex(consumer, matrix, 0f, 0f, sz * CORE, color, light);
                }
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

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, int color,
                               int light)
    {
        matrix.transformPosition(x, y, z, VERTEX);
        consumer.vertex(VERTEX.x, VERTEX.y, VERTEX.z)
                .color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >>> 24).uv2(light).endVertex();
    }
}
