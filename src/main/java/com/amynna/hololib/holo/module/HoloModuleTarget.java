package com.amynna.hololib.holo.module;

/**
 * Compatibilite d'un type de module : le seul genre de memoire dans lequel il peut etre installe. Un type de module a
 * exactement une compatibilite.
 */
public enum HoloModuleTarget
{
    /** Memoire de la dimension : accessible partout dans la dimension. */
    DIMENSION,
    /** Memoire de position, portee par un marker : chargee avec lui. */
    POSITION,
    /** Memoire d'un joueur. */
    PLAYER
}
