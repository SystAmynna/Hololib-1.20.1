package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.client.render.type.HoloRenderTypes;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * {@link HoloCanvas} sur un {@code MultiBufferSource}. La pile doit etre dans le repere du GUI (pixels, {@code y} vers
 * le bas, face visible vers {@code +Z}) : voir {@link HoloWindowRenderer}. Les decalages de {@link #push} sont tenus
 * en flottants (pas de {@code pushPose}, donc pas d'allocation) ; chaque dessin avance d'un pas en {@code z} pour
 * eviter le z-fighting, une couche ({@link #beginLayer()}) d'un seul pas a sa fin. Objet reutilise, fil de rendu
 * uniquement.
 */
final class BufferHoloCanvas implements HoloCanvas
{
    /** Ecart en profondeur entre deux dessins successifs, en pixels. */
    private static final float Z_STEP = 0.1f;

    private final Matrix4f textMatrix = new Matrix4f();
    private float[] offsets = new float[32];
    private int depth;
    /** Couches ouvertes : tant qu'il y en a, {@code z} n'avance pas. */
    private int layers;
    private PoseStack.Pose pose;
    private MultiBufferSource buffers;
    private Font font;
    private int light;
    private float partialTick;
    private HoloTheme theme = HoloTheme.DEFAULT;
    private float ox;
    private float oy;
    private float z;

    void begin(PoseStack.Pose pose, MultiBufferSource buffers, int light, float partialTick, HoloTheme theme)
    {
        this.pose = pose;
        this.theme = theme;
        this.partialTick = partialTick;
        this.buffers = buffers;
        this.light = light;
        this.font = Minecraft.getInstance().font;
        depth = 0;
        layers = 0;
        ox = 0f;
        oy = 0f;
        z = 0f;
    }

    void end()
    {
        pose = null;
        buffers = null;
    }

    @Override
    public float partialTick() { return partialTick; }

    @Override
    public HoloTheme theme() { return theme; }

    @Override
    public void push(float dx, float dy)
    {
        if (2 * depth + 1 >= offsets.length) offsets = Arrays.copyOf(offsets, offsets.length * 2);
        offsets[2 * depth] = ox;
        offsets[2 * depth + 1] = oy;
        depth++;
        ox += dx;
        oy += dy;
    }

    @Override
    public void pop()
    {
        if (depth == 0) throw new IllegalStateException("HoloCanvas.pop sans push");
        depth--;
        ox = offsets[2 * depth];
        oy = offsets[2 * depth + 1];
    }

    @Override
    public void beginLayer() { layers++; }

    @Override
    public void endLayer()
    {
        if (layers == 0) throw new IllegalStateException("HoloCanvas.endLayer sans beginLayer");
        if (--layers == 0) z += Z_STEP;
    }

    /** Pas en profondeur apres un dessin, sauf dans une couche. */
    private void advance()
    {
        if (layers == 0) z += Z_STEP;
    }

    @Override
    public void fill(float x, float y, float width, float height, int argb)
    {
        if (width <= 0f || height <= 0f) return;
        float x0 = ox + x;
        float y0 = oy + y;
        float x1 = x0 + width;
        float y1 = y0 + height;
        int a = argb >>> 24;
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        Matrix4f m = pose.pose();
        VertexConsumer vc = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(light).endVertex();
        advance();
    }

    @Override
    public void gradient(float x, float y, float width, float height, int topLeft, int topRight, int bottomRight,
                         int bottomLeft)
    {
        if (width <= 0f || height <= 0f) return;
        float x0 = ox + x;
        float y0 = oy + y;
        float x1 = x0 + width;
        float y1 = y0 + height;
        Matrix4f m = pose.pose();
        VertexConsumer vc = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        vertex(vc, m, x0, y0, topLeft);
        vertex(vc, m, x0, y1, bottomLeft);
        vertex(vc, m, x1, y1, bottomRight);
        vertex(vc, m, x1, y0, topRight);
        advance();
    }

    private void vertex(VertexConsumer vc, Matrix4f m, float x, float y, int argb)
    {
        vc.vertex(m, x, y, z).color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24).uv2(light)
                .endVertex();
    }

    @Override
    public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb)
    {
        int a = argb >>> 24;
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        Matrix4f m = pose.pose();
        VertexConsumer vc = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        vc.vertex(m, ox + x0, oy + y0, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, ox + x1, oy + y1, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, ox + x2, oy + y2, z).color(r, g, b, a).uv2(light).endVertex();
        vc.vertex(m, ox + x3, oy + y3, z).color(r, g, b, a).uv2(light).endVertex();
        advance();
    }

    @Override
    public void text(Component text, float x, float y, int argb)
    {
        textMatrix.set(pose.pose()).translate(0f, 0f, z);
        font.drawInBatch(text, ox + x, oy + y, argb, false, textMatrix, buffers, Font.DisplayMode.NORMAL, 0, light);
        advance();
    }

    @Override
    public int textWidth(Component text) { return Minecraft.getInstance().font.width(text); }

    @Override
    public int lineHeight() { return Minecraft.getInstance().font.lineHeight; }

    @Override
    public void texture(ResourceLocation texture, float x, float y, float width, float height, float u0, float v0,
                        float u1, float v1, int argb)
    {
        if (width <= 0f || height <= 0f) return;
        float x0 = ox + x;
        float y0 = oy + y;
        float x1 = x0 + width;
        float y1 = y0 + height;
        int a = argb >>> 24;
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        Matrix4f m = pose.pose();
        // RenderType.text : POSITION_COLOR_TEX_LIGHTMAP, translucide, cible principale ; memoise par texture.
        VertexConsumer vc = buffers.getBuffer(RenderType.text(texture));
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv(u0, v0).uv2(light).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv(u0, v1).uv2(light).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv(u1, v1).uv2(light).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv(u1, v0).uv2(light).endVertex();
        advance();
    }
}
