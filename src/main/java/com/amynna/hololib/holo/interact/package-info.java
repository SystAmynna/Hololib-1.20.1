/**
 * Interaction au regard avec les hologrammes, partie <b>commune</b> (aucune classe client).
 * <ul>
 *     <li>{@link com.amynna.hololib.holo.interact.HoloInteractible} : element (objet, fenetre, widget) qui recoit le
 *     survol et choisit, controle par controle ({@code holo.capture.HoloControl}), ce qu'il intercepte tant qu'il est
 *     vise.</li>
 *     <li>{@link com.amynna.hololib.holo.interact.HoloInteraction} : contexte des callbacks (point vise, regard,
 *     capture explicite liee a l'objet), implemente cote client.</li>
 * </ul>
 * Le point vise est le centre de l'ecran. Le controleur client ({@code client.interact.HoloInteractions}) lance le
 * rayon a chaque frame dans les quatre familles (HUD, vue, monde, ciel, dans cet ordre de priorite) et ne retient que
 * les objets dont l'{@link com.amynna.hololib.holo.object.HoloAccess acces} autorise le joueur local.
 * <p>
 * L'interaction ne lit aucun input elle-meme : elle est un captor passif de la capture de controles
 * ({@code holo.capture}), qui reste utilisable seule. Les callbacks sont locaux : un effet sur le jeu passe par une
 * action de module, que le serveur verifie (droit d'acces de l'objet source, visibilite, et pour l'objet d'un autre
 * joueur, inspection).
 */
package com.amynna.hololib.holo.interact;
