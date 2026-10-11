/**
 * Module {@code hololib:targeting} (Ciblage) : cible du joueur (entite ou bloc) choisie a l'ouverture du focus, gardee
 * tant qu'il est ouvert, a l'usage d'autres modules ({@code TargetingModule.of}, {@code targetPlayer} cote serveur).
 * Requiert {@code hololib:focus}. Compatibilite :
 * {@link com.amynna.hololib.modules.camera.targeting.TargetingThroughWalls} (entites visables a travers les murs,
 * implemente par le radar). Logique client :
 * {@code client.modules.camera.targeting} ; contour vert et segment : {@code client.render.modules.camera.targeting}.
 */
package com.amynna.hololib.modules.camera.targeting;
