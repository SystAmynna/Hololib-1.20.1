/**
 * Modules de demonstration, enregistres comme le ferait un mod tiers (un par compatibilite, plus l'interaction) :
 * <ul>
 *     <li>{@code hololib:demo_dimension} : une fenetre dans le ciel ;</li>
 *     <li>{@code hololib:demo_world_pos} : un cube qui tourne au-dessus du marker ({@link
 *     com.amynna.hololib.demo.DemoCube}, dessine par {@code client.render.demo.DemoCubeRenderer}) ;</li>
 *     <li>{@code hololib:demo_player} : trois fenetres (HUD, camera normale, camera follow) et un cube au-dessus de
 *     la tete du joueur, visibles des autres joueurs qui l'inspectent ({@code /hololib_inspect}) ;</li>
 *     <li>{@code hololib:demo_interaction} : une fenetre interactive au regard (boutons gauche / droit / molette,
 *     zones molette, survol et capture de la souris, animation, bascule du droit d'usage), voir
 *     {@link com.amynna.hololib.demo.DemoInteractionModule}.</li>
 * </ul>
 * A installer avec {@code /hololib_module}.
 */
package com.amynna.hololib.demo;
