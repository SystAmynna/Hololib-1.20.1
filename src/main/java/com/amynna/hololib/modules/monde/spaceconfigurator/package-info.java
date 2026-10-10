/**
 * Module {@code hololib:space_configurator} (memoire de position, marker), reserve aux operateurs : edition de la
 * galaxie du Space Environment de la dimension du marker ({@code modules.dimension.space}). Il ne porte qu'un etat
 * d'interface et une <b>copie</b> des donnees de l'Environment (seule autorite), recopiee quand sa revision change ;
 * les modifications passent par les methodes serveur de l'Environment. Conception : {@code docs/space/02-architecture.md}
 * par. 7, {@code 04-mathematiques.md} par. 7, {@code 05-synchronisation.md} par. 4.
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.monde.spaceconfigurator.SpaceConfiguratorModule} : mode (galaxie /
 *     systeme), selections, cadrage galactique ; copie synchronisee (en-tetes de tous les systemes, systeme edite) ;
 *     actions verifiees cote serveur (operateur d'abord, puis source) ; sondage du transform du cube (pas de callback
 *     d'edition dans le framework) : echelle uniforme imposee, poignees replacees (la fenetre ne suit plus le cube,
 *     T24) ; sondage de la poignee (deplacement d'une etoile) et de la poignee d'orbite (inclinaison, forme) ; temps
 *     fige du mode systeme {@code t_edit} et animation locale ; zoom sur un corps (T25, D17 : etat sauvegarde et
 *     synchronise, action 13) ; origine des coordonnees (T26, C50 : id sauvegarde et synchronise, action 14, defaut
 *     {@code Sol} sinon le premier) ; acces de la fenetre = operateurs connectes ; copie de
 *     la vitesse de distorsion de l'Environment et action qui la regle (T24) ; backend client (joueur local
 *     operateur) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spaceconfigurator.SpaceMapCube} : cube {@code World} editable par
 *     les operateurs (translation, rotation, echelle), repere local {@code [-1, 1]^3} dans lequel le renderer dessine
 *     tout le contenu ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spaceconfigurator.SpaceConfiguratorWindow} : fenetre {@code World}
 *     detachee du cube (T24) : creee devant la base du cube par defaut, puis editable seule par les operateurs
 *     (translation, rotation, bouton oeil : {@code OFF} ne laisse que le cube) ; mode galaxie (T11) : liste des
 *     systemes (origine precedee de « @ »), nom, ajouter, retirer (confirmation), editer, monde ici, recadrer,
 *     « Definir comme origine » et coordonnees de la selection sur la ligne d'information (T26), axe avant du monde,
 *     vitesse de distorsion (T24) ; mode systeme (T12) :
 *     liste des corps, ajout d'etoile / planete / lune / objet mineur (T16), retrait, nom, rayon, luminosite, periode,
 *     couleur, phase, lecture de l'orbite, coordonnees du corps selectionne en UA sur la ligne d'information (T26), « Zoom » / « Dezoom » (T25), « Animer », « Retour » ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spaceconfigurator.SpaceMapHandle} : poignee de translation unique
 *     ({@code docs/space/decisions.md} C4), posee sur l'etoile selectionnee en mode galaxie ; son deplacement est
 *     sonde par le module et converti en {@code moveSystem} ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spaceconfigurator.SpaceOrbitHandle} : poignee d'orbite unique
 *     (outils Rotation et Echelle, C5), posee sur le parent affiche du corps selectionne en mode systeme (en zoom,
 *     seulement si ce parent est le corps zoome ou un de ses descendants) ; sa
 *     rotation et son echelle sont sondees par le module et converties en orbite ({@code updateBody}) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spaceconfigurator.SpaceSystemScale} : echelle du mode systeme
 *     (tout a l'echelle depuis T25, D13 : demi-etendue {@code H_sys} du systeme entier ou {@code H_zoom} d'un zoom sur
 *     un corps, rayons affiches, positions affichees), commune au module et au renderer ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spaceconfigurator.SpaceCoordinates} : origine effective des
 *     coordonnees et format des coordonnees affichees (3 decimales, virgule), communs a la fenetre et au renderer
 *     (T26).</li>
 * </ul>
 * Regles : tout est masque aux non-operateurs ({@code shown()}, {@code docs/space/decisions.md} C9) et chaque action
 * reverifie {@code hasPermissions(2)} cote serveur. Code commun : aucune classe client (le client passe par
 * {@code SpaceConfiguratorModule.Backend}). Dessin : {@code client.render.modules.monde.spaceconfigurator}.
 */
package com.amynna.hololib.modules.monde.spaceconfigurator;
