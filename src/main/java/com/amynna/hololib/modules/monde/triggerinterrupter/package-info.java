/**
 * Module {@code hololib:trigger_interrupter} (memoire de position, marker), visible et utilisable par tous (sauf « OP
 * requis ») : fenetre avec un titre, un bouton et un texte facultatif, qui execute des commandes serveur.
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.monde.triggerinterrupter.TriggerInterrupterModule} : seule autorite sur
 *     l'etat (textes, « OP requis », onze listes de commandes), sauvegarde et synchronise ; actions appui / relachement /
 *     battement / molette, maintien libere d'office s'il se tait ; API serveur publique reservee au Trigger Interrupter
 *     Configurator du meme marker ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.triggerinterrupter.TriggerInterrupterWindow} : la fenetre
 *     ({@code World}, editable par les operateurs) ;</li>
 *     <li>{@code TriggerButton} : le bouton (trois clics et molette, battement du maintien).</li>
 * </ul>
 */
package com.amynna.hololib.modules.monde.triggerinterrupter;
