package com.amynna.hololib.holo.object;

/** Qui voit un {@link HoloObject} en dehors de son proprietaire. */
public enum HoloVisibility
{
    /** Proprietaire seul : le serveur n'envoie ni l'ancrage ni l'objet aux autres joueurs. */
    PRIVATE,
    /** Tous les joueurs de la dimension. */
    PUBLIC;

    private static final HoloVisibility[] VALUES = values();

    public static HoloVisibility byOrdinal(int ordinal)
    {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : PRIVATE;
    }
}
