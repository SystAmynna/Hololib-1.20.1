/**
 * Capture de controles, partie <b>commune</b> (aucune classe client), independante de l'interaction au regard.
 * <ul>
 *     <li>{@link com.amynna.hololib.holo.capture.HoloControl} : controle capturable (boutons de souris bruts,
 *     molette, mouvements de souris, Echap, Entree, n'importe quel {@code KeyMapping} par son nom, tout le clavier
 *     avec le texte tape) ; {@link com.amynna.hololib.holo.capture.HoloKeys} : codes de touches.</li>
 *     <li>{@link com.amynna.hololib.holo.capture.HoloCaptor} : recoit les controles qu'il capture, a la place du
 *     jeu.</li>
 *     <li>{@link com.amynna.hololib.holo.capture.HoloCapture} : contexte des callbacks (regard, liberation).</li>
 *     <li>{@link com.amynna.hololib.holo.capture.HoloCaptures} : debut / fin d'une capture explicite.</li>
 * </ul>
 * Le controleur client ({@code client.capture.HoloControlCapture}) est le seul abonne du mod aux inputs : pile de
 * captures explicites (la plus recente d'abord), puis captors passifs permanents (l'interaction au regard,
 * {@code holo.interact}, en est un). Un controle que personne ne capture va au jeu.
 */
package com.amynna.hololib.holo.capture;
