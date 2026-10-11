package com.amynna.hololib.modules.dimension.space.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Masses reelles des 62 corps de la galaxie par defaut (T39, D29), en kg, <b>seule source</b> de ces valeurs : utilisee
 * par {@link SpaceGalaxy#createDefault()} et par la lecture NBT d'un corps sauvegarde avant T39
 * ({@link SpaceBody#load}, cle {@code mass} absente : masse par nom, comme la rotation "auto" de C83). Un nom absent de
 * la table donne une masse estimee ({@link SpaceBody#estimatedMass}). Valeurs, sources et conventions :
 * {@code docs/space/donnees-systemes.md}, section "Masses".
 * <p>
 * Sol : NASA Planetary Fact Sheets (Soleil, planetes, Pluton), JPL SSD (lunes : {@code GM / G},
 * {@code G = 6,6743e-11}), publications citees par l'annexe (Ceres, Eris, Haumea, Makemake). Etoiles voisines en masses
 * solaires ({@value #SUN} kg), exoplanetes en masses terrestres ({@value #EARTH} kg) : <b>masse minimale
 * {@code M sin i}</b> (archive NASA) sauf Epsilon Eridani b (masse vraie, Thompson et al. 2025). Noms sans accents,
 * comparaison exacte (comme {@link SpaceSolRotations}).
 */
final class SpaceKnownMasses
{
    /** Masse du Soleil, kg (NASA Sun Fact Sheet). */
    static final double SUN = 1.9884e30;
    /** Masse de la Terre, kg (NASA Earth Fact Sheet). */
    static final double EARTH = 5.9722e24;

    private static final Map<String, Double> BY_NAME = new HashMap<>();

    static {
        // Sol : Soleil et planetes (NASA Planetary Fact Sheets).
        mass("Soleil", SUN);
        mass("Mercure", 3.3010e23);
        mass("Venus", 4.8673e24);
        mass("Terre", EARTH);
        mass("Mars", 6.4169e23);
        mass("Jupiter", 1.89813e27);
        mass("Saturne", 5.6832e26);
        mass("Uranus", 8.6811e25);
        mass("Neptune", 1.02409e26);
        // Objets mineurs : Ceres (Dawn, GM 62,6284), Pluton (fact sheet), Eris (Holler et al. 2021), Haumea
        // (Ragozzine & Brown 2009), Makemake (estimation, lune MK2).
        mass("Ceres", 9.3835e20);
        mass("Pluton", 1.303e22);
        mass("Eris", 1.6466e22);
        mass("Haumea", 4.006e21);
        mass("Makemake", 3.1e21);
        // Lunes : JPL SSD, Planetary Satellite Physical Parameters (GM / G).
        mass("Lune", 7.3458e22);
        mass("Phobos", 1.0618e16);
        mass("Deimos", 1.4413e15);
        mass("Io", 8.9296e22);
        mass("Europe", 4.7986e22);
        mass("Ganymede", 1.4815e23);
        mass("Callisto", 1.0757e23);
        mass("Encelade", 1.0803e20);
        mass("Tethys", 6.1750e20);
        mass("Dione", 1.0955e21);
        mass("Rhea", 2.3065e21);
        mass("Titan", 1.3452e23);
        mass("Japet", 1.8057e21);
        mass("Miranda", 6.4426e19);
        mass("Ariel", 1.2511e21);
        mass("Umbriel", 1.2750e21);
        mass("Titania", 3.3996e21);
        mass("Oberon", 3.0760e21);
        mass("Triton", 2.1403e22);
        mass("Charon", 1.5897e21);

        // Systemes voisins : etoiles (masses solaires), planetes (masses terrestres, M sin i sauf mention).
        mass("Alpha du Centaure A", sun(1.0788));
        mass("Alpha du Centaure B", sun(0.9092));
        mass("Proxima du Centaure", sun(0.1221));
        mass("Proxima b", earth(1.055));
        mass("Proxima d", earth(0.26));
        mass("Barnard", sun(0.162));
        mass("Barnard d", earth(0.263));
        mass("Barnard b", earth(0.299));
        mass("Barnard c", earth(0.335));
        mass("Barnard e", earth(0.193));
        mass("Wolf 359", sun(0.110));
        mass("Lalande 21185", sun(0.3899));
        mass("Lalande 21185 b", earth(2.69));
        mass("Lalande 21185 c", earth(13.6));
        mass("Sirius A", sun(2.063));
        mass("Sirius B", sun(1.018));
        mass("Epsilon Eridani", sun(0.82));
        // Masse vraie (orbite inclinee de 40 degres, Thompson et al. 2025) : 1,00 masse de Jupiter.
        mass("Epsilon Eridani b", earth(317.828407));
        mass("61 Cygni A", sun(0.70));
        mass("61 Cygni B", sun(0.63));
        mass("Procyon A", sun(1.478));
        mass("Procyon B", sun(0.592));
        mass("Tau Ceti", sun(0.783));
        mass("Tau Ceti g", earth(1.75));
        mass("Tau Ceti h", earth(1.83));
        mass("Tau Ceti f", earth(3.93));
        mass("Altair", sun(1.86));
        mass("Vega", sun(2.135));
    }

    private SpaceKnownMasses() {}

    /** Masse reelle (kg) du corps par defaut nomme exactement {@code name}, {@code NaN} si le nom n'est pas connu. */
    static double byName(String name)
    {
        Double mass = BY_NAME.get(name);
        return mass != null ? mass : Double.NaN;
    }

    private static double sun(double solarMasses) { return solarMasses * SUN; }

    private static double earth(double earthMasses) { return earthMasses * EARTH; }

    private static void mass(String name, double kg)
    {
        if (BY_NAME.put(name, kg) != null) throw new IllegalStateException("Masse en double : " + name);
    }
}
