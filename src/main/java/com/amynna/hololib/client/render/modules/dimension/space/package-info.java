/**
 * Dessin du module {@code hololib:space_environment} ({@code com.amynna.hololib.modules.dimension.space}) : un
 * {@code SkyRenderer} bas niveau ({@link com.amynna.hololib.client.render.modules.dimension.space.SpaceSkyRenderer})
 * remplace visuellement le ciel vanilla par le systeme courant vu depuis la pose du monde, et met le brouillard a une
 * couleur spatiale quasi noire. Le module n'a aucun objet holographique : rien a router, rien a editer.
 * <p>
 * Distorsion du saut interstellaire (T35, D23, C73) : sous-package {@code warp} (chronologie
 * {@code SpaceWarpTimeline}, convergence des etoiles placee par le ciel, scene du voyage et flashs dessines par
 * {@code SpaceWarpEffect}, appele par {@code SpaceSkyRenderer}) ; la surcouche de Space Knowledge, le saut et le lien
 * de Space Pilot lisent seulement {@code SpaceWarpTimeline.active} pour se couper.
 * <p>
 * Regles de dessin partagees par ces surcouches du joueur local (T33) :
 * {@link com.amynna.hololib.client.render.modules.dimension.space.SpaceOverlay} (pixel de reference, rayon et couleur
 * de l'anneau d'un corps, {@code RenderType} translucide sans ecriture de profondeur).
 * <p>
 * Textures des astres (T30) : {@link com.amynna.hololib.client.render.modules.dimension.space.SpaceBodyTextureCache}
 * (instance partagee, aussi pour la Space Map) resout la reference d'un corps en texture prete ; chargement a la demande
 * (decodage et mipmaps hors du fil de rendu, televersement par {@code beginFrame}), liberation apres une minute sans
 * demande, point d'entree des textures importees (T45 : image enregistree par empreinte, demandee au serveur par
 * {@code client.modules.dimension.space.SpaceTextureClient} tant qu'elle manque, liberee a la deconnexion). Le ciel demande les textures de tous les corps du systeme
 * courant et texture les disques qui en ont une (rotation propre au temps d'affichage).
 * <p>
 * Regles :
 * <ul>
 *     <li>rien n'est dessine sans Environment synchronise dans le miroir de dimension (ciel vanilla intact) ;</li>
 *     <li>pose et positions des corps au temps d'affichage du module ({@code pose(partialTick, dest)},
 *     {@code bodyPositions}), jamais au temps de jeu de Minecraft ; la pose est interpolee dans le repere du
 *     referentiel (pas de saccade pres d'une lune rapide, {@code docs/space/05-synchronisation.md} par. 2) ;</li>
 *     <li>rayons de dessin : fond 120 blocs, fond etoile procedural 112,5 (T31 : graine de l'Environment, quads
 *     precalcules par graine dans les axes du systeme, une matrice par frame), etoiles de fond (autres systemes) 110,
 *     corps de 105 (le plus lointain) a 50 (le plus
 *     proche), tous sous le plan lointain (128 au minimum) ; un corps peut couvrir jusqu'a 90 degres de rayon
 *     angulaire (demi-sphere de ciel au plancher d'altitude, D9) ;</li>
 *     <li>disques des corps en maillages polaires precalcules a quatre niveaux de detail (24 x 6 a 192 x 48), choisis
 *     par la taille a l'ecran avec hysteresis ; normale de sphere exacte (perspective) pour les phases et pour les
 *     coordonnees de texture (rotation propre, {@code SpaceSpin}) ; points lointains jamais textures ;</li>
 *     <li>temps d'animation de la distorsion : horloge d'affichage du module ({@code displayTicks}) et compte du
 *     saut, jamais le temps de jeu (C20) ;</li>
 *     <li>aucune allocation par frame, seulement des {@code RenderType} ({@code HoloRenderTypes} ; un type local
 *     translucide et un additif sans ecriture de profondeur pour la distorsion ; un type local texture par texture
 *     d'astre, {@code POSITION_COLOR_TEX}), aucun etat GL modifie a la main (les parametres d'une texture d'astre
 *     sont poses a son televersement, sur sa propre texture).</li>
 * </ul>
 * Conception : {@code docs/space/04-mathematiques.md} par. 4, 8 et 10, cartes
 * {@code docs/space/suivi.md} (T30, T31 ; cartes des lots 1 et 2, dont T05 et T23, dans l'historique git).
 * Enregistrement : {@link com.amynna.hololib.client.render.modules.dimension.space.SpaceRenderers}, appele par
 * {@code ModuleRenderers}.
 */
package com.amynna.hololib.client.render.modules.dimension.space;
