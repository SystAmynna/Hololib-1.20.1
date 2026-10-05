/**
 * Modules de demonstration, enregistres comme le ferait un mod tiers (un par compatibilite, plus l'interaction et le
 * clavier) :
 * <ul>
 *     <li>{@code hololib:demo_dimension} : une fenetre dans le ciel ;</li>
 *     <li>{@code hololib:demo_world_pos} : un cube qui tourne au-dessus du marker ({@link
 *     com.amynna.hololib.demo.DemoCube}, dessine par {@code client.render.demo.DemoCubeRenderer}) ;</li>
 *     <li>{@code hololib:demo_player} : trois fenetres (HUD, camera normale, camera follow) et un cube au-dessus de
 *     la tete du joueur, visibles des autres joueurs qui l'inspectent ({@code /hololib_inspect}) ;</li>
 *     <li>{@code hololib:demo_interaction} : une fenetre interactive au regard construite avec les widgets generiques
 *     ({@code holo.widget.control} : bouton, multi-bouton, zones de survol et de molette, boite a souris, barre de
 *     valeur, pastille a glisser, interrupteur d'acces) et une animation, voir
 *     {@link com.amynna.hololib.demo.DemoInteractionModule} ;</li>
 *     <li>{@code hololib:demo_keyboard} : un pave ou chaque joueur pilote son point avec les controles de deplacement
 *     captures (une {@code HoloWidgetKeyBox} multijoueur) et une zone de texte qui capture tout le clavier, chacun
 *     avec un interrupteur d'ouverture a tous, voir {@link com.amynna.hololib.demo.DemoKeyboardModule}.</li>
 * </ul>
 * A installer avec {@code /hololib_module}.
 */
package com.amynna.hololib.demo;
