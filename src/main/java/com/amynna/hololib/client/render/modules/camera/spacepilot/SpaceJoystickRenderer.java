package com.amynna.hololib.client.render.modules.camera.spacepilot;

import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickAim;
import com.amynna.hololib.modules.camera.spacepilot.SpaceJoystickForward;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Joystick de pilotage de Space Pilot (migre du Space Controller en T19, geometrie inchangee sauf le rayon du cercle
 * avant, C48) : anneaux plats translucides dans le plan local {@code z = 0}, couleur principale du theme du joueur,
 * en quads ({@link HoloRenderTypes#TRANSLUCENT_QUADS}, sans culling). Aucune allocation par frame (cosinus
 * precalcules, vecteurs et matrice reutilises), aucun etat GL modifie.
 * <ul>
 *     <li>{@link Forward} : cercle avant (zone morte), plus clair quand l'ecart du regard la depasse (etat du pad lu
 *     par {@link SpaceJoystickForward#beyondDeadZone()}), et segment vers le centre de l'ecran en premiere
 *     personne ;</li>
 *     <li>{@link Aim} : cercle de visee, plus petit, au centre de l'ecran.</li>
 * </ul>
 * Affichage decide par {@code shown()} des objets (pilote local en capture) ; placement du cercle avant par le module.
 */
public final class SpaceJoystickRenderer
{
    private static final int SEGMENTS = 48;
    /**
     * Demi-epaisseur des traits, en blocs (a {@value SpaceJoystickForward#DISTANCE} blocs de l'oeil) : le bord
     * exterieur du cercle avant est a {@code RADIUS + THICKNESS}, d'ou part le lien vers le referentiel
     * ({@link SpaceReferenceLinkRenderer}, T33).
     */
    static final float THICKNESS = 0.0035f;
    /** Espace laisse entre le segment et chaque anneau, en blocs. */
    private static final float GAP = 0.01f;
    private static final HoloColor FORWARD_COLOR = HoloColor.primary(0xB0);
    /** Couleur principale eclaircie (moitie vers le blanc), opaque : une rotation est commandee. */
    private static final HoloColor FORWARD_ACTIVE_COLOR = HoloColor.primary(0xFF).lighter(0.5f);
    private static final HoloColor SEGMENT_COLOR = HoloColor.primary(0x80);
    private static final HoloColor SEGMENT_ACTIVE_COLOR = HoloColor.primary(0xC0).lighter(0.5f);
    private static final HoloColor AIM_COLOR = HoloColor.primary(0xD0);
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
            HoloTheme theme = HoloThemes.resolve(object);
            ring(consumer, matrix, SpaceJoystickForward.RADIUS,
                    (active ? FORWARD_ACTIVE_COLOR : FORWARD_COLOR).resolve(theme), packedLight);
            if (ctx.family() == HoloAnchor.Family.VIEW) {
                segment(consumer, matrix, (active ? SEGMENT_ACTIVE_COLOR : SEGMENT_COLOR).resolve(theme), packedLight);
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
                    AIM_COLOR.resolve(HoloThemes.resolve(object)), packedLight);
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
}
