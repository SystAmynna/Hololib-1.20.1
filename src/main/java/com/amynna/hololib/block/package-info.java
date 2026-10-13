/**
 * Blocs du mod (code commun), enregistres par {@link com.amynna.hololib.block.HoloLibBlocks}.
 * <ul>
 *     <li>{@link com.amynna.hololib.block.CosmicPoolBlock} ({@code hololib:<couleur>_cosmic_pool}, 16 couleurs) : cube
 *     plein dessine par un block entity renderer avec le shader {@code assets/hololib/shaders/core/cosmic_pool.fsh} :
 *     trou profond en parallaxe (fond etoile, deux plans de fluide pixelises, particules en volume ; continu entre
 *     bassins voisins de la meme couleur, teintes tirees de la couleur), bords lumineux ondulants la ou la surface
 *     s'arrete (masques calcules par {@link com.amynna.hololib.block.CosmicPoolBlockEntity#faceMask}) ; item dessine
 *     avec le meme shader. Rendu : {@code client/render/block}.</li>
 * </ul>
 */
package com.amynna.hololib.block;
