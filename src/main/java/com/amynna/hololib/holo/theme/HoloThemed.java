package com.amynna.hololib.holo.theme;

/**
 * Trait d'un objet (ou d'un element dessine) qui prend en charge un theme. Le module choisit dans son code la
 * {@linkplain #themeSource() source} et le {@linkplain #staffTheme() jeu} (standard ou staff) ; le renderer resout le
 * theme par {@link HoloThemes#resolve}. Toutes les {@code HoloWindow} l'implementent.
 */
public interface HoloThemed
{
    /** Theme du serveur ou du client qui regarde ({@code CLIENT} par defaut). */
    default HoloThemeSource themeSource() { return HoloThemeSource.CLIENT; }

    /** Vrai pour un objet reserve aux ops : il prend le theme staff de sa source. */
    default boolean staffTheme() { return false; }

    /** Theme impose (apercu d'un configurateur), cote client ; {@code null} = theme resolu. */
    default HoloTheme themeOverride() { return null; }
}
