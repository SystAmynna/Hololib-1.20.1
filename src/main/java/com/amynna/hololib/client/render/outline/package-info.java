/**
 * Contours facon glowing, partages par les renderers des modules (radar, ciblage), sans Mixin ni glowing vanilla (le
 * drapeau glowing est synchronise par le serveur et colore par l'equipe : il ne peut etre force cote client).
 * <ul>
 *     <li>{@link com.amynna.hololib.client.render.outline.HoloOutlineMask} : cibles hors ecran (copie de la profondeur
 *     de la scene, masque) et {@code MultiBufferSource} du masque : entites redessinees avec leurs types de contour
 *     vanilla rediriges, boites pleines (silhouette d'un bloc) ;</li>
 *     <li>{@link com.amynna.hololib.client.render.outline.HoloOutlineShader} : shader {@code hololib:outline_composite}
 *     ({@code RegisterShadersEvent}) et composition plein ecran du contour seul, visible a travers les murs.</li>
 * </ul>
 * Usage dans un {@code WorldRenderer} : envoyer le lot en attente, {@code prepare}, {@code clearMask}, remplir le
 * masque, puis {@code HoloOutlineShader.compose}. Une instance de masque par renderer ; fil de rendu.
 */
package com.amynna.hololib.client.render.outline;
