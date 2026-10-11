/**
 * Cote client des modules Area Displayer ({@code modules.monde.areadisplayer}) :
 * <ul>
 *     <li>{@link com.amynna.hololib.client.modules.monde.areadisplayer.AreaDisplayerClient} : backend du module
 *     (abonnement du miroir au terrain), installe au setup client ;</li>
 *     <li>{@link com.amynna.hololib.client.modules.monde.areadisplayer.AreaTerrainClient} : handlers du canal
 *     {@code hololib:area_terrain}, vues par marker, remaillage borne par tick, horloge des trainees, oubli a la
 *     deconnexion ;</li>
 *     <li>{@link com.amynna.hololib.client.modules.monde.areadisplayer.AreaView} : voxels, maillage par section,
 *     positions suivies (interpolees sur 2 ticks) et trainees (64 points, 5 s), lus par le renderer du pave ;</li>
 *     <li>{@link com.amynna.hololib.client.modules.monde.areadisplayer.AreaMesher} : maillage glouton d'une section
 *     (faces visibles fusionnees par direction et par type, ordre anti-horaire vu de l'exterieur).</li>
 * </ul>
 * Dessin : {@code client.render.modules.monde.areadisplayer}.
 */
package com.amynna.hololib.client.modules.monde.areadisplayer;
