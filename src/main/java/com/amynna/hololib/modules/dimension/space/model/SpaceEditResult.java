package com.amynna.hololib.modules.dimension.space.model;

import net.minecraft.network.chat.Component;

/**
 * Resultat d'une operation d'edition de {@link SpaceGalaxy} : succes (avec l'id cree pour un ajout) ou refus motive.
 * Le motif est un {@link Component} destine au joueur (texte litteral en francais sans accents, a remplacer par une
 * cle de traduction plus tard).
 *
 * @param success vrai si l'operation a ete appliquee
 * @param id      id du systeme ou du corps cree par un ajout reussi, {@code 0} sinon
 * @param message motif du refus, {@code null} en cas de succes
 */
public record SpaceEditResult(boolean success, int id, Component message)
{
    private static final SpaceEditResult SUCCESS = new SpaceEditResult(true, 0, null);

    /** Succes sans creation. */
    public static SpaceEditResult ok() { return SUCCESS; }

    /** Succes d'un ajout : id de l'element cree. */
    public static SpaceEditResult created(int id) { return new SpaceEditResult(true, id, null); }

    /** Refus, avec son motif. */
    public static SpaceEditResult refused(String reason)
    {
        return new SpaceEditResult(false, 0, Component.literal(reason));
    }
}
