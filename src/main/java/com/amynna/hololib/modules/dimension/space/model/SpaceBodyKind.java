package com.amynna.hololib.modules.dimension.space.model;

/**
 * Type d'un corps celeste et regles de parente (voir {@code docs/space/03-modele-donnees.md} par. 2) : {@code STAR} sans
 * parent ou autour d'une {@code STAR} (binaires simples), {@code PLANET} autour d'une {@code STAR}, {@code MOON}
 * autour d'une {@code PLANET}. Porte aussi les valeurs par defaut d'un corps ajoute.
 * <p>
 * Sauvegarde par nom ({@link #name()}), reseau par ordinal : ne jamais renommer ni reordonner les constantes.
 */
public enum SpaceBodyKind
{
    STAR("Etoile", 696_000.0, 0xFFFFE07A, 20.0, 32_668.0),
    PLANET("Planete", 6_371.0, 0xFF3A6FD8, 1.0, 365.25),
    MOON("Lune", 1_737.0, 0xFFB4B4B4, 0.00257, 27.32);

    private final String defaultName;
    private final double defaultRadius;
    private final int defaultColor;
    private final double defaultSemiMajorAxis;
    private final double referencePeriod;

    SpaceBodyKind(String defaultName, double defaultRadius, int defaultColor, double defaultSemiMajorAxis,
                  double referencePeriod)
    {
        this.defaultName = defaultName;
        this.defaultRadius = defaultRadius;
        this.defaultColor = defaultColor;
        this.defaultSemiMajorAxis = defaultSemiMajorAxis;
        this.referencePeriod = referencePeriod;
    }

    /** Prefixe du nom d'un corps ajoute (suivi de son id). */
    public String defaultName() { return defaultName; }

    /** Rayon d'un corps ajoute, en km. */
    public double defaultRadius() { return defaultRadius; }

    /** Couleur ARGB d'un corps ajoute. */
    public int defaultColor() { return defaultColor; }

    /**
     * Demi-grand axe minimal d'un corps ajoute sur orbite, en UA (avant ecartement des orbites existantes). Pour une
     * etoile : compagne d'une binaire, loin des planetes habituelles.
     */
    public double defaultSemiMajorAxis() { return defaultSemiMajorAxis; }

    /**
     * Periode d'un corps ajoute a {@link #defaultSemiMajorAxis()}, en jours ; a un autre demi-grand axe {@code a},
     * la periode par defaut suit la troisieme loi de Kepler : {@code P = reference * (a / a_defaut)^1.5}.
     */
    public double referencePeriod() { return referencePeriod; }

    /**
     * Vrai si un corps de ce type peut avoir pour parent un corps de type {@code parent} ({@code null} = aucun
     * parent, centre du systeme).
     */
    public boolean acceptsParent(SpaceBodyKind parent)
    {
        return switch (this) {
            case STAR -> parent == null || parent == STAR;
            case PLANET -> parent == STAR;
            case MOON -> parent == PLANET;
        };
    }

    /** Type par son nom sauvegarde, {@code null} si inconnu. */
    public static SpaceBodyKind byName(String name)
    {
        for (SpaceBodyKind kind : values()) if (kind.name().equals(name)) return kind;
        return null;
    }

    /** Type par son ordinal reseau ; leve si l'ordinal est invalide (reseau strict). */
    public static SpaceBodyKind byOrdinal(int ordinal)
    {
        SpaceBodyKind[] values = values();
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("Type de corps : " + ordinal);
        return values[ordinal];
    }
}
