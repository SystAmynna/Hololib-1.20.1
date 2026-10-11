package com.amynna.hololib.modules.dimension.space.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Rotations propres reelles des 34 corps de Sol (T28), <b>seule source</b> de ces valeurs : utilisee par
 * {@link SpaceGalaxy#createDefault()} (Sol de la galaxie par defaut) et par la lecture NBT d'un corps sauvegarde
 * avant T28 ({@link SpaceBody#load}, cle {@code spin} absente : rotation "auto" par nom). Valeurs, sources et
 * conventions : {@code docs/space/donnees-systemes.md}, section "Rotation propre".
 * <p>
 * Pole nord IAU (obliquite au plus 90, sens retrograde par le signe de la periode, decision C82) ; noeud = longitude
 * ecliptique du pole - 90 - yaw de l'orbite (Soleil : - 90) ; phase 0. Lunes synchrones, axe sur la normale de leur
 * orbite (Lune : etat de Cassini). Noms sans accents, comparaison exacte.
 */
final class SpaceSolRotations
{
    private static final Map<String, SpaceRotation> BY_NAME = new HashMap<>();

    static {
        // Nom, periode siderale (h, < 0 retrograde ; 0 synchrone), obliquite, noeud (degres).
        spin("Soleil", 609.12, 7.25, -104.23);
        spin("Mercure", 1_407.6, 0.034, 150.77);
        spin("Venus", -5_832.6, 2.64, 168.66);
        spin("Terre", 23.9345, 23.44, -102.94);
        spin("Mars", 24.6229, 25.19, -73.13);
        spin("Jupiter", 9.9250, 3.13, 143.07);
        spin("Saturne", 10.656, 26.73, -102.90);
        spin("Uranus", -17.24, 82.23, -3.31);
        spin("Neptune", 16.11, 28.32, -175.73);
        // Objets mineurs : obliquite de Ceres approchee (Dawn) ; Eris, Haumea, Makemake : pole inconnu (0).
        spin("Ceres", 9.07417, 4.0, 127.31);
        spin("Pluton", -153.2928, 60.49, 3.28);
        spin("Eris", 378.86, 0.0, 0.0);
        spin("Haumea", 3.9155, 0.0, 0.0);
        spin("Makemake", 22.8266, 0.0, 0.0);
        spin("Lune", 0.0, 6.68, 180.0);
        for (String moon : new String[] {"Phobos", "Deimos", "Io", "Europe", "Ganymede", "Callisto", "Encelade",
                "Tethys", "Dione", "Rhea", "Titan", "Japet", "Miranda", "Ariel", "Umbriel", "Titania", "Oberon",
                "Triton", "Charon"}) {
            spin(moon, 0.0, 0.0, 0.0);
        }
    }

    private SpaceSolRotations() {}

    /** Rotation reelle du corps de Sol nomme exactement {@code name}, {@code null} si le nom n'est pas dans la table. */
    static SpaceRotation byName(String name) { return BY_NAME.get(name); }

    private static void spin(String name, double period, double obliquity, double node)
    {
        BY_NAME.put(name, new SpaceRotation(period, obliquity, node, 0.0));
    }
}
