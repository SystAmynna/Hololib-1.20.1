package com.amynna.hololib.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * {@code BufferSource} du mod, partage par les passes Sky, World et View (sequentielles, fil de rendu ; chacune le vide
 * a la fin). Mode immediat, sans buffer fixe : changer de {@code RenderType} envoie le lot precedent, donc l'ordre
 * d'emission est l'ordre de dessin.
 * <p>
 * On n'utilise jamais {@code RenderBuffers.bufferSource()} : pendant {@code LevelRenderer.renderLevel}, Oculus (meme
 * sans shaderpack) le remplace par un tampon qui ignore {@code endBatch(RenderType)}, dessine tout au profiler
 * {@code "translucent"} et reordonne les {@code RenderType} (graphe par entite, nos types y ont un ordre arbitraire).
 */
public final class HoloBuffers
{
    private static final MultiBufferSource.BufferSource SOURCE = MultiBufferSource.immediate(new BufferBuilder(256));

    private HoloBuffers() {}

    public static MultiBufferSource.BufferSource source() { return SOURCE; }
}
