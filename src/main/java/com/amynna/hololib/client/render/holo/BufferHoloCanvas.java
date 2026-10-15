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
    /** Ecart en profondeur entre deux dessins successifs, en pixels (regle par la fenetre). */
    private float zStep = 0.02f;

    private final Matrix4f textMatrix = new Matrix4f();
    private float[] offsets = new float[32];
    private int depth;
    /** Pile des clips (4 flottants chacun, avant le clip courant) ; {@code clipDepth} = nombre de clips ouverts. */
    private float[] clips = new float[16];
    private int clipDepth;
    private float cx0;
    private float cy0;
    private float cx1;
    private float cy1;
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

    void begin(PoseStack.Pose pose, MultiBufferSource buffers, int light, float partialTick, HoloTheme theme,
               float zStep)
    {
        this.zStep = zStep;
        clipDepth = 0;
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
    public float z() { return z; }

    @Override
    public void z(float z) { this.z = z; }

    @Override
    public void shiftDepth(float dz) { z += dz; }

    @Override
    public void pushClip(float x, float y, float width, float height)
    {
        if (4 * clipDepth + 3 >= clips.length) clips = Arrays.copyOf(clips, clips.length * 2);
        if (clipDepth == 0) {
            cx0 = Float.NEGATIVE_INFINITY;
            cy0 = Float.NEGATIVE_INFINITY;
            cx1 = Float.POSITIVE_INFINITY;
            cy1 = Float.POSITIVE_INFINITY;
        }
        clips[4 * clipDepth] = cx0;
        clips[4 * clipDepth + 1] = cy0;
        clips[4 * clipDepth + 2] = cx1;
        clips[4 * clipDepth + 3] = cy1;
        clipDepth++;
        cx0 = Math.max(cx0, ox + x);
        cy0 = Math.max(cy0, oy + y);
        cx1 = Math.min(cx1, ox + x + width);
        cy1 = Math.min(cy1, oy + y + height);
    }

    @Override
    public void popClip()
    {
        if (clipDepth == 0) throw new IllegalStateException("HoloCanvas.popClip sans pushClip");
        clipDepth--;
        cx0 = clips[4 * clipDepth];
        cy0 = clips[4 * clipDepth + 1];
        cx1 = clips[4 * clipDepth + 2];
        cy1 = clips[4 * clipDepth + 3];
    }

    @Override
    public void beginLayer() { layers++; }

    @Override
    public void endLayer()
    {
        if (layers == 0) throw new IllegalStateException("HoloCanvas.endLayer sans beginLayer");
        if (--layers == 0) z += zStep;
    }

    /** Pas en profondeur apres un dessin, sauf dans une couche. */
    private void advance()
    {
        if (layers == 0) z += zStep;
    }

    @Override
    public void fill(float x, float y, float width, float height, int argb)
    {
        if (width <= 0f || height <= 0f) return;
        float x0 = ox + x;
        float y0 = oy + y;
        float x1 = x0 + width;
        float y1 = y0 + height;
        if (clipDepth > 0) {
            x0 = Math.max(x0, cx0);
            y0 = Math.max(y0, cy0);
            x1 = Math.min(x1, cx1);
            y1 = Math.min(y1, cy1);
            if (x1 <= x0 || y1 <= y0) return;
        }
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
        if (clipDepth > 0) {
            float nx0 = Math.max(x0, cx0);
            float ny0 = Math.max(y0, cy0);
            float nx1 = Math.min(x1, cx1);
            float ny1 = Math.min(y1, cy1);
            if (nx1 <= nx0 || ny1 <= ny0) return;
            float u0 = (nx0 - x0) / width;
            float u1 = (nx1 - x0) / width;
            float v0 = (ny0 - y0) / height;
            float v1 = (ny1 - y0) / height;
            int tl = bilerp(topLeft, topRight, bottomRight, bottomLeft, u0, v0);
            int tr = bilerp(topLeft, topRight, bottomRight, bottomLeft, u1, v0);
            int br = bilerp(topLeft, topRight, bottomRight, bottomLeft, u1, v1);
            int bl = bilerp(topLeft, topRight, bottomRight, bottomLeft, u0, v1);
            topLeft = tl;
            topRight = tr;
            bottomRight = br;
            bottomLeft = bl;
            x0 = nx0;
            y0 = ny0;
            x1 = nx1;
            y1 = ny1;
        }
        Matrix4f m = pose.pose();
        VertexConsumer vc = buffers.getBuffer(HoloRenderTypes.TRANSLUCENT_QUADS);
        vertex(vc, m, x0, y0, topLeft);
        vertex(vc, m, x0, y1, bottomLeft);
        vertex(vc, m, x1, y1, bottomRight);
        vertex(vc, m, x1, y0, topRight);
        advance();
    }

    /** Couleur interpolee bilineairement dans le rectangle ({@code u}, {@code v} dans 0..1), par canal. */
    private static int bilerp(int tl, int tr, int br, int bl, float u, float v)
    {
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            float top = (tl >>> shift & 0xFF) * (1f - u) + (tr >>> shift & 0xFF) * u;
            float bottom = (bl >>> shift & 0xFF) * (1f - u) + (br >>> shift & 0xFF) * u;
            out |= Math.round(top * (1f - v) + bottom * v) << shift;
        }
        return out;
    }

    private void vertex(VertexConsumer vc, Matrix4f m, float x, float y, int argb)
    {
        vc.vertex(m, x, y, z).color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24).uv2(light)
                .endVertex();
    }

    @Override
    public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb)
    {
        if (clipDepth > 0) {
            float minX = Math.min(Math.min(x0, x1), Math.min(x2, x3)) + ox;
            float maxX = Math.max(Math.max(x0, x1), Math.max(x2, x3)) + ox;
            float minY = Math.min(Math.min(y0, y1), Math.min(y2, y3)) + oy;
            float maxY = Math.max(Math.max(y0, y1), Math.max(y2, y3)) + oy;
            if (minX < cx0 || maxX > cx1 || minY < cy0 || maxY > cy1) return;
        }
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
        if (clipDepth > 0 && (ox + x < cx0 || ox + x > cx1 || oy + y < cy0 || oy + y + font.lineHeight > cy1)) return;
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
        if (clipDepth > 0) {
            float nx0 = Math.max(x0, cx0);
            float ny0 = Math.max(y0, cy0);
            float nx1 = Math.min(x1, cx1);
            float ny1 = Math.min(y1, cy1);
            if (nx1 <= nx0 || ny1 <= ny0) return;
            float du = u1 - u0;
            float dv = v1 - v0;
            float nu0 = u0 + du * (nx0 - x0) / width;
            float nu1 = u0 + du * (nx1 - x0) / width;
            float nv0 = v0 + dv * (ny0 - y0) / height;
            float nv1 = v0 + dv * (ny1 - y0) / height;
            u0 = nu0;
            u1 = nu1;
            v0 = nv0;
            v1 = nv1;
            x0 = nx0;
            y0 = ny0;
            x1 = nx1;
            y1 = ny1;
        }
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
