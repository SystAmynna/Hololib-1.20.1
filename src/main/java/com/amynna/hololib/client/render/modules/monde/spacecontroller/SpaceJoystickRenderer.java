package com.amynna.hololib.client.render.modules.monde.spacecontroller;

import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.monde.spacecontroller.SpaceJoystickAim;
import com.amynna.hololib.modules.monde.spacecontroller.SpaceJoystickForward;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Mini-interface du joystick du Space Controller : anneaux plats translucides dans le plan local {@code z = 0},
 * couleur d'accent du mod ({@link ConfigWindow#ACCENT}), en quads ({@link HoloRenderTypes#TRANSLUCENT_QUADS}, sans
 * culling). Aucune allocation par frame (cosinus precalcules, vecteurs et matrice reutilises), aucun etat GL modifie.
 * <ul>
 *     <li>{@link Forward} : cercle avant, plus clair quand l'ecart du regard depasse la zone morte (etat du pad lu par
 *     {@link SpaceJoystickForward#beyondDeadZone()}), et segment vers le centre de l'ecran en premiere personne ;</li>
 *     <li>{@link Aim} : cercle de visee, plus petit, au centre de l'ecran.</li>
 * </ul>
 */
public final class SpaceJoystickRenderer
{
    private static final int SEGMENTS = 48;
    /** Demi-epaisseur des traits, en blocs (a {@value SpaceJoystickForward#DISTANCE} blocs de l'oeil). */
    private static final float THICKNESS = 0.0035f;
    /** Espace laisse entre le segment et chaque anneau, en blocs. */
    private static final float GAP = 0.01f;
    private static final int ACCENT_RGB = ConfigWindow.ACCENT & 0xFFFFFF;
    private static final int FORWARD_COLOR = 0xB0000000 | ACCENT_RGB;
    /** Accent eclairci (moitie vers le blanc), opaque : une rotation est commandee. */
    private static final int FORWARD_ACTIVE_COLOR = 0xFF000000 | lighten(ACCENT_RGB);
    private static final int SEGMENT_COLOR = 0x80000000 | ACCENT_RGB;
    private static final int SEGMENT_ACTIVE_COLOR = 0xC0000000 | lighten(ACCENT_RGB);
    private static final int AIM_COLOR = 0xD0000000 | ACCENT_RGB;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];
    /** Sommet transforme (fil de rendu seulement) : {@code VertexConsumer.vertex(Matrix4f, ...)} alloue un vecteur par sommet. */
    private static final Vector3f VERTEX = new Vector3f();

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = i * Mth.TWO_PI / SEGMENTS;
            COS[i] = Mth.cos(angle);
            SIN[i] = Mth.sin(angle);
        }
    }

    private SpaceJoystickRenderer() {}

    /**
     * Cercle avant ({@code Local} relatif sur l'axe avant du monde) et, si l'objet est dessine dans la vue
     * ({@code ctx.family() == VIEW}, premiere personne), segment vers le centre de l'ecran. En F5 l'objet est dessine
     * dans le monde autour de l'oeil (routage) : anneau seul.
     * <p>
     * Segment ({@code docs/space/04-mathematiques.md} par. 6) : a la passe vue, la pile part de l'identite dans
     * l'espace vue, donc {@code pose.last().pose()} envoie le repere local du cercle dans l'espace vue ; son inverse
     * envoie le centre de l'ecran a la distance du cercle, {@code (0, 0, -DISTANCE)}, dans le repere local, ou le
     * segment est trace depuis l'origine (bord de l'anneau avant) jusqu'a ce point (bord de l'anneau de visee). Sa
     * largeur est perpendiculaire au segment et a la direction de l'oeil (bande vue de face).
     */
    static final class Forward implements HoloObjectRenderer<SpaceJoystickForward>
    {
        private final Matrix4f inverse = new Matrix4f();
        private final Vector3f eye = new Vector3f();
        private final Vector3f target = new Vector3f();
        private final Vector3f direction = new Vector3f();
        private final Vector3f middle = new Vector3f();
        private final Vector3f side = new Vector3f();

        @Override
        public void render(SpaceJoystickForward object, PoseStack pose, MultiBufferSource buffers, int packedLight,
                           HoloDrawContext ctx)
        {
            VertexConsumer consumer = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
            Matrix4f matrix = pose.last().pose();
            boolean active = object.beyondDeadZone();
            ring(consumer, matrix, SpaceJoystickForward.RADIUS, active ? FORWARD_ACTIVE_COLOR : FORWARD_COLOR,
                    packedLight);
            if (ctx.family() == HoloAnchor.Family.VIEW) {
                segment(consumer, matrix, active ? SEGMENT_ACTIVE_COLOR : SEGMENT_COLOR, packedLight);
            }
        }

        private void segment(VertexConsumer consumer, Matrix4f matrix, int color, int light)
        {
            if (Math.abs(matrix.determinant()) < 1e-12f) return;
            matrix.invert(inverse);
            inverse.transformPosition(0f, 0f, -SpaceJoystickForward.DISTANCE, target);
            inverse.transformPosition(0f, 0f, 0f, eye);
            float length = target.length();
            float from = SpaceJoystickForward.RADIUS + GAP;
            float to = length - SpaceJoystickAim.RADIUS - GAP;
            if (to <= from) return;
            direction.set(target).div(length);
            direction.mul(0.5f * (from + to), middle);
            eye.sub(middle);
            direction.cross(eye, side);
            float sideLength = side.length();
            if (sideLength < 1e-6f) return;
            side.mul(THICKNESS / sideLength);
            float x0 = direction.x * from;
            float y0 = direction.y * from;
            float z0 = direction.z * from;
            float x1 = direction.x * to;
            float y1 = direction.y * to;
            float z1 = direction.z * to;
            vertex(consumer, matrix, x0 - side.x, y0 - side.y, z0 - side.z, color, light);
            vertex(consumer, matrix, x1 - side.x, y1 - side.y, z1 - side.z, color, light);
            vertex(consumer, matrix, x1 + side.x, y1 + side.y, z1 + side.z, color, light);
            vertex(consumer, matrix, x0 + side.x, y0 + side.y, z0 + side.z, color, light);
        }
    }

    /** Cercle de visee ({@code Local} follow au centre de l'ecran). */
    static final class Aim implements HoloObjectRenderer<SpaceJoystickAim>
    {
        @Override
        public void render(SpaceJoystickAim object, PoseStack pose, MultiBufferSource buffers, int packedLight,
                           HoloDrawContext ctx)
        {
            ring(buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS), pose.last().pose(), SpaceJoystickAim.RADIUS,
                    AIM_COLOR, packedLight);
        }
    }

    /** Anneau plat de rayon {@code radius} dans le plan {@code z = 0}, centre a l'origine. */
    private static void ring(VertexConsumer consumer, Matrix4f matrix, float radius, int color, int light)
    {
        float inner = radius - THICKNESS;
        float outer = radius + THICKNESS;
        for (int i = 0; i < SEGMENTS; i++) {
            vertex(consumer, matrix, COS[i] * inner, SIN[i] * inner, 0f, color, light);
            vertex(consumer, matrix, COS[i + 1] * inner, SIN[i + 1] * inner, 0f, color, light);
            vertex(consumer, matrix, COS[i + 1] * outer, SIN[i + 1] * outer, 0f, color, light);
            vertex(consumer, matrix, COS[i] * outer, SIN[i] * outer, 0f, color, light);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, int color,
                               int light)
    {
        matrix.transformPosition(x, y, z, VERTEX);
        consumer.vertex(VERTEX.x, VERTEX.y, VERTEX.z)
                .color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >>> 24).uv2(light).endVertex();
    }

    /** Couleur RGB rapprochee de moitie du blanc. */
    private static int lighten(int rgb)
    {
        int r = (rgb >> 16 & 0xFF) + 0xFF >> 1;
        int g = (rgb >> 8 & 0xFF) + 0xFF >> 1;
        int b = (rgb & 0xFF) + 0xFF >> 1;
        return r << 16 | g << 8 | b;
    }
}
