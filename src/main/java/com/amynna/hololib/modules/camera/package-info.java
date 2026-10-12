/**
 * Modules de joueur ({@code HoloModuleTarget.PLAYER}) : dans la memoire active d'un joueur, autour de sa camera.
 * <ul>
 *     <li>{@code focus} : mode focus (touche H), revele les objets {@code HoloDisplay.FOCUS}.</li>
 *     <li>{@code editor} : modules editeurs (touche G), session d'edition, et
 *     activation des editeurs (fenetre "Editeurs" de Config).</li>
 *     <li>{@code config} : en mode focus, bouton ecrou et liste des modules {@code Configurable}.</li>
 *     <li>{@code quickaction} : en mode focus, roue d'actions au centre de l'ecran, actions declarees par les modules
 *     {@code QuickActionProvider}.</li>
 *     <li>{@code crosshaircancel} : reticule du joueur annule (demonstration du RenderCanceler).</li>
 *     <li>{@code radar} : onde de detection du joueur local, entites reperees a travers les murs (Config, Quick
 *     Action, visables par le ciblage).</li>
 *     <li>{@code targeting} : cible (entite ou bloc) choisie a l'ouverture du focus, gardee pendant le focus, contour
 *     vert et segment.</li>
 *     <li>{@code keys} : cles du joueur, interrogeables par les modules d'autres joueurs (serveur) ;
 *     {@code keyseditor} : edition des cles (Config : soi-meme, ou joueur cible pour les ops).</li>
 *     <li>{@code attackguard} : pas d'attaque sur un joueur qui possede une cle de la whitelist (Config), croix
 *     rouge a la place du reticule.</li>
 *     <li>{@code themeconfigurator} : themes du joueur ({@code theme_configurator}) et du serveur
 *     ({@code admin_theme_configurator}, ops), fenetre de Config et apercu.</li>
 * </ul>
 * Focus et edition ne sont jamais ouverts ensemble. Leur logique client (touches, conditions de sortie, repere de
 * l'editeur) vit dans {@code com.amynna.hololib.client.modules.camera}.
 */
package com.amynna.hololib.modules.camera;
