/**
 * Module {@code hololib:config} ({@link com.amynna.hololib.modules.camera.config.ConfigModule}) : configuration en jeu
 * des autres modules de la memoire du joueur, en mode focus.
 * <ul>
 *     <li>Bouton ecrou ({@link com.amynna.hololib.modules.camera.config.ConfigNutWindow}) : place a l'ouverture du
 *     focus, affichage non modifiable ; un clic fait tourner l'ecrou et ouvre / ferme la liste.</li>
 *     <li>Liste ({@link com.amynna.hololib.modules.camera.config.ConfigListWindow}) des modules qui implementent
 *     {@link com.amynna.hololib.modules.camera.config.Configurable} : un clic ouvre la fenetre de configuration que le
 *     module detient.</li>
 *     <li>Liste et fenetres de configuration sont des {@link com.amynna.hololib.modules.camera.config.ConfigWindow} :
 *     croix (fermer) et epingle ; ouvertes devant le regard au clic, fermees avec le focus sauf si elles sont
 *     epinglees (rouvertes alors avec lui) ; deplacables en les saisissant par le cadre (position relative au regard
 *     memorisee).</li>
 * </ul>
 * <b>Rendre un module configurable</b> : implementer {@code Configurable} et creer sa {@code ConfigWindow} dans
 * {@code onLoad} (voir {@code demo.DemoConfigurableModule}). Les icones (ecrou, epingle, croix) sont dessinees en
 * quadrilateres ({@code HoloCanvas.quad}) ; logique client : {@code client.modules.camera.config.ConfigController}.
 */
package com.amynna.hololib.modules.camera.config;
