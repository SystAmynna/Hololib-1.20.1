package com.amynna.hololib.client.render.world;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;

import java.util.List;

/**
 * Passe des {@link WorldRenderer}, appelee par {@code RenderDispatcher} a {@code AFTER_ENTITIES}. Usage interne.
 * Aucun etat GL n'est touche : les {@code RenderType} des vertices s'en chargent quand vanilla vide les buffers.
 */
public final class WorldRenderPass
{
    private static final WorldRenderContext CONTEXT = new WorldRenderContext();

    private WorldRenderPass() {}

    /** @param pose PoseStack de l'evenement (rotation camera), rendu dans l'etat recu */
    public static void render(List<WorldRenderer<?>> renderers, PoseStack pose, Camera camera, Frustum frustum,
                              float partialTick)
    {
        ClientLevel level = Minecraft.getInstance().level;
        if (renderers.isEmpty() || level == null) return;
        CONTEXT.set(level, camera, frustum, partialTick);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (WorldRenderer<?> renderer : renderers) renderer.renderAll(pose, buffers, CONTEXT);
    }
}
