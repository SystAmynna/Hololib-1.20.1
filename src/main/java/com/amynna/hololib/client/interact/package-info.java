/**
 * Interaction au regard, partie <b>client</b> : {@link com.amynna.hololib.client.interact.HoloInteractions} trouve
 * chaque frame l'hologramme sous le reticule ({@code client.render.holo.HoloPicking}), signale le survol et, comme
 * captor passif de la capture de controles ({@code client.capture}), route les controles interceptes vers les
 * elements {@link com.amynna.hololib.holo.interact.HoloInteractible}. Ne lit aucun input lui-meme ; aucun dessin.
 */
package com.amynna.hololib.client.interact;
