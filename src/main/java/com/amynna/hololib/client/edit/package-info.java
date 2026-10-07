/**
 * Session d'edition du joueur local (client) : {@link com.amynna.hololib.client.edit.HoloEditSession} (cycle de vie,
 * objets tenus, envoi au serveur, objets HUD du cadre), {@link com.amynna.hololib.client.edit.HoloEditTools} (outil
 * actif, poignees, gizmos, glisser / molette) et les boutons de chaque objet edite
 * ({@code HoloEditTarget} : {@code HoloEditPanel} a droite, {@code HoloEditEyeButton} au-dessus), overlays clients
 * routes a cote de leur hote ({@code client.render.holo.HoloAttachment}).
 * <p>
 * Branchements : {@code HoloRouting} (affichage revele, HUD suspendu), {@code HoloPicking} (poignee de l'outil,
 * gizmos), {@code ClientHoloContainer} (placement recu ignore pour un objet tenu). Le dessin des gizmos est dans
 * {@code client.render.edit}.
 */
package com.amynna.hololib.client.edit;
