/**
 * Renderers de l'Area Displayer ({@code modules.monde.areadisplayer}) :
 * {@link com.amynna.hololib.client.render.modules.monde.areadisplayer.AreaDisplayerCubeRenderer} (cadre, terrain
 * monochrome du maillage glouton par tuiles garde sur le GPU par {@code AreaTileGpu} (tout l'hologramme, option Culling : une seule couche par pre-passe de profondeur), points suivis et noms au premier plan, trainees, etat du scan), enregistre par
 * {@link com.amynna.hololib.client.render.modules.monde.areadisplayer.AreaDisplayerRenderers}. Donnees lues dans
 * {@code client.modules.monde.areadisplayer.AreaTerrainClient}.
 */
package com.amynna.hololib.client.render.modules.monde.areadisplayer;
