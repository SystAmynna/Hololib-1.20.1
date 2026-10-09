/**
 * Dessin du module {@code hololib:space_environment} ({@code com.amynna.hololib.modules.dimension.space}) : un
 * {@code SkyRenderer} bas niveau ({@link com.amynna.hololib.client.render.modules.dimension.space.SpaceSkyRenderer})
 * remplace visuellement le ciel vanilla par le systeme courant vu depuis la pose du monde, et met le brouillard a une
 * couleur spatiale quasi noire. Le module n'a aucun objet holographique : rien a router, rien a editer.
 * <p>
 * Regles :
 * <ul>
 *     <li>rien n'est dessine sans Environment synchronise dans le miroir de dimension (ciel vanilla intact) ;</li>
 *     <li>pose et positions des corps au temps d'affichage du module ({@code pose(partialTick, dest)},
 *     {@code bodyPositions}), jamais au temps de jeu de Minecraft ; la pose est interpolee dans le repere du
 *     referentiel (pas de saccade pres d'une lune rapide, {@code docs/space/05-synchronisation.md} par. 2) ;</li>
 *     <li>rayons de dessin : fond 120 blocs, etoiles de fond 110, corps de 105 (le plus lointain) a 50 (le plus
 *     proche), tous sous le plan lointain (128 au minimum) ; un corps peut couvrir jusqu'a 90 degres de rayon
 *     angulaire (demi-sphere de ciel au plancher d'altitude, D9) ;</li>
 *     <li>disques des corps en maillages polaires precalcules a quatre niveaux de detail (24 x 6 a 192 x 48), choisis
 *     par la taille a l'ecran avec hysteresis ; normale de sphere exacte (perspective) pour les phases ;</li>
 *     <li>aucune allocation par frame, seulement {@code HoloRenderTypes} (aucun etat GL modifie).</li>
 * </ul>
 * Conception : {@code docs/space/04-mathematiques.md} par. 4, carte {@code docs/space/taches/T05-rendu-ciel.md}.
 * Enregistrement : {@link com.amynna.hololib.client.render.modules.dimension.space.SpaceRenderers}, appele par
 * {@code ModuleRenderers}.
 */
package com.amynna.hololib.client.render.modules.dimension.space;
