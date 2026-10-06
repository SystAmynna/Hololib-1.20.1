package com.amynna.hololib.client.render.modules.dimension.planet1;

import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.modules.dimension.planet1.Planet1Planet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Dessine une {@link Planet1Planet} : chaque face du cube est une grille de quads opaques dont la couleur, calculee
 * par sommet, suit des bandes de geante gazeuse (horizontales sur les faces laterales, en carres concentriques sur les
 * poles) deformees par une turbulence animee, avec une grande tache. Chaque face est ombree selon une lumiere fixe
 * dans les axes du ciel, puis une coque translucide un peu plus grande donne une brume atmospherique.
 */
final class Planet1PlanetRenderer implements HoloObjectRenderer<Planet1Planet>
{
    /** Subdivisions par cote de face. */
    private static final int GRID = 20;
    private static final int ROW = GRID + 1;

    /** Faces : normale, axe U, axe V, avec {@code U x V = normale} (sens direct vu de l'exterieur). */
    private static final float[][] FACES = {
            {0, 1, 0, 0, 0, 1, 1, 0, 0},
            {0, -1, 0, 1, 0, 0, 0, 0, 1},
            {1, 0, 0, 0, 1, 0, 0, 0, 1},
            {-1, 0, 0, 0, 0, 1, 0, 1, 0},
            {0, 0, 1, 1, 0, 0, 0, 1, 0},
            {0, 0, -1, 0, 1, 0, 1, 0, 0},
    };

    /** Bandes, du pole sud au pole nord, reparties sur la latitude {@code [-LAT_RANGE, LAT_RANGE]}. */
    private static final int[] PALETTE = {
            0x4E3B2E, 0x7A5C44, 0xC9A27A, 0xE8D3B0, 0xB8743E, 0xEAD9BC, 0xD9B48A,
            0xF1E2C6, 0xA65F33, 0xE3C79F, 0x8E6A4E, 0xC2A07E, 0x5A4636,
    };
    /** Latitude des poles : 1 a l'arete, jusqu'a {@code 1 + POLAR_STRETCH} au centre des faces haut/bas. */
    private static final float POLAR_STRETCH = 0.6f;
    private static final float LAT_RANGE = 1f + POLAR_STRETCH;

    /** Grande tache : longitude, latitude, demi-largeurs (radians, latitude) et couleur. */
    private static final float SPOT_THETA = 0f;
    private static final float SPOT_LAT = -0.32f;
    private static final float SPOT_WIDTH = 0.38f;
    private static final float SPOT_HEIGHT = 0.11f;
    private static final int SPOT_COLOR = 0xB5502E;

    /** Lumiere fixe (axes du ciel, normalisee a l'initialisation) et part de lumiere ambiante. */
    private static final float LIGHT_X;
    private static final float LIGHT_Y;
    private static final float LIGHT_Z;
    private static final float AMBIENT = 0.35f;

    /** Brume : echelle de la coque et couleur {@code 0xAARRGGBB}. */
    private static final float HAZE_SCALE = 1.03f;
    private static final int HAZE_COLOR = 0x30F4E6D0;

    static
    {
        float x = 0.55f, y = 0.65f, z = -0.52f;
        float length = Mth.sqrt(x * x + y * y + z * z);
        LIGHT_X = x / length;
        LIGHT_Y = y / length;
        LIGHT_Z = z / length;
    }

    private final Quaternionf rotation = new Quaternionf();
    private final Quaternionf spinRotation = new Quaternionf();
    private final Vector3f normal = new Vector3f();
    /** Couleurs d'une face, par sommet de la grille (reutilise). */
    private final int[] colors = new int[ROW * ROW];

