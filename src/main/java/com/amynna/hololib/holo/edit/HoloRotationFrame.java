package com.amynna.hololib.holo.edit;

/**
 * Repere de la rotation d'un {@link HoloRelativeToOpen} quand il est place depuis sa position relative
 * ({@code HoloObject.openOffset()}). Sa direction est toujours relative au regard ; sa rotation :
 * <ul>
 *     <li>{@link #GAZE} : relative au regard entier. En ouvrant le focus le regard leve, une fenetre a droite est
 *     inclinee comme le regard (jusqu'a 90 degres) ;</li>
 *     <li>{@link #PLAYER} : relative au joueur, comme un {@code Local} relatif (face a l'oeil, droite). Une fenetre a
 *     toujours le meme angle, quel que soit le tangage du regard a l'ouverture.</li>
 * </ul>
 * Les deux coincident pour un regard horizontal. Synchronise et sauvegarde par le framework ; modifiable en jeu si
 * l'objet implemente {@link HoloEditRotation}.
 */
public enum HoloRotationFrame
{
    GAZE,
    PLAYER;

    private static final HoloRotationFrame[] VALUES = values();

    public static HoloRotationFrame byOrdinal(int ordinal)
    {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : PLAYER;
    }
}
