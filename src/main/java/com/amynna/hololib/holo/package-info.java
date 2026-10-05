/**
 * Modele holographique <b>commun</b> au client et au serveur : aucune classe client ici (le serveur dedie charge ce
 * code). Le dessin vit dans {@code client.render.holo}.
 * <ul>
 *     <li>{@code object} : {@link com.amynna.hololib.holo.object.HoloObject} (ce qui se dessine) et
 *     {@link com.amynna.hololib.holo.object.HoloAnchor} (ou, donc par quelle famille de renderers) et
 *     {@link com.amynna.hololib.holo.object.HoloTransform} (placement dans le repere de l'ancrage).</li>
 *     <li>{@code window} / {@code widget} : fenetres et leur arborescence de widgets (espace 2D en pixels).</li>
 *     <li>{@code module} : {@link com.amynna.hololib.holo.module.HoloModule}, etat + logique + objets, present
 *     des deux cotes ; registre Forge des types.</li>
 *     <li>{@code memory} : memoires (joueur, dimension, position) et leurs conteneurs vivants cote serveur.</li>
 *     <li>{@code inspect} : acces d'inspection entre joueurs (qui voit les affichages de qui).</li>
 * </ul>
 *
 * <h2>Replication</h2>
 * Le serveur fait autorite. On replique l'<b>etat</b> des modules, jamais l'interface : chaque client recree un miroir
 * du module a partir de son type, et le miroir reconstruit ses objets. Le framework synchronise lui-meme l'ancrage, le
 * transform et la visibilite de chaque objet. Seuls les modules ayant un objet {@code PUBLIC} sont envoyes aux
 * observateurs (joueurs de la dimension, joueurs qui suivent le chunk, et pour une memoire de joueur, les joueurs qui
 * suivent son entite <b>et</b> l'inspectent : par defaut personne) ; un ancrage {@code Local} d'un autre joueur est vu
 * autour de sa tete, son {@code Hud} jamais.
 */
package com.amynna.hololib.holo;
