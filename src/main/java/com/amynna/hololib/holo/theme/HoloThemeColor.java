package com.amynna.hololib.holo.theme;

import net.minecraft.network.chat.Component;

/**
 * Role d'une couleur dans un {@link HoloTheme}. Un module ne choisit jamais une teinte : il choisit un role (et son
 * alpha, sa nuance) par {@link HoloColor}.
 */
public enum HoloThemeColor
{
    /** Bleu par defaut : accents, bordures, titres. */
    PRIMARY("Principale"),
    /** Autre bleu par defaut : fonds de boutons et de lignes, fond des motifs. */
    SECONDARY("Secondaire"),
    /** Vert par defaut : actif, cible, element courant. */
    SELECTION("Selection"),
    /** Jaune par defaut : action en cours, element mis en avant. */
    ACTION("Action"),
    /** Blanc par defaut : textes, gris (assombri). */
    SPECIAL("Speciale"),
    /** Rouge par defaut : erreur, suppression, danger. */
    CRITICAL("Critique");

    private static final HoloThemeColor[] VALUES = values();

    private final Component label;

    HoloThemeColor(String label) { this.label = Component.literal(label); }

    /** Nom affiche dans les configurateurs. */
    public Component label() { return label; }

    /** Role d'ordinal {@code ordinal}, ou {@code null} hors bornes (paquet ou sauvegarde invalide). */
    public static HoloThemeColor byOrdinal(int ordinal)
    {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
    }

    public static int count() { return VALUES.length; }
}
