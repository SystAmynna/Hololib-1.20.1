package com.amynna.hololib.client.render.modules.monde.spaceconfigurator;

import com.amynna.hololib.client.render.holo.HoloDrawContext;
import com.amynna.hololib.client.render.holo.HoloObjectRenderer;
import com.amynna.hololib.client.render.holo.HoloWindowRenderer;
import com.amynna.hololib.client.render.modules.dimension.space.SpaceBodyTextureCache;
import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.modules.dimension.space.model.SpaceBody;
import com.amynna.hololib.modules.dimension.space.model.SpaceBodyKind;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceConfiguratorWindow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Renderer de la fenetre du Space Configurator (T44) : la fenetre elle-meme par le renderer generique des fenetres
 * ({@link HoloWindowRenderer}), puis, onglet « Texture » ouvert avec un corps selectionne, l'<b>apercu</b> de la
 * texture affichee pour ce corps ({@link SpaceBodyTextureCache#texture}, comme le ciel et la Space Map : image
 * importee prete, sinon texture integree ou regle auto) : l'image equirectangulaire entiere dans le rectangle
 * {@link SpaceConfiguratorWindow#PREVIEW_X}, {@link SpaceConfiguratorWindow#PREVIEW_Y},
 * {@link SpaceConfiguratorWindow#PREVIEW_WIDTH} x {@link SpaceConfiguratorWindow#PREVIEW_HEIGHT} pixels de la fenetre,
 * avance de {@value #PREVIEW_DEPTH} pixel vers l'avant (devant le fond de la fenetre). Une texture teintee (etoile) est
 * multipliee par la couleur du corps et eclaircie comme sur la carte ({@code SpaceMapCubeRenderer}, C92). Tant que
 * la texture n'est pas chargee : rectangle de la couleur du corps.
 * <p>
 * Le chargement est demande par cet appel ; le televersement est fait par {@code SpaceBodyTextureCache.beginFrame},
 * appele chaque frame par le ciel (et par le cube de la Map en vue systeme, celle ou l'on edite un corps). Sans
 * allocation par frame ; aucun etat GL modifie (seulement des {@code RenderType}).
 */
public final class SpaceConfiguratorWindowRenderer implements HoloObjectRenderer<SpaceConfiguratorWindow>
{
    /** Avance de l'apercu vers l'avant, en pixels de la fenetre. */
    private static final float PREVIEW_DEPTH = 0.5f;

    private final HoloWindowRenderer window = new HoloWindowRenderer();
    private final Vector3f vertex = new Vector3f();

    @Override
    public void render(SpaceConfiguratorWindow object, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       HoloDrawContext ctx)
    {
        window.render(object, pose, buffers, packedLight, ctx);
        SpaceSystem system = object.previewSystem();
        int index = object.previewIndex();
        if (system == null || index < 0 || index >= system.bodies().size()) return;

        // Repere local de l'objet : blocs, y vers le haut, centre ; face visible vers +Z.
        float s = object.pixelScale();
        float x0 = (SpaceConfiguratorWindow.PREVIEW_X - 0.5f * object.width()) * s;
        float x1 = x0 + SpaceConfiguratorWindow.PREVIEW_WIDTH * s;
        float y1 = (0.5f * object.height() - SpaceConfiguratorWindow.PREVIEW_Y) * s;
        float y0 = y1 - SpaceConfiguratorWindow.PREVIEW_HEIGHT * s;
        float z = PREVIEW_DEPTH * s;
        Matrix4f matrix = pose.last().pose();
        SpaceBody body = system.bodies().get(index);
        int color = body.color();
        float red = (color >> 16 & 0xFF) / 255f;
        float green = (color >> 8 & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;

        SpaceBodyTextureCache.Texture texture = SpaceBodyTextureCache.instance().texture(system, index);
        if (texture == null) {
            // Repli : couleur unie du corps (sens anti-horaire vu de +Z : faces arriere eliminees).
            VertexConsumer vc = buffers.getBuffer(HoloRenderTypes.SOLID_QUADS);
            solid(vc, matrix, x0, y0, z, red, green, blue, packedLight);
            solid(vc, matrix, x1, y0, z, red, green, blue, packedLight);
            solid(vc, matrix, x1, y1, z, red, green, blue, packedLight);
            solid(vc, matrix, x0, y1, z, red, green, blue, packedLight);
            return;
        }
        if (texture.tinted()) {
            if (body.kind() == SpaceBodyKind.STAR) {
                float gain = Math.min(1f / Math.max(texture.luminance(), 0.05f),
                        1f / Math.max(Math.max(red, green), Math.max(blue, 0.05f)));
                red = Math.min(1f, red * gain);
                green = Math.min(1f, green * gain);
                blue = Math.min(1f, blue * gain);
            }
        } else {
            red = 1f;
            green = 1f;
            blue = 1f;
        }
        // Image entiere : u de 0 (longitude -180) a 1, v de 0 (nord, en haut) a 1.
        VertexConsumer vc = buffers.getBuffer(texture.renderType());
        textured(vc, matrix, x0, y0, z, red, green, blue, 0f, 1f);
        textured(vc, matrix, x1, y0, z, red, green, blue, 1f, 1f);
        textured(vc, matrix, x1, y1, z, red, green, blue, 1f, 0f);
        textured(vc, matrix, x0, y1, z, red, green, blue, 0f, 0f);
    }

    private void solid(VertexConsumer vc, Matrix4f matrix, float x, float y, float z, float red, float green,
                       float blue, int light)
    {
        matrix.transformPosition(x, y, z, vertex);
        vc.vertex(vertex.x, vertex.y, vertex.z).color(red, green, blue, 1f).uv2(light).endVertex();
    }

    private void textured(VertexConsumer vc, Matrix4f matrix, float x, float y, float z, float red, float green,
                          float blue, float u, float v)
    {
        matrix.transformPosition(x, y, z, vertex);
        vc.vertex(vertex.x, vertex.y, vertex.z).color(red, green, blue, 1f).uv(u, v).endVertex();
    }
}
