/**
 * Code commun des modules Trigger (Trigger Area, Trigger Interrupter et leurs Configurators), aucune classe client :
 * {@link com.amynna.hololib.modules.monde.trigger.TriggerCommands} (execution des commandes serveur : permission 2
 * imposee, sortie supprimee, budget par tick ; listes de commandes immuables executees dans l'ordre, au plus 16 par
 * liste), {@link com.amynna.hololib.modules.monde.trigger.CommandListEditor} et
 * {@link com.amynna.hololib.modules.monde.trigger.CommandListGroup} (editeur d'une liste retractable : champ, « + »,
 * « x » par ligne, lignes glissables ; groupe en accordeon, une liste ouverte a la fois) et {@link com.amynna.hololib.modules.monde.trigger.TriggerOps}
 * (operateurs : niveau de permission, operateur local cote client, acces des fenetres reservees aux operateurs).
 */
package com.amynna.hololib.modules.monde.trigger;
