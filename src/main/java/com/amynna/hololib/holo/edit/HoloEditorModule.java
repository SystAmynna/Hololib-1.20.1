package com.amynna.hololib.holo.edit;

import net.minecraft.network.chat.Component;

/**
 * Module de memoire de joueur qui donne une capacite d'edition ({@link HoloEditorCapability}). Tous les modules
 * editeurs equipes s'ouvrent ensemble par la touche d'edition ; un editeur desactive ne donne plus sa capacite. Le
 * serveur lit les modules editeurs du joueur pour valider chaque modification.
 */
public interface HoloEditorModule
{
    HoloEditorCapability editorCapability();

    /** Faux si le joueur l'a desactive (etat sauvegarde) : le module ne donne alors plus sa capacite. */
    boolean editorEnabled();

    /** Nom affiche (panneau des editeurs). */
    Component editorName();
}
