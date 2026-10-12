/**
 * Themes : regles de rendu des objets {@link com.amynna.hololib.holo.theme.HoloThemed} (toutes les fenetres), code
 * commun.
 * <ul>
 * <li>{@link com.amynna.hololib.holo.theme.HoloTheme} : motif de fond des fenetres
 * ({@link com.amynna.hololib.holo.theme.HoloThemePattern}, registre Forge {@code hololib:theme_pattern} extensible) et
 * une couleur par {@linkplain com.amynna.hololib.holo.theme.HoloThemeColor role} (principale, secondaire, selection,
 * action, speciale, critique), sans alpha.</li>
 * <li>{@link com.amynna.hololib.holo.theme.HoloColor} : ce qu'un module dessine, role + alpha + nuance (ou couleur
 * fixe), resolu dans le theme au dessin ; un module n'ecrit jamais de teinte en dur.</li>
 * <li>{@link com.amynna.hololib.holo.theme.HoloThemeLibrary} : bibliotheque nommee du serveur (themes partages, deux
 * integres) et selections du serveur standard / staff, envoyees a tous.</li>
 * <li>Selection du joueur : module {@link com.amynna.hololib.holo.theme.HoloThemeSelector} de sa memoire (suit le
 * serveur par defaut).</li>
 * <li>{@link com.amynna.hololib.holo.theme.HoloThemes} : resolution (source {@code SERVER} / {@code CLIENT} choisie
 * par le module, jeu staff pour les objets reserves aux ops), backend client.</li>
 * </ul>
 */
package com.amynna.hololib.holo.theme;