    @Override
    public void render(Planet1Planet planet, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        float spin = planet.spin(ctx.partialTick()) * Mth.DEG_TO_RAD;
        float flow = planet.flow(ctx.partialTick());
        float h = planet.half();
        // Orientation de la planete dans les axes du ciel (comme HoloPlacement.rotation), pour l'eclairage.
        HoloTransform t = planet.transform();
        rotation.rotationYXZ(-t.yaw() * Mth.DEG_TO_RAD, t.pitch() * Mth.DEG_TO_RAD, t.roll() * Mth.DEG_TO_RAD)
                .rotateY(spin);

        pose.pushPose();
        pose.mulPose(spinRotation.rotationY(spin));
        Matrix4f m = pose.last().pose();

        VertexConsumer vc = buffers.getBuffer(HoloRenderTypes.SOLID_QUADS);
        for (float[] face : FACES) {
            normal.set(face[0], face[1], face[2]).rotate(rotation);
            float light = normal.x * LIGHT_X + normal.y * LIGHT_Y + normal.z * LIGHT_Z;
            float shade = AMBIENT + (1f - AMBIENT) * Math.max(0f, light);
            for (int j = 0; j < ROW; j++) {
                for (int i = 0; i < ROW; i++) {
                    float a = (2f * i / GRID - 1f) * h;
                    float b = (2f * j / GRID - 1f) * h;
                    colors[j * ROW + i] = surfaceColor(face[0] * h + face[3] * a + face[6] * b,
                            face[1] * h + face[4] * a + face[7] * b, face[2] * h + face[5] * a + face[8] * b, h,
                            flow, shade);
                }
            }
            for (int j = 0; j < GRID; j++) {
                for (int i = 0; i < GRID; i++) {
                    vertex(vc, m, packedLight, face, h, i, j);
                    vertex(vc, m, packedLight, face, h, i + 1, j);
                    vertex(vc, m, packedLight, face, h, i + 1, j + 1);
                    vertex(vc, m, packedLight, face, h, i, j + 1);
                }
            }
        }

        vc = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        float s = h * HAZE_SCALE;
        int a = HAZE_COLOR >>> 24, r = HAZE_COLOR >> 16 & 0xFF, g = HAZE_COLOR >> 8 & 0xFF, bl = HAZE_COLOR & 0xFF;
        for (float[] face : FACES) {
            for (int k = 0; k < 4; k++) {
                // Coins (-,-), (+,-), (+,+), (-,+) dans la base (U, V).
                float u = k == 1 || k == 2 ? s : -s;
                float v = k >= 2 ? s : -s;
                vc.vertex(m, face[0] * s + face[3] * u + face[6] * v, face[1] * s + face[4] * u + face[7] * v,
                        face[2] * s + face[5] * u + face[8] * v).color(r, g, bl, a).uv2(packedLight).endVertex();
            }
        }
        pose.popPose();
    }

    /** Sommet {@code (i, j)} de la grille d'une face, avec sa couleur precalculee. */
    private void vertex(VertexConsumer vc, Matrix4f m, int light, float[] face, float h, int i, int j)
    {
        float a = (2f * i / GRID - 1f) * h;
        float b = (2f * j / GRID - 1f) * h;
        int c = colors[j * ROW + i];
        vc.vertex(m, face[0] * h + face[3] * a + face[6] * b, face[1] * h + face[4] * a + face[7] * b,
                face[2] * h + face[5] * a + face[8] * b).color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, 0xFF)
                .uv2(light).endVertex();
    }

    /** Couleur {@code 0xRRGGBB} de la surface au point local {@code (x, y, z)}, ombree par {@code shade}. */
    private static int surfaceColor(float x, float y, float z, float h, float flow, float shade)
    {
        // Longitude continue autour de l'axe Y : les frequences entieres raccordent la couture a +-PI.
        float theta = (float) Mth.atan2(z, x);
        float lat;
        if (Math.abs(y) >= h * 0.999f) {
            lat = Math.signum(y) * (1f + (1f - Math.max(Math.abs(x), Math.abs(z)) / h) * POLAR_STRETCH);
        } else {
            lat = y / h;
        }
        float turbulent = lat + 0.035f * Mth.sin(5f * theta + 7f * lat + flow)
                + 0.015f * Mth.sin(13f * theta - 4f * lat - 1.7f * flow);
        int color = palette(turbulent);

        float dTheta = Mth.wrapDegrees((theta - SPOT_THETA) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        float dx = dTheta / SPOT_WIDTH;
        float dy = (turbulent - SPOT_LAT) / SPOT_HEIGHT;
        float e = dx * dx + dy * dy;
        if (e < 1f) color = mix(color, SPOT_COLOR, (1f - e) * (1f - e));

        int r = (int) ((color >> 16 & 0xFF) * shade);
        int g = (int) ((color >> 8 & 0xFF) * shade);
        int b = (int) ((color & 0xFF) * shade);
        return r << 16 | g << 8 | b;
    }

    /** Couleur de la palette a la latitude {@code lat}, interpolee entre deux bandes. */
    private static int palette(float lat)
    {
        float s = Mth.clamp((lat + LAT_RANGE) / (2f * LAT_RANGE), 0f, 1f) * (PALETTE.length - 1);
        int index = Math.min((int) s, PALETTE.length - 2);
        return mix(PALETTE[index], PALETTE[index + 1], s - index);
    }

    private static int mix(int from, int to, float t)
    {
        int r = (int) Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int g = (int) Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return r << 16 | g << 8 | b;
    }
}
