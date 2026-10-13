package com.amynna.hololib.client.render.block;

import com.amynna.hololib.HoloLib;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

import java.util.function.Supplier;

/**
 * {@code RenderType} du bassin cosmique (sous-classe pour acceder aux etats proteges ; jamais instanciee), calque sur
 * {@code RenderType.endPortal()} : opaque, faces arriere eliminees, sans lumiere. Format
 * {@code POSITION_COLOR_TEX_LIGHTMAP} detourne : couleur = masques des bords et coins + id de la couleur, UV =
 * coordonnees locales de la face, UV2 = origine du bloc dans le plan de la face (voir {@link CosmicPoolRenderer}).
 */
public final class CosmicPoolRenderType extends RenderType
{
    /** Dans le monde (bloc, item pose ou porte par une entite) : l'interieur ignore le bobbing de la marche. */
    public static final RenderType COSMIC_POOL = pool(HoloLib.MODID + ":cosmic_pool", CosmicPoolShaders::worldShader);

    /** GUI et main en premiere personne : pas de bobbing dans la projection, rien a compenser. */
    public static final RenderType COSMIC_POOL_STILL =
            pool(HoloLib.MODID + ":cosmic_pool_still", CosmicPoolShaders::stillShader);

    private CosmicPoolRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                 boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear)
    {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    /** Type opaque du bassin avec le shader donne. */
    private static RenderType pool(String name, Supplier<ShaderInstance> shader)
    {
        return create(name, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(shader))
                        .createCompositeState(false));
    }
}
