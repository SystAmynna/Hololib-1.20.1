/**
 * Module {@code hololib:trigger_area} (memoire de position, marker), visible par tous si l'option « visible » est
 * active : zone de declenchement de commandes.
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.monde.triggerarea.TriggerAreaModule} : seule autorite sur l'etat (listes de
 *     commandes d'entree, de sortie et de tick, options visible / joueurs / entites vivantes), sauvegarde et synchronise ; API
 *     serveur publique reservee au Trigger Area Configurator du meme marker ; aucune action ; detection serveur de
 *     l'entree et de la sortie des entites dans la zone ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.triggerarea.TriggerAreaZone} : pave {@code World}, editable par les
 *     operateurs (translation, rotation, echelle par axe) ; le serveur teste les entites contre ce meme pave.</li>
 * </ul>
 */
package com.amynna.hololib.modules.monde.triggerarea;
