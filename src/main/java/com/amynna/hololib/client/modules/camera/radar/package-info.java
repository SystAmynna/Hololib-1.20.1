/**
 * Logique client du module {@code hololib:radar}, pour le joueur local seulement :
 * <ul>
 *     <li>{@link com.amynna.hololib.client.modules.camera.radar.RadarClient} : module de la memoire du joueur local
 *     (une fois par tick), backend du module (Activer / Desactiver, texte d'etat de la fenetre) ;</li>
 *     <li>{@link com.amynna.hololib.client.modules.camera.radar.RadarScan} : onde (origine figee au lancement, rayon,
 *     effacement) et entites reperees, testees une fois quand l'onde les atteint, gardees jusqu'a Desactiver ;</li>
 *     <li>{@link com.amynna.hololib.client.modules.camera.radar.RadarWalls} : epaisseur de mur entre l'oeil et la
 *     cible (blocs occultants comptes le long du segment).</li>
 * </ul>
 * Rien n'est envoye au serveur : la detection porte sur les entites connues du client (portee de suivi des entites).
 * Dessin : {@code client.render.modules.camera.radar}.
 */
package com.amynna.hololib.client.modules.camera.radar;
