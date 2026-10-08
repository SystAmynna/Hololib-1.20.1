package com.amynna.hololib.holo.edit;

/**
 * Objet de l'interface de l'editeur lui-meme (repere de l'ecran, fenetre du cadre HUD) :
 * pendant la session d'edition, son proprietaire le voit et l'edite sans capacite d'edition, si
 * {@link #shownInSession()} est vrai. A placer dans une memoire de joueur, en affichage {@code OFF}.
 */
public interface HoloEditorUi extends HoloEditable
{
    /** Cote client : vrai si l'objet doit apparaitre dans la session (ex. son module est actif). */
    default boolean shownInSession() { return true; }
}
