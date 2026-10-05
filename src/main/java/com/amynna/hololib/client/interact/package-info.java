/**
 * Interaction au regard, partie <b>client</b> : {@link com.amynna.hololib.client.interact.HoloInteractions}, seul
 * abonne du mod aux inputs souris, trouve chaque frame l'hologramme sous le reticule
 * ({@code client.render.holo.HoloPicking}), signale le survol, intercepte les clics, la molette et les mouvements de
 * la souris pris par les elements {@link com.amynna.hololib.holo.interact.HoloInteractible}, et annule les inputs
 * vanilla correspondants. Aucune logique de rendu ici, aucun dessin.
 */
package com.amynna.hololib.client.interact;
