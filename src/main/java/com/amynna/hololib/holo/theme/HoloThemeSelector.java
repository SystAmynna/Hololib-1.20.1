package com.amynna.hololib.holo.theme;

/**
 * Selection des themes d'un joueur, portee par un module de sa memoire (Theme Configurator) : le client la lit dans la
 * memoire active du joueur local. Sans module, le joueur suit le serveur.
 */
public interface HoloThemeSelector
{
    /** Aucun choix : le joueur suit la selection du serveur. */
    int FOLLOW_SERVER = -1;

    /** Identifiant du theme choisi pour le jeu standard ou staff, ou {@link #FOLLOW_SERVER}. */
    int selectedTheme(boolean staff);
}
