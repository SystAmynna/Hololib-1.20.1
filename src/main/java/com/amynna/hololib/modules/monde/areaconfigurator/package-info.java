/**
 * Module {@code hololib:area_displayer_configurator} (memoire de position, marker), reserve aux operateurs : reglages
 * de l'Area Displayer du meme marker ({@code modules.monde.areadisplayer}).
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.monde.areaconfigurator.AreaConfiguratorModule} : aucun etat propre ;
 *     actions verifiees (operateur d'abord, puis source, Displayer present) relayees a l'API serveur du Displayer ;
 *     acces de la fenetre = operateurs connectes ; backend client (joueur local operateur) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.areaconfigurator.AreaConfiguratorWindow} : fenetre {@code World}
 *     (coins A / B saisis champ par champ ou « Ici », Cadre, Resolution a paliers (1:1 a 1:32, stricte), Auto Update et intervalle,
 *     Joueurs / Entites et leurs trainees, liste noire de blocs ignores par le scan, « Actualiser »), dessinee chez les operateurs seulement.</li>
 * </ul>
 */
package com.amynna.hololib.modules.monde.areaconfigurator;
