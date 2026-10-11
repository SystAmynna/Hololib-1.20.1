/**
 * Distorsion du saut interstellaire (T35, D23, C73 ; remplace l'effet provisoire de T23), vue par tous les joueurs de
 * la dimension : chacun la deduit du saut affiche par son miroir de l'Environment ({@code jumpAt},
 * {@code jumpFraction}, {@code displayTicks}), rien de plus sur le reseau.
 * <ol>
 *     <li><b>Depart</b> (charge validee, debut du voyage) : pendant {@code CONVERGE_TICKS} = 20 ticks, toutes les
 *     etoiles du ciel (fond procedural et autres systemes) glissent sur leur grand cercle vers la cible, en accelerant,
 *     puis <b>flash</b> blanc : la distorsion commence ;</li>
 *     <li><b>Voyage</b> : degrade arc-en-ciel de la cible (violet, bleu) vers l'arriere (rouge), grosse source
 *     lumineuse a halos concentriques et deux flares a quatre branches contra-rotatives sur la cible, particules de
 *     vitesse blanches ; brouillard accorde au degrade dans le regard (hors fluides, C23) ;</li>
 *     <li><b>Arrivee</b> : le blanc monte sur les derniers ticks, ciel entierement blanc au changement de systeme, puis
 *     le blanc s'estompe sur le nouveau systeme.</li>
 * </ol>
 * Classes :
 * <ul>
 *     <li>{@link com.amynna.hololib.client.render.modules.dimension.space.warp.SpaceWarpTimeline} (publique) :
 *     chronologie, phase visuelle courante en lecture seule ({@code NONE}, {@code CONVERGE}, {@code WARP},
 *     {@code ARRIVAL} : instants cles des sons de T36), fractions de la convergence, blanc des flashs, direction de la
 *     cible ; {@code active} coupe la surcouche de Space Knowledge et les rendus du saut et du lien de Space
 *     Pilot ;</li>
 *     <li>{@link com.amynna.hololib.client.render.modules.dimension.space.warp.SpaceWarpConvergence} (publique) :
 *     quad d'une etoile en convergence, pour {@code SpaceSkyRenderer} ;</li>
 *     <li>{@link com.amynna.hololib.client.render.modules.dimension.space.warp.SpaceWarpEffect} (publique) : dessin de
 *     la scene du voyage et des flashs par-dessus le ciel, brouillard ; parties internes
 *     {@code SpaceWarpSpectrum} (degrade), {@code SpaceWarpSource} (source, halos, flares), {@code SpaceWarpStreaks}
 *     (particules), {@code SpaceWarpTypes} ({@code RenderType} locaux : translucide et additif, sans ecriture de
 *     profondeur), {@code SpaceWarpVertices} (sommets sans allocation).</li>
 * </ul>
 * Regles : temps d'animation = horloge d'affichage du module et compte du saut, jamais le temps de jeu (C20) ; aucun
 * shader ajoute, aucune allocation par frame, aucun etat GL modifie a la main ; fil client uniquement. Conception :
 * {@code docs/space/04-mathematiques.md} par. 8, {@code docs/space/02-architecture.md} par. 5.
 */
package com.amynna.hololib.client.render.modules.dimension.space.warp;
