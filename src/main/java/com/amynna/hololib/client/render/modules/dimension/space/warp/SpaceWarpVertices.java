package com.amynna.hololib.client.render.modules.dimension.space.warp;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Emission de sommets {@code POSITION_COLOR_LIGHTMAP} transformes sans allocation
 * ({@code VertexConsumer.vertex(Matrix4f, ...)} cree un vecteur, C38), partagee par les parties de la distorsion.
 * Fil de rendu.
 */
final class SpaceWarpVertices
{
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    private final Vector3f transformed = new Vector3f();

    void put(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a)
    {
        m.transformPosition(x, y, z, transformed);
        vc.vertex(transformed.x, transformed.y, transformed.z).color(r, g, b, a).uv2(LIGHT).endVertex();
    }
}
