package com.amynna.hololib.holo.mode;

/**
 * Module de memoire de joueur qui porte un {@link HoloMode}. Le client lit l'etat des modules de ce type pour choisir
 * quels objets dessiner (affichage {@code FOCUS} : focus du proprietaire de la memoire, ou du joueur local pour une
 * memoire sans proprietaire). Un seul mode actif a la fois par joueur : activer l'un ferme les autres.
 */
public interface HoloModeModule
{
    HoloMode mode();

    /** Vrai si le mode est actif (etat du miroir pour un client). */
    boolean modeActive();
}
