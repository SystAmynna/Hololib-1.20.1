package com.amynna.hololib.holo.edit;

/**
 * Outil d'edition : rotation ({@code yaw}, {@code pitch}, {@code roll} du transform).
 * <ul>
 *     <li>{@code Local} et {@code Sky} : clic gauche maintenu, la souris (camera figee) fait basculer l'objet
 *     gauche / droite, haut / bas ; molette = rotation autour de l'axe du regard.</li>
 *     <li>{@code World} : trois cercles X / Y / Z autour de l'objet, a attraper et faire tourner.</li>
 *     <li>{@code Hud} : molette = rotation dans le plan de l'ecran.</li>
 * </ul>
 */
public interface HoloEditRotation extends HoloEditable
{
}
