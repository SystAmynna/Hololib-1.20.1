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
 * </ul>
 * Focus et edition ne sont jamais ouverts ensemble. Leur logique client (touches, conditions de sortie, repere de
 * l'editeur) vit dans {@code com.amynna.hololib.client.modules.camera}.
 */
package com.amynna.hololib.modules.camera;
