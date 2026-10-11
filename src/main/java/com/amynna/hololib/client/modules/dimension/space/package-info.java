/**
 * Logique client du module {@code hololib:space_environment} :
 * {@link com.amynna.hololib.client.modules.dimension.space.SpaceEnvironmentClient} installe le backend du module
 * (miroir de la dimension du joueur local, temps de jeu client, joueur local) au setup client. La simulation client
 * (dead reckoning, prediction du pilote) est dans le module lui-meme (code commun) ; le ciel est dessine par
 * {@code client.render.modules.dimension.space}.
 * <p>
 * {@link com.amynna.hololib.client.modules.dimension.space.SpaceSoundController} (T36, D26, C80) : sons du vaisseau
 * (moteurs, coque, charge et sequence du saut) deduits par chaque client de l'etat affiche, entendus par tous les
 * joueurs de la dimension ; non positionnels, categorie {@code AMBIENT}, evenements crees cote client
 * ({@code assets/hololib/sounds.json}, fichiers et credits sous {@code assets/hololib/sounds/space/}). Appele par
 * {@code SpaceEnvironmentModule.clientTick} via le backend, coupe par {@code onUnload} du miroir.
 * <p>
 * {@link com.amynna.hololib.client.modules.dimension.space.SpaceTextureClient} (T45, D27) : cote client du canal des
 * textures Space ({@code modules.dimension.space.texture}) : demande au serveur des textures importees qu'un corps
 * reference sans image dans le cache, reassemblage, verification et decodage hors du fil de rendu, enregistrement dans
 * {@code SpaceBodyTextureCache} ; transport d'un import (morceaux paces par tick) ; tout est oublie a la deconnexion.
 * Conception : {@code docs/space/}.
 */
package com.amynna.hololib.client.modules.dimension.space;
