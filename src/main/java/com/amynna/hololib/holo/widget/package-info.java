/**
 * Widgets des fenetres : {@link com.amynna.hololib.holo.widget.HoloWidget} (base),
 * {@link com.amynna.hololib.holo.widget.HoloPanel} (conteneur), label, bouton. Ils dessinent via
 * {@link com.amynna.hololib.holo.widget.HoloCanvas}, interface commune implementee cote client : un widget s'ecrit
 * sans classe client ni renderer dedie. L'etat d'un widget ne circule jamais sur le reseau.
 * <p>
 * Un widget interactif implemente {@link com.amynna.hololib.holo.interact.HoloInteractible} (ex.
 * {@link com.amynna.hololib.holo.widget.HoloButton}) : la fenetre le trouve sous le regard
 * ({@code HoloWindow.interactibleAt}) et les controles qu'il n'intercepte pas remontent a ses parents.
 */
package com.amynna.hololib.holo.widget;
