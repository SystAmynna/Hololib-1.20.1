package com.amynna.hololib.holo.interact;

/**
 * Element holographique avec lequel le joueur interagit en le <b>regardant</b> : le point vise est le centre de
 * l'ecran (le reticule). Implemente par un {@code HoloObject} (qui doit alors surcharger
 * {@code HoloObject.interactibleAt}), une {@code HoloWindow} ou un widget.
 * <p>
 * Pour chaque controle ({@link HoloInput}), {@link #intercepts} decide si l'element le prend : intercepte, l'input
 * n'atteint pas le jeu (pas d'attaque, d'utilisation ni de changement d'emplacement) et le callback correspondant est
 * appele ; sinon il remonte au parent interactible (panneaux, puis fenetre), et passe au jeu si personne ne le prend.
 * Le survol ({@link #onHover} / {@link #onHoverRelease}) est toujours signale a l'element vise le plus profond.
 * <p>
 * Callbacks appeles <b>cote client</b>, sur le fil client, jamais pendant le rendu, et seulement pour un joueur
 * autorise ({@code HoloObject.access()}). Pour agir sur le jeu, envoyer une action au module
 * ({@code HoloModule.sendAction(source, ...)}), que le serveur valide. Le contexte {@link HoloInteraction} est
 * reutilise : ne pas le garder au-dela de l'appel.
 */
public interface HoloInteractible
{
    /** Vrai si cet element prend le controle {@code input} quand il est vise. */
    default boolean intercepts(HoloInput input) { return false; }

    /** Le regard entre sur l'element. */
    default void onHover(HoloInteraction ctx) {}

    /** Le regard quitte l'element (ou l'interaction est suspendue : ecran ouvert, objet retire...). */
    default void onHoverRelease(HoloInteraction ctx) {}

    default void onLeftClick(HoloInteraction ctx) {}

    /** Relachement d'un appui intercepte par cet element, meme si le regard est parti. */
    default void onLeftClickRelease(HoloInteraction ctx) {}

    default void onRightClick(HoloInteraction ctx) {}

    default void onRightClickRelease(HoloInteraction ctx) {}

    default void onMiddleClick(HoloInteraction ctx) {}

    default void onMiddleClickRelease(HoloInteraction ctx) {}

    /** Cran(s) de molette : positif vers le haut. */
    default void onScroll(HoloInteraction ctx, double delta) {}

    /** Debut d'une capture de la souris ({@link HoloInput#MOUSE_MOVE} intercepte, element survole). */
    default void onCaptureStart(HoloInteraction ctx) {}

    /**
     * Chaque frame pendant une capture : deplacement de la souris converti en degres de rotation (sensibilite et
     * inversion des options appliquees, comme la camera l'aurait fait), eventuellement nul. Le regard peut etre pilote
     * par {@link HoloInteraction#setLook}.
     */
    default void onMouseMove(HoloInteraction ctx, float dYaw, float dPitch) {}

    /** Fin de la capture : {@link HoloInteraction#releaseMouse()}, ecran ouvert, objet retire. */
    default void onCaptureEnd(HoloInteraction ctx) {}
}
