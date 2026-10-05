/**
 * Capture de controles, partie <b>client</b> : {@link com.amynna.hololib.client.capture.HoloControlCapture}, seul
 * abonne du mod aux inputs (boutons, molette, mouvements de souris, touches, mappings, ouverture du menu pause), et
 * {@link com.amynna.hololib.client.capture.HoloKeyboardScreen}, ecran invisible qui porte la capture de tout le clavier
 * (texte compris). Il
 * distribue chaque controle au captor qui le prend ({@code holo.capture.HoloCaptor} : captures explicites, puis
 * captors passifs) et empeche le jeu de le voir. Independant de l'interaction au regard, qui n'en est qu'un client
 * (captor passif de {@code client.interact}).
 */
package com.amynna.hololib.client.capture;
