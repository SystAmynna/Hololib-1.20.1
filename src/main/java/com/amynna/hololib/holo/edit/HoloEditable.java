package com.amynna.hololib.holo.edit;

/**
 * Base des interfaces d'edition, implementees par un {@code HoloObject} : l'objet peut etre modifie en jeu par un
 * joueur en mode edition. Chaque interface derivee ajoute un outil ({@link HoloEditTranslation},
 * {@link HoloEditRotation}, {@link HoloEditScale}, {@link HoloEditVisibility}, {@link HoloEditFixOrDynamic}) ; le
 * framework sauvegarde et synchronise le resultat comme tout placement.
 * <p>
 * Qui peut editer ({@link HoloEditRules#mayEdit}) : un joueur qui voit l'objet, equipe du module editeur de sa
 * portee ({@link HoloEditorCapability}), si {@link #ownRequired()} et {@link #opRequired()} l'autorisent. Le
 * proprietaire est celui de la memoire de joueur, ou le createur du module pour une memoire de position ou de
 * dimension ({@code HoloModule.editOwner()}). Le client ne propose l'edition qu'aux joueurs autorises ; le serveur
 * verifie a nouveau chaque modification. Le droit d'usage ({@code HoloAccess}) n'intervient pas.
 */
public interface HoloEditable
{
    /**
     * Vrai (defaut) : seul le proprietaire peut editer l'objet (memoire de joueur : son proprietaire ; position ou
     * dimension : le createur du module, ou un operateur equipe de l'Other Editor).
     */
    default boolean ownRequired() { return true; }

    /** Vrai : il faut etre operateur (niveau 2) pour editer l'objet. Faux par defaut. */
    default boolean opRequired() { return false; }
}
