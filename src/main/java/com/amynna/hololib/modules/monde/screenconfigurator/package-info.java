/**
 * Module {@code hololib:screen_configurator} (memoire de position, marker), reserve aux operateurs : reglages du
 * Screen du meme marker ({@code modules.monde.screen}).
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.monde.screenconfigurator.ScreenConfiguratorModule} : aucun etat propre ;
 *     actions verifiees (operateur d'abord, puis source, Screen present) relayees a l'API serveur du Screen ; acces de
 *     la fenetre = operateurs connectes ; backend client (joueur local operateur) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.screenconfigurator.ScreenConfiguratorWindow} : fenetre {@code World}
 *     (mode camera fixe / joueur, position et orientation ou « Ici », joueur suivi ou « Moi », FOV, resolution et
 *     prereglages 16:9, images/s), dessinee chez les operateurs seulement.</li>
 * </ul>
 */
package com.amynna.hololib.modules.monde.screenconfigurator;
