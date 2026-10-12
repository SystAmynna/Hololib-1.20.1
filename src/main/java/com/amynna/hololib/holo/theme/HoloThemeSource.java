package com.amynna.hololib.holo.theme;

/** Theme applique a un {@link HoloThemed} : celui du serveur (commun a tous) ou celui de chaque client. */
public enum HoloThemeSource
{
    /** Selection du serveur : le meme rendu pour tous les joueurs. */
    SERVER,
    /** Selection du joueur qui regarde (celle du serveur tant qu'il n'a rien choisi). */
    CLIENT
}
