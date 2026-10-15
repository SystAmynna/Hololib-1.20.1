/**
 * Module {@code hololib:area_displayer} (memoire de position, marker) : pave holographique du terrain d'une zone
 * (coins A et B), avec les joueurs et les entites vivantes qui s'y trouvent. Visible par tous ; reglages par l'Area
 * Displayer Configurator du meme marker ({@code modules.monde.areaconfigurator}, operateurs).
 * <ul>
 *     <li>{@link com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerModule} : coins, options, intervalle de
 *     l'Auto Update, resolution, liste noire de blocs ignores, generation, avancement (sauvegardes et synchronises) ; voxels sauvegardes (format creux, deflates) ; API
 *     serveur du Configurator ; recherche par marker ({@code of}, table par cote) ; backend client (abonnement au
 *     terrain) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerCube} : pave {@code World} editable par
 *     les operateurs, proportions de la zone, echelle uniforme ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.areadisplayer.AreaGrid} : grille deterministe (resolution = palier
 *     1:1 a 1:32 appliquee strictement, taille de zone illimitee), sections de {@code 16^3}, tuiles de {@code 4^3}
 *     sections, cles {@code long}, empaquetage 2 bits ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.areadisplayer.AreaVoxels} : voxels en stockage creux (sections non
 *     vides seulement), serveur et client ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.areadisplayer.AreaScanner} : calcul serveur des voxels par colonnes
 *     de sections, budget de temps par tick, chunks charges a la demande sans generation (passage complet) ou deja
 *     charges seulement (Auto Update : rescan periodique, jamais en temps reel) ;</li>
 *     <li>{@link com.amynna.hololib.modules.monde.areadisplayer.AreaFeed} : abonnes (joueurs qui suivent le chunk du
 *     marker), envoi des sections changees et des positions suivies par le canal dedie.</li>
 * </ul>
 * Reseau : {@code terrain} (canal {@code hololib:area_terrain}). Code commun : aucune classe client. Client :
 * {@code client.modules.monde.areadisplayer} ; dessin : {@code client.render.modules.monde.areadisplayer}.
 */
package com.amynna.hololib.modules.monde.areadisplayer;
