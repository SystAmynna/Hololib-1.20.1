package com.amynna.hololib.client.render.modules.dimension.space.warp;

import com.amynna.hololib.HoloLib;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * {@code RenderType} locaux de la distorsion (sous-classe pour acceder aux etats proteges, jamais instanciee) : quads
 * {@code POSITION_COLOR_LIGHTMAP} double face, <b>sans ecriture de profondeur</b> ni tri, test de profondeur normal.
 * Dans la passe du ciel, l'ordre d'envoi fait l'ordre de dessin (un changement de type vide le lot precedent) :
 * <ul>
 *     <li>{@link #BLEND} : translucide classique (degrade, trainees, flash) ;</li>
 *     <li>{@link #ADD} : additif pondere par l'alpha ({@code SRC_ALPHA, ONE}, comme les eclairs vanilla) : source
 *     lumineuse, halos et flares s'ajoutent a ce qui est dessous.</li>
 * </ul>
 * Aucun etat GL modifie a la main.
 */
final class SpaceWarpTypes extends RenderType
{
    static final RenderType BLEND = create(HoloLib.MODID + ":space_warp_blend",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_LIGHTMAP_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    static final RenderType ADD = create(HoloLib.MODID + ":space_warp_add",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_LIGHTMAP_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private SpaceWarpTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                           boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear)
    {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
