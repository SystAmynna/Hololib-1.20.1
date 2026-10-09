/**
 * Logique client du module {@code hololib:space_environment} :
 * {@link com.amynna.hololib.client.modules.dimension.space.SpaceEnvironmentClient} installe le backend du module
 * (miroir de la dimension du joueur local, temps de jeu client, joueur local) au setup client. La simulation client
 * (dead reckoning, prediction du pilote) est dans le module lui-meme (code commun) ; le ciel est dessine par
 * {@code client.render.modules.dimension.space}. Conception : {@code docs/space/}.
 */
package com.amynna.hololib.client.modules.dimension.space;
