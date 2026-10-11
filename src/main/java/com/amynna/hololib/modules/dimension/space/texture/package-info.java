/**
 * Textures d'astres importees (T45, decisions D21, D27, C79, C136-C140) : <b>canal reseau dedie</b>
 * ({@link com.amynna.hololib.modules.dimension.space.texture.SpaceTextureNetwork}, {@code SimpleChannel}
 * {@code hololib:space_textures}, protocole propre, enregistre au setup commun par son propre abonne ; {@code HoloNetwork}
 * et son protocole ne changent pas), paquets
 * ({@link com.amynna.hololib.modules.dimension.space.texture.SpaceTexturePackets}), logique serveur
 * ({@link com.amynna.hololib.modules.dimension.space.texture.SpaceTextureServer} : reception d'un import par joueur,
 * envoi d'une texture au seul joueur qui la demande, morceaux paces par tick) et stockage
 * ({@link com.amynna.hololib.modules.dimension.space.texture.SpaceTextureStore} : {@code data/hololib_space_textures/
 * <sha1>.png} de la dimension, validation complete avant ecriture, quota de 64, orphelins supprimes quand il est
 * atteint).
 * <p>
 * Flux : le client de l'operateur prepare l'image ({@code client.modules.monde.spaceconfigurator.SpaceTextureImport}),
 * l'envoie ({@code Begin}, {@code Chunk}), puis, sur {@code DONE}, demande son application a l'astre par l'action 7 du
 * Configurator ({@code custom:<sha1>}, refusee si le fichier n'est pas stocke). Tout client qui rencontre une empreinte
 * inconnue la demande ({@code Request}) et la recoit en {@code Data} ; repli {@code auto} en attendant
 * ({@code client.modules.dimension.space.SpaceTextureClient}). Conception : {@code docs/space/02-architecture.md}
 * par. 7, {@code 05-synchronisation.md} par. 5.
 * <p>
 * Code commun : aucune classe client chargee sur un serveur dedie (handlers client par {@code DistExecutor}) ; decodage
 * serveur par {@code javax.imageio}.
 */
package com.amynna.hololib.modules.dimension.space.texture;
