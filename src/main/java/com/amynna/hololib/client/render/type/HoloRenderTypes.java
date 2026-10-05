package com.amynna.hololib.client.render.type;

import com.amynna.hololib.HoloLib;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * {@code RenderType} du mod (sous-classe pour acceder aux etats proteges de {@code RenderStateShard} ; jamais
 * instanciee). Format {@code POSITION_COLOR_LIGHTMAP} : {@code vertex(matrice, x, y, z).color(..).uv2(lumiere)}.
 * Tous ecrivent dans la cible principale : utilisables par les {@code WorldRenderer} comme par les
 * {@code ViewRenderer} (passer {@code LightTexture.FULL_BRIGHT} dans la vue).
 */
public final class HoloRenderTypes extends RenderType
{
    /** Quads opaques, faces arriere eliminees. */
    public static final RenderType SOLID_QUADS = create(HoloLib.MODID + ":solid_quads",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_LIGHTMAP_SHADER)
                    .setLightmapState(LIGHTMAP)
                    .createCompositeState(false));

    /**
     * Quads translucides, double face, tries par distance a l'envoi ; ecrivent la profondeur (un objet translucide
     * cache ce qui est dessine apres lui derriere).
     */
    public static final RenderType TRANSLUCENT_QUADS = create(HoloLib.MODID + ":translucent_quads",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_LIGHTMAP_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .createCompositeState(false));

    private HoloRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                            boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear)
    {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
