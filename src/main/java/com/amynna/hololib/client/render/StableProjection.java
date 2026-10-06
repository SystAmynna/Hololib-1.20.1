package com.amynna.hololib.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Projection perspective sans bobbing, secousse de degats ni nausee (que vanilla applique a la pile de projection du
 * monde dans {@code GameRenderer.renderLevel}), memes plans proche et lointain que vanilla. Sans allocation.
 */
public final class StableProjection
{
    /** Plan proche de vanilla ({@code GameRenderer.getProjectionMatrix}). */
    private static final float NEAR = 0.05f;

    private StableProjection() {}

    /** Ecrit dans {@code dest} la perspective de FOV vertical {@code fovDegrees} et la renvoie. */
    public static Matrix4f set(Matrix4f dest, float fovDegrees)
    {
        Minecraft mc = Minecraft.getInstance();
        float aspect = (float) mc.getWindow().getWidth() / (float) mc.getWindow().getHeight();
        return dest.setPerspective(fovDegrees * Mth.DEG_TO_RAD, aspect, NEAR, mc.gameRenderer.getDepthFar());
    }

    /** FOV configure dans les options, sans modificateur (sprint, effets, eau, mort, autres mods), en degres. */
    public static float configuredFov() { return Minecraft.getInstance().options.fov().get(); }
}
