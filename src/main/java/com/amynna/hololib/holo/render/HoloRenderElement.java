package com.amynna.hololib.holo.render;

/**
 * Element du rendu vanilla qu'un module peut annuler par {@link HoloRenderCanceler}. Annule = non dessine chez le
 * client local, rien d'autre ne change (logique, sons, brouillard).
 */
public enum HoloRenderElement
{
    /**
     * Ciel : fond, lueur du lever et du coucher, soleil, lune, etoiles, disque sombre sous l'horizon. Le fond de l'ecran
     * prend la couleur du brouillard (y compris avec un shaderpack), privee de la lueur du lever et du coucher : elle ne
     * depend pas du regard. Les {@code SkyRenderer} du mod restent dessines.
     */
    SKY,
    /** Nuages vanilla (ceux calcules par un shaderpack dans ses passes composite restent). */
    CLOUDS,
    /** Pluie et neige dessinees ; eclaboussures et son de la pluie restent. */
    WEATHER,
    /** Main du joueur en premiere personne (les deux mains, objets tenus compris). */
    HAND,
    /** Reticule au centre de l'ecran (l'interaction au regard n'est pas affectee). */
    CROSSHAIR,
    /**
     * Bloc d'inventaire du bas de l'ecran : barre d'inventaire, nom de l'objet tenu, vie, armure, faim, air, experience,
     * vie de la monture et barre de saut.
     */
    INVENTORY_HUD
}
