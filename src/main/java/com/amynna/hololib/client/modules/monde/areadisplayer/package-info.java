/**
 * Cote client des modules Area Displayer ({@code modules.monde.areadisplayer}) :
 * <ul>
 *     <li>{@link com.amynna.hololib.client.modules.monde.areadisplayer.AreaDisplayerClient} : backend du module
 *     (abonnement du miroir au terrain), installe au setup client ;</li>
 *     <li>{@link com.amynna.hololib.client.modules.monde.areadisplayer.AreaTerrainClient} : handlers du canal
 *     {@code hololib:area_terrain}, vues par marker, tuiles sales soumises au maillage (budget par tick), horloge
 *     des trainees, oubli a la deconnexion ;</li>
 *     <li>{@link com.amynna.hololib.client.modules.monde.areadisplayer.AreaMeshWorker} : fil de maillage des tuiles
 *     (sur une copie, resultats repris au tick client) ;</li>
 *     <li>{@link com.amynna.hololib.client.modules.monde.areadisplayer.AreaView} : voxels (creux), maillage par tuile de 64 voxels,
 *     positions suivies (interpolees sur 2 ticks) et trainees (64 points, 5 s), lus par le renderer du pave ;</li>
 *     <li>{@link com.amynna.hololib.client.modules.monde.areadisplayer.AreaMesher} : maillage glouton d'une tuile
 *     (faces visibles fusionnees sur toute la tuile par direction et par type, ordre anti-horaire vu de
 *     l'exterieur).</li>
 * </ul>
 * Dessin : {@code client.render.modules.monde.areadisplayer}.
 */
package com.amynna.hololib.client.modules.monde.areadisplayer;
