package com.amynna.hololib.holo.edit;

import com.amynna.hololib.holo.memory.HoloScope;

import java.util.UUID;

/**
 * Capacite d'edition apportee par un module editeur ({@link HoloEditorModule}) equipe dans la memoire du joueur.
 * Chaque capacite ouvre une portee ; {@link #OTHER} n'en ouvre aucune mais leve la propriete sur les autres.
 */
public enum HoloEditorCapability
{
    /** Objets fixes de sa propre memoire : {@code Local} relatif, monde, ciel ({@code HoloEditRules.ownCapability}). */
    OWN_FIX,
    /** Objets dynamiques de sa propre memoire : {@code Local} follow et {@code HoloRelativeToOpen}. */
    OWN_DYN,
    /** Objets HUD de sa propre memoire (cadre HUD de l'editor). */
    OWN_HUD,
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
     * autre joueur) ; {@code own} : capacite de l'objet dans sa propre memoire ({@code HoloEditRules.ownCapability}).
     */
    public static HoloEditorCapability of(HoloScope scope, UUID player, HoloEditorCapability own)
    {
        if (scope instanceof HoloScope.Player owned) return owned.owner().equals(player) ? own : null;
        if (scope instanceof HoloScope.Position) return WORLD;
        if (scope instanceof HoloScope.Dimension) return DIMENSION;
        return null;
    }

    /** Vrai pour une capacite sur sa propre memoire. */
    public boolean own() { return this == OWN_FIX || this == OWN_DYN || this == OWN_HUD; }
}
