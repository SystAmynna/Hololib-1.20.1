/**
 * Renderers des blocs du mod ({@code com.amynna.hololib.block}), hors familles de {@code RenderDispatcher} : ce sont
 * des {@code BlockEntityRenderer} / {@code BlockEntityWithoutLevelRenderer} vanilla, dessines par Minecraft.
 * <ul>
 *     <li>Bassin cosmique : {@link com.amynna.hololib.client.render.block.CosmicPoolShaders} (shader
 *     {@code hololib:cosmic_pool}, <b>a editer dans {@code assets/hololib/shaders/core/cosmic_pool.fsh}</b>, uniformes
 *     declares dans {@code cosmic_pool.json} ; bobbing de la marche compense ; enregistrement du renderer),
 *     {@link com.amynna.hololib.client.render.block.CosmicPoolRenderType} (monde / GUI et premiere personne),
 *     {@link com.amynna.hololib.client.render.block.CosmicPoolRenderer} (faces visibles du bloc),
 *     {@link com.amynna.hololib.client.render.block.CosmicPoolItemRenderer} (item).</li>
 * </ul>
 */
package com.amynna.hololib.client.render.block;
