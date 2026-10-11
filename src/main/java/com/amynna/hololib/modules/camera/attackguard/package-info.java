/**
 * Module {@code hololib:attack_guard} : le proprietaire ne peut pas attaquer un joueur qui possede (module
 * {@code hololib:keys}) une cle de la whitelist du module, editee dans sa fenetre de Config. Joueurs proteges proches
 * calcules par le serveur ; attaque annulee cote serveur ({@code AttackEntityEvent}) et interceptee cote client
 * ({@code client.modules.camera.attackguard}), reticule remplace par une croix rouge
 * ({@code client.render.modules.camera.attackguard}).
 */
package com.amynna.hololib.modules.camera.attackguard;
