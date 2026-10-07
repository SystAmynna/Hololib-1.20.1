package com.amynna.hololib.holo.edit;

/**
 * Affichage d'un {@code HoloObject} : <b>quand</b> il est dessine, independamment de qui le voit
 * ({@code HoloVisibility}). Synchronise et sauvegarde par le framework ; modifiable en jeu si l'objet implemente
 * {@link HoloEditVisibility}. En mode edition, un objet editable est toujours dessine.
 */
public enum HoloDisplay
{
    /** Jamais dessine (sauf en mode edition). */
    OFF,
    /** Dessine seulement quand le proprietaire de la memoire est en mode focus. */
    FOCUS,
    /** Toujours dessine. */
    ON;

    private static final HoloDisplay[] VALUES = values();

    public static HoloDisplay byOrdinal(int ordinal)
    {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : ON;
    }

    /** Mode suivant du cycle {@code OFF -> FOCUS -> ON -> OFF}. */
    public HoloDisplay next() { return VALUES[(ordinal() + 1) % VALUES.length]; }
}
