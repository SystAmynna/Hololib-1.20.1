/**
 * Module {@code hololib:screen} (memoire de position, marker), visible par tous : camera de surveillance. Un ecran
 * holographique montre le rendu d'une zone, rendu par chaque client qui le regarde avec ses propres reglages
 * graphiques (shaderpack compris) par une vue secondaire du framework ({@code client.render.levelview}).
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.monde.screen.ScreenModule} : seule autorite sur l'etat (mode camera fixe /
 *     regard d'un joueur, position, orientation, FOV, joueur suivi, resolution, cadence), sauvegarde et synchronise ;
 *     API serveur publique reservee au Screen Configurator du meme marker ; aucune action ; backend client (vue
 *     ouverte, reglee, fermee) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.screen.ScreenDisplay} : ecran {@code World} aux proportions de la
 *     resolution, editable par les operateurs (translation, rotation, echelle uniforme).</li>
 * </ul>
 * Limites (celles des vues secondaires) : la camera ne montre que les chunks charges par le client qui regarde, et un
 * joueur suivi doit etre dans son niveau client (sinon « Pas de signal », retabli de lui-meme).
 */
package com.amynna.hololib.modules.monde.screen;
