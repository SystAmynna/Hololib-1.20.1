/**
 * Module {@code hololib:space_configurator} (memoire de position, marker), reserve aux operateurs : edition de la
 * galaxie du Space Environment de la dimension du marker ({@code modules.dimension.space}). Depuis T42 (D20, C78), il
 * n'a plus de carte : il se branche sur la <b>Space Map du meme marker</b> ({@code modules.monde.spacemap}), dont la
 * selection est l'objet edite et dont le cube et la vue portent les poignees. Il ne porte qu'un etat d'interface
 * (temps fige de la poignee d'orbite, copie des reglages de l'Environment) ; l'Environment reste la seule autorite,
 * modifiee par ses methodes serveur. Conception : {@code docs/space/02-architecture.md} par. 7,
 * {@code 04-mathematiques.md} par. 7, {@code 05-synchronisation.md} par. 4.
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.monde.spaceconfigurator.SpaceConfiguratorModule} : recherche de la Map du
 *     marker ({@code SpaceMapModule.of}, deux cotes, sans allocation ; sans Map : « Space Map requise sur ce marker »,
 *     edition desactivee, aucune poignee) ; actions verifiees cote serveur (operateur d'abord, puis source, Map et
 *     Environment ; ids vises portes par l'action) ; sondage des poignees (deplacement d'une etoile, inclinaison et
 *     forme d'une orbite) converti avec le contexte de leur derniere ecriture ; temps fige {@code t_edit} de la
 *     poignee d'orbite, repris au changement de contexte seulement ; copie des reglages (avant du monde, distorsion,
 *     echelle de temps) ; acces de la fenetre = operateurs connectes ; backend client (joueur local operateur) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spaceconfigurator.SpaceConfiguratorWindow} : fenetre {@code World}
 *     editable seule par les operateurs (translation, rotation, bouton oeil), placee par defaut a droite de la fenetre
 *     de la Map ; carte galactique de la Map : nom du systeme selectionne, ajouter, retirer (confirmation), monde ici,
 *     avant du monde, echelle de temps (C119), vitesse de distorsion ; vue systeme de la Map : corps selectionne
 *     (nom, ajout d'etoile / planete / lune / objet mineur ; onglets, T44 : « Corps » = retrait, « Orbite », rayon,
 *     luminosite, masse, periode, couleur, phase, lecture de l'orbite ; « Rotation » = periode de rotation, obliquite,
 *     noeud, phase de rotation ; « Texture » = apercu, liste « Auto » + textures integrees, « Importer » (T45) ;
 *     D34 : « RESET » du corps et « Se teleporter ») ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spaceconfigurator.SpaceMapHandle} : poignee de translation unique
 *     ({@code docs/space/decisions.md} C4), posee sur l'etoile selectionnee de la carte galactique de la Map ; son
 *     deplacement est sonde par le module et converti en {@code moveSystem} ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spaceconfigurator.SpaceOrbitHandle} : poignee d'orbite unique
 *     (outils Rotation et Echelle, C5), posee au temps fige sur le parent affiche du corps selectionne dans la vue
 *     systeme de la Map, si l'orbite y est entiere ; sa rotation et son echelle sont sondees par le module et
 *     converties en orbite ({@code updateBody}).</li>
 * </ul>
 * Regles : fenetre et poignees sont masquees aux non-operateurs ({@code shown()}, {@code docs/space/decisions.md} C9 ;
 * la Map reste visible de tous) et chaque action reverifie {@code hasPermissions(2)} cote serveur, en premier. Code
 * commun : aucune classe client (le client passe par {@code SpaceConfiguratorModule.Backend}). Dessin :
 * {@code client.render.modules.monde.spaceconfigurator}.
 */
package com.amynna.hololib.modules.monde.spaceconfigurator;
