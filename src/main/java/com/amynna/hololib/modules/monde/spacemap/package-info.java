/**
 * Module {@code hololib:space_map} (memoire de position, marker ; lot 3, T37, D20, C77) : carte de la galaxie du Space
 * Environment de la dimension du marker ({@code modules.dimension.space}), <b>visible et utilisable par tous</b>, vue
 * partagee par tous les joueurs. Comme le Configurator dont elle est extraite, la Map ne porte qu'un etat de navigation
 * et une <b>copie</b> des donnees de l'Environment (seule autorite), recopiee quand sa revision change. Conception :
 * {@code docs/space/02-architecture.md} par. 7 (Space Map), {@code 04-mathematiques.md} par. 7,
 * {@code 05-synchronisation.md} par. 4.
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.monde.spacemap.SpaceMapModule} : vue partagee (mode, systeme affiche ou
 *     deplie, selection, centre et zoom continu des deux vues, origine des coordonnees), sauvegardee et synchronisee ;
 *     copie de la galaxie (en-tetes de tous les systemes, systeme affiche) ; actions de navigation de tout joueur a
 *     portee ; sondage du cube (echelle uniforme) ; recherche par le Configurator du meme marker
 *     ({@code SpaceMapModule.of}, table des Maps chargees, sans allocation), lecture de sa selection et de sa vue,
 *     vue orientee par ses editions ({@code focusSystem}, {@code selectInView}, {@code frameOrbit}), orbite masquee
 *     sous sa poignee ({@code hideOrbit}) (T42) ; action 8 {@code TARGET} : la selection courante devient (ou cesse
 *     d'etre) la <b>cible du vaisseau</b>, tenue par l'Environment ; la Map retire la cible qu'elle a posee quand sa
 *     selection change (T43, D33) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spacemap.SpaceMapCube} : cube {@code World} editable par les
 *     operateurs (translation, rotation, echelle), repere local {@code [-1, 1]^3} dans lequel le renderer dessine la
 *     carte ; molette = zoom ; porte aussi les poignees du Configurator du meme marker (T42) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spacemap.SpaceMapView} : ce que le renderer du cube lit (implementee
 *     par la Map) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spacemap.SpaceMapWindow} : fenetre de navigation {@code World}
 *     (arborescence de la gravite, zoom, vue d'ensemble, origine), placee par les operateurs, utilisee par tous ;
 *     {@link com.amynna.hololib.modules.monde.spacemap.SpaceMapTreeRow} : ligne de l'arborescence (bouton « CIBLER » /
 *     « DECIBLER » au bout de la ligne selectionnee, T43) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spacemap.SpaceSystemScale} : echelle de la vue systeme (demi-etendues,
 *     cadrage d'un recentrage, positions et rayons affiches), commune a la Map, au Configurator et au renderer ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.spacemap.SpaceCoordinates} : origine effective et format des
 *     coordonnees affichees (3 decimales, virgule).</li>
 * </ul>
 * Code commun : aucune classe client (le temps vivant est lu par {@code SpaceEnvironmentModule.client()}). Dessin :
 * {@code client.render.modules.monde.spacemap}.
 */
package com.amynna.hololib.modules.monde.spacemap;
