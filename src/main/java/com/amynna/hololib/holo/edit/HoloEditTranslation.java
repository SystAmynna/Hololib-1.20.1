package com.amynna.hololib.holo.edit;

/**
 * Outil d'edition : deplacement ({@code x}, {@code y}, {@code z} du transform, interpretes selon l'ancrage).
 * <ul>
 *     <li>{@code Local} et {@code Sky} : clic gauche maintenu, l'objet suit le regard ; molette = distance.</li>
 *     <li>{@code World} : trois segments X / Y / Z a travers l'objet, a attraper et faire glisser.</li>
 *     <li>{@code Hud} : glisser dans le cadre de la fenetre de l'editor ; molette = ordre de dessin.</li>
 * </ul>
 */
public interface HoloEditTranslation extends HoloEditable
{
}
