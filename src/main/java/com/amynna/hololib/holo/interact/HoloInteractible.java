package com.amynna.hololib.holo.interact;

import com.amynna.hololib.holo.capture.HoloControl;

/**
 * Element holographique avec lequel le joueur interagit en le <b>regardant</b> : le point vise est le centre de
 * l'ecran (le reticule). Implemente par un {@code HoloObject} (qui doit alors surcharger
 * {@code HoloObject.interactibleAt}), une {@code HoloWindow} ou un widget.
 * <p>
 * Tant qu'il est vise, {@link #intercepts} decide controle par controle ({@link HoloControl}) s'il le prend :
 * intercepte, le controle n'atteint pas le jeu et le callback correspondant est appele ; sinon il remonte au parent
 * interactible (panneaux, puis fenetre), et passe au jeu si personne ne le prend. {@link HoloControl#MOUSE_MOVE} ne
 * peut pas etre intercepte au survol (il figerait le regard) : pour capturer la souris ou garder des controles au-dela
 * du survol, demarrer une capture explicite ({@link HoloInteraction#capture}). Le survol ({@link #onHover} /
 * {@link #onHoverRelease}) est toujours signale a l'element vise le plus profond.
 * <p>
 * L'interception au survol s'appuie sur la capture de controles ({@code holo.capture}) : l'interaction y est un captor
 * passif, consulte apres les captures explicites. Callbacks appeles <b>cote client</b>, jamais pendant le rendu, et
 * seulement pour un joueur autorise ({@code HoloObject.access()}). Pour agir sur le jeu, envoyer une action au module
 * ({@code HoloModule.sendAction(source, ...)}), que le serveur valide. Le contexte {@link HoloInteraction} est
 * reutilise : ne pas le garder au-dela de l'appel.
 */
public interface HoloInteractible
{
    /** Vrai si cet element prend {@code control} quand il est vise ({@code MOUSE_MOVE} ignore). */
    default boolean intercepts(HoloControl control) { return false; }

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

    /** Appui ou relachement d'un autre controle intercepte : Echap, Entree, mapping des options. */
    default void onControl(HoloInteraction ctx, HoloControl control, boolean pressed) {}
}
