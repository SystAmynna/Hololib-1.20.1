package com.amynna.hololib.holo.edit;

import com.amynna.hololib.holo.memory.HoloScope;

import java.util.UUID;

/**
 * Capacite d'edition apportee par un module editeur ({@link HoloEditorModule}) equipe dans la memoire du joueur.
 * Chaque capacite ouvre une portee ; {@link #OTHER} n'en ouvre aucune mais leve la propriete sur les autres.
 */
public enum HoloEditorCapability
{
    /** Objets de sa propre memoire. */
    OWN,
    /** Objets des memoires de position (markers). */
    WORLD,
    /** Objets de la memoire de dimension (celestes). */
    DIMENSION,
    /**
     * Operateurs : ignore {@code ownRequired} (proprietaire = createur du module) pour {@link #WORLD} et
     * {@link #DIMENSION}. N'ouvre pas la memoire des autres joueurs.
     */
    OTHER;

    /**
     * Capacite requise pour editer un objet de cette portee, ou {@code null} si aucune ne le permet (memoire d'un
     * autre joueur).
     */
    public static HoloEditorCapability of(HoloScope scope, UUID player)
    {
        if (scope instanceof HoloScope.Player owned) return owned.owner().equals(player) ? OWN : null;
        if (scope instanceof HoloScope.Position) return WORLD;
        if (scope instanceof HoloScope.Dimension) return DIMENSION;
        return null;
    }
}
