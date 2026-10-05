package com.amynna.hololib.holo.capture;

/**
 * Recoit des controles du joueur a la place du jeu. Une capture <b>explicite</b> commence par
 * {@link HoloCaptures#begin} et dure jusqu'a {@link HoloCapture#release()} ou {@link HoloCaptures#end} : elle ne se
 * termine jamais d'elle-meme (penser a laisser une sortie, ex. capturer Echap). Le framework utilise aussi des captors
 * <b>passifs</b> permanents (l'interaction au regard), consultes apres les captures explicites.
 * <p>
 * Pour chaque controle, le captor le plus recent qui le {@linkplain #captures capture} le recoit ; le jeu ne le voit
 * pas. Callbacks cote client, sur le fil client, jamais pendant le rendu, et seulement quand aucun ecran n'est ouvert
 * (la capture reste active sous un ecran, en pause). Le contexte est reutilise : ne pas le garder.
 */
public interface HoloCaptor
{
    /** Vrai si ce captor prend {@code control} (consulte a chaque input : peut varier). */
    boolean captures(HoloControl control);

    /** Debut d'une capture explicite. */
    default void onCaptureStart(HoloCapture ctx) {}

    /** Fin d'une capture explicite. */
    default void onCaptureEnd(HoloCapture ctx) {}

    /** Appui d'un bouton, d'une touche ou d'un mapping capture. */
    default void onPress(HoloCapture ctx, HoloControl control) {}

    /** Relachement d'un appui pris par ce captor (meme s'il ne capture plus ce controle). */
    default void onRelease(HoloCapture ctx, HoloControl control) {}

    /** Cran(s) de molette : positif vers le haut. */
    default void onScroll(HoloCapture ctx, double delta) {}

    /**
     * Chaque frame tant que ce captor prend {@link HoloControl#MOUSE_MOVE} : deplacement de la souris en degres de
     * rotation (sensibilite et inversion appliquees, comme la camera l'aurait fait), eventuellement nul. La camera ne
     * tourne pas ; {@link HoloCapture#setLook} l'oriente.
     */
    default void onMouseMove(HoloCapture ctx, float dYaw, float dPitch) {}
}
