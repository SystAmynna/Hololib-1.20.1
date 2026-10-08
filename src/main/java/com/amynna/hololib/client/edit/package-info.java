/**
 * Session d'edition du joueur local (client) : {@link com.amynna.hololib.client.edit.HoloEditSession} (cycle de vie,
 * objets tenus, envoi au serveur, objets HUD du cadre), {@link com.amynna.hololib.client.edit.HoloEditTools} (outil
 * actif, poignees, gizmos, glisser / molette) et les boutons de chaque objet edite
 * ({@code HoloEditTarget} : {@code HoloEditPanel} a droite, boutons d'outils a icone {@code HoloEditToolButton} et
 * "+" qui ouvre les valeurs d'une ligne ; {@code HoloEditEyeButton} au-dessus), overlays clients routes a cote de
 * leur hote ({@code client.render.holo.HoloAttachment}). Saisir un objet pour l'editer joue le son d'edition
 * ({@code client.interact.HoloSounds}). Ouverte depuis le focus, la session laisse en place les
 * {@code HoloRelativeToOpen} qu'il a places ({@code open(..., placements)}).
 * <p>
 * Branchements : {@code HoloRouting} (affichage revele, HUD suspendu), {@code HoloPicking} (poignee de l'outil,
 * gizmos), {@code ClientHoloContainer} (placement recu ignore pour un objet tenu). Le dessin des gizmos est dans
 * {@code client.render.edit}.
 */
package com.amynna.hololib.client.edit;
