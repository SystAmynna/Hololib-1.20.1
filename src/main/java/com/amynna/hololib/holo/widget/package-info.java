/**
 * Widgets des fenetres : {@link com.amynna.hololib.holo.widget.HoloWidget} (base),
 * {@link com.amynna.hololib.holo.widget.HoloPanel} (conteneur), {@link com.amynna.hololib.holo.widget.HoloLabel}
 * (texte). Ils dessinent via {@link com.amynna.hololib.holo.widget.HoloCanvas}, interface commune implementee cote
 * client : un widget s'ecrit sans classe client ni renderer dedie. L'etat d'un widget ne circule jamais sur le reseau.
 * {@link com.amynna.hololib.holo.widget.HoloTextMetrics} mesure le texte hors du dessin.
 * <p>
 * Un widget interactif implemente {@link com.amynna.hololib.holo.interact.HoloInteractible} : la fenetre le trouve
 * sous le regard ({@code HoloWindow.interactibleAt}) et les controles qu'il n'intercepte pas remontent a ses parents.
 * Les comportements courants (survol, clic, molette, glisser, saisie...) et les widgets prets a l'emploi sont dans
 * {@code holo.widget.control}.
 */
package com.amynna.hololib.holo.widget;
