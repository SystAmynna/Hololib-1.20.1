/**
 * Modules de demonstration, enregistres comme le ferait un mod tiers (un par compatibilite, plus l'interaction et le
 * clavier) :
 * <ul>
 *     <li>{@code hololib:demo_dimension} : une fenetre dans le ciel, editable par les operateurs ;</li>
 *     <li>{@code hololib:demo_world_pos} : un cube qui tourne au-dessus du marker ({@link
 *     com.amynna.hololib.demo.DemoCube}, dessine par {@code client.render.demo.DemoCubeRenderer}) ;</li>
 *     <li>{@code hololib:demo_editable_cube} : un cube au-dessus du marker, entierement editable avec le World Editor
 *     par le joueur qui a installe le module ;</li>
 *     <li>{@code hololib:demo_player} : trois fenetres (HUD, camera normale, camera follow) et un cube au-dessus de
 *     la tete du joueur, visibles des autres joueurs qui l'inspectent ({@code /hololib_inspect}), plus une fenetre du
 *     mode focus ({@code FOCUS}, replacee a chaque ouverture) ; tout est editable en jeu
 *     ({@code hololib:own_editor}) ;</li>
 *     <li>{@code hololib:demo_interaction} : une fenetre interactive au regard construite avec les widgets generiques
 *     ({@code holo.widget.control} : bouton, multi-bouton, zones de survol et de molette, boite a souris, barre de
 *     valeur, pastille a glisser, interrupteur d'acces) et une animation, voir
 *     {@link com.amynna.hololib.demo.DemoInteractionModule} ;</li>
 *     <li>{@code hololib:demo_keyboard} : un pave ou chaque joueur pilote son point avec les controles de deplacement
 *     captures (une {@code HoloWidgetKeyBox} multijoueur) et une zone de texte qui capture tout le clavier, chacun
 *     avec un interrupteur d'ouverture a tous, voir {@link com.amynna.hololib.demo.DemoKeyboardModule} ;</li>
 *     <li>{@code hololib:demo_configurable} : une jauge au HUD reglee depuis sa fenetre de configuration, ouverte par
 *     le module Config ({@code hololib:config}), voir {@link com.amynna.hololib.demo.DemoConfigurableModule}.</li>
 * </ul>
 * Les fenetres ({@link com.amynna.hololib.demo.DemoEditableWindow}) et les cubes implementent toutes les interfaces
 * d'edition. A installer avec {@code /hololib_module}.
 */
package com.amynna.hololib.demo;
