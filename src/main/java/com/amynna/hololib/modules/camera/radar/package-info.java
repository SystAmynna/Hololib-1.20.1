/**
 * Module {@code hololib:radar} (memoire de joueur) : radar a onde du joueur local.
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.camera.radar.RadarModule} : reglages sauvegardes et synchronises
 *     (epaisseur de mur traversable ou infinie, rayon, joueurs, autres entites), modifies par {@code sendAction} et
 *     valides par le serveur ; presence de Quick Action dans la memoire (calculee par le serveur) ; compatible Config
 *     ({@code Configurable}) et Quick Action ({@code QuickActionProvider} : actions {@code activate} et
 *     {@code deactivate}) ; pont {@code Backend} vers le radar local ;</li>
 *     <li>{@link com.amynna.hololib.modules.camera.radar.RadarWindow} : fenetre de declenchement en camera normale
 *     ({@code Local} relatif, {@code PUBLIC}, editable), Activer / Desactiver et etat ; retiree quand Quick Action est
 *     present ;</li>
 *     <li>{@link com.amynna.hololib.modules.camera.radar.RadarConfigWindow} : fenetre de Config « Radar ».</li>
 * </ul>
 * L'onde et les reperages sont purement clients et vus du seul proprietaire : etat et detection dans
 * {@code client.modules.camera.radar}, dessin dans {@code client.render.modules.camera.radar}. Code commun : aucune
 * classe client.
 */
package com.amynna.hololib.modules.camera.radar;
