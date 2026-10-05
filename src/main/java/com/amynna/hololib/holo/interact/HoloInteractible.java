package com.amynna.hololib.holo.interact;

import com.amynna.hololib.holo.capture.HoloControl;

/**
 * Element holographique avec lequel le joueur interagit en le <b>regardant</b> : le point vise est le centre de
 * l'ecran (le reticule). Implemente par un {@code HoloObject} (qui doit alors surcharger
 * {@code HoloObject.interactibleAt}), une {@code HoloWindow} ou un widget.
 * <p>
 * Tant qu'il est vise, {@link #intercepts} decide controle par controle ({@link HoloControl}) s'il le prend :
 * intercepte, le controle n'atteint pas le jeu et le callback correspondant est appele ; sinon il remonte au parent
 * interactible (panneaux, puis fenetre), et passe au jeu si personne ne le prend. {@link HoloControl#MOUSE_MOVE} et
 * {@link HoloControl#KEYBOARD} ne peuvent pas etre interceptes au survol (ils retiendraient le joueur) : pour capturer
 * la souris, le clavier ou garder des controles au-dela du survol, demarrer une capture explicite
 * ({@link HoloInteraction#capture}). Le survol ({@link #onHover} / {@link #onHoverRelease}) est toujours signale a
 * l'element vise le plus profond.
 * <p>
 * L'interception au survol s'appuie sur la capture de controles ({@code holo.capture}) : l'interaction y est un captor
 * passif, consulte apres les captures explicites. Callbacks appeles <b>cote client</b>, jamais pendant le rendu, et
 * seulement pour un joueur autorise ({@code HoloObject.access()}). Pour agir sur le jeu, envoyer une action au module
 * ({@code HoloModule.sendAction(source, ...)}), que le serveur valide. Le contexte {@link HoloInteraction} est
 * reutilise : ne pas le garder au-dela de l'appel.
 */
public interface HoloInteractible
{
    /**
     * Vrai si cet element prend {@code control} quand il est vise ({@code MOUSE_MOVE}, {@code KEYBOARD} ignores). Par
     * defaut, aiguille vers {@link #interceptsClick}, {@link #interceptsScroll} ou {@link #interceptsKey} : des traits
     * differents (clic, molette...) peuvent ainsi se combiner sans conflit de methodes par defaut.
     */
    default boolean intercepts(HoloControl control)
    {
        return switch (control.kind()) {
            case MOUSE_BUTTON -> interceptsClick(control);
            case SCROLL -> interceptsScroll();
            case ESCAPE, ENTER, MAPPING -> interceptsKey(control);
            case MOUSE_MOVE, KEYBOARD -> false;
        };
    }

    /** Bouton brut de la souris ({@code LEFT_CLICK}, {@code RIGHT_CLICK}, {@code MIDDLE_CLICK}). */
    default boolean interceptsClick(HoloControl button) { return false; }

    default boolean interceptsScroll() { return false; }

    /** Echap, Entree ou mapping des options. */
    default boolean interceptsKey(HoloControl control) { return false; }

    /**
     * Marge de sortie du survol, en pixels de fenetre (widgets d'une {@code HoloWindow} seulement) : tant que le regard
     * reste dans le rectangle de l'element elargi de cette marge, sur le fond de la fenetre ou en dehors, le survol
     * continue ; viser un autre element le termine aussitot. 0 par defaut.
     */
    default float hoverMargin() { return 0f; }

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

    /**
     * Chaque frame tant qu'un clic pris par cet element reste enfonce : {@code ctx.x(), ctx.y()} est le regard projete
     * sur le plan de l'objet, dans le repere de l'element, meme hors de ses bords ({@code ctx.hovering()} : le regard
     * est encore sur l'objet). Base du glisser-deposer. Rien si le regard ne croise plus le plan (de dos).
     */
    default void onDrag(HoloInteraction ctx, HoloControl button) {}
}
