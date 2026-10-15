package com.amynna.hololib.client.render.modules.monde.areadisplayer;

import com.amynna.hololib.client.modules.monde.areadisplayer.AreaMesher;
import com.amynna.hololib.client.modules.monde.areadisplayer.AreaView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/**
 * Cache GPU du terrain de l'Area Displayer : par tuile ({@link AreaView.Tile}), deux {@code VertexBuffer} statiques
 * (quads solides et fluides), construits une fois quand le maillage de la tuile change puis dessines a chaque frame sans
 * re-emettre un seul vertex. Tout le code {@code VertexBuffer} du terrain est ici (fil de rendu seulement).
 * <p>
 * Les sommets ne portent que l'ombrage par direction (gris) et la lumiere pleine ; la couleur du theme et la
 * transparence sont posees par {@code RenderSystem.setShaderColor} avant le dessin ({@code ColorModulator} du shader
 * {@code position_color_lightmap}), donc un changement de theme ne reconstruit rien.
 * <p>
 * <b>Cycle de vie</b> : un {@code VertexBuffer} n'est jamais libere par le ramasse-miettes : {@link #release} (installe
 * comme liberation de {@link AreaView}) ferme ceux d'une tuile remplacee, retiree ou dont la vue est oubliee.
 */
final class AreaTileGpu
{
    /** Ombrage par direction : +X, -X, +Y, -Y, +Z, -Z. */
    private static final float[] SHADE = {0.8f, 0.8f, 1f, 0.5f, 0.65f, 0.65f};
    private static final int[] GRAY = new int[6];

    static {
        for (int i = 0; i < GRAY.length; i++) GRAY[i] = Math.round(255f * SHADE[i]);
    }

    /** Buffers d'une tuile ({@code null} : aucun quad de ce type). */
    private static final class Buffers
    {
        VertexBuffer solid;
        VertexBuffer fluid;
    }

    private static BufferBuilder builder;

    private AreaTileGpu() {}

    /** Installe la liberation des tuiles de {@link AreaView} (fil de rendu, au setup). */
    static void install() { AreaView.setReleaser(AreaTileGpu::release); }

    /** Envoie le maillage CPU de la tuile au GPU (remplace les buffers precedents) puis le libere cote CPU. */
    static void upload(AreaView.Tile tile)
    {
        release(tile);
        Buffers buffers = new Buffers();
        buffers.solid = build(tile.solid(), tile.solidQuads());
        buffers.fluid = build(tile.fluid(), tile.fluidQuads());
        tile.setGpu(buffers);
        tile.uploaded();
    }

    private static VertexBuffer build(float[] quads, int count)
    {
        if (quads == null || count == 0) return null;
        if (builder == null) builder = new BufferBuilder(1 << 20);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_LIGHTMAP);
        int light = LightTexture.FULL_BRIGHT;
        for (int at = 0; at < count * AreaMesher.FLOATS_PER_QUAD; at += AreaMesher.FLOATS_PER_QUAD) {
            int gray = GRAY[(int) quads[at + 12] % 6];
            for (int v = 0; v < 4; v++) {
                builder.vertex(quads[at + 3 * v], quads[at + 3 * v + 1], quads[at + 3 * v + 2])
                        .color(gray, gray, gray, 255).uv2(light).endVertex();
            }
        }
        BufferBuilder.RenderedBuffer rendered = builder.end();
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(rendered);
        VertexBuffer.unbind();
        return buffer;
    }

    /** Ferme les buffers GPU de la tuile. */
    static void release(AreaView.Tile tile)
    {
        if (!(tile.gpu() instanceof Buffers buffers)) return;
        if (buffers.solid != null) buffers.solid.close();
        if (buffers.fluid != null) buffers.fluid.close();
        tile.setGpu(null);
    }

    /** Vrai si la tuile a des buffers du type demande. */
    static boolean has(AreaView.Tile tile, boolean fluid)
    {
        return tile.gpu() instanceof Buffers buffers && (fluid ? buffers.fluid : buffers.solid) != null;
    }

    /** Dessine les quads solides ou fluides de la tuile ({@link #has} vrai) ; le {@code RenderType} est deja pose. */
    static void draw(AreaView.Tile tile, boolean fluid, Matrix4f modelView, Matrix4f projection, ShaderInstance shader)
    {
        Buffers buffers = (Buffers) tile.gpu();
        VertexBuffer buffer = fluid ? buffers.fluid : buffers.solid;
        buffer.bind();
        buffer.drawWithShader(modelView, projection, shader);
    }
}
