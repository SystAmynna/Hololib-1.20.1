package com.amynna.hololib.holo.theme;

import com.amynna.hololib.holo.widget.HoloCanvas;
import net.minecraft.network.chat.Component;

/**
 * Motif de fond des fenetres d'un theme, dessine en code commun par {@link HoloCanvas} (aucune classe client). Un
 * motif n'a pas de couleur propre : il prend celles du theme. Enregistre dans {@link HoloThemePatterns} ; un autre mod
 * ajoute les siens par son propre {@code DeferredRegister}.
 * <p>
 * <b>Regle</b> : un motif se dessine en morceaux <b>qui ne se recouvrent pas</b>, dans une seule couche
 * ({@code beginLayer} / {@code endLayer}), chaque morceau avec sa couleur finale ({@link #mix}). Des quads
 * translucides superposes a quelques centiemes de pixel d'ecart scintillent (z-fighting, tri des quads translucides
 * par Embeddium / Oculus), surtout vus de dos.
 */
public interface HoloThemePattern
{
    /** Nom affiche dans les configurateurs. */
    Component name();

    /**
     * Dessine le fond sur {@code (0, 0) - (width, height)}, du fond vers l'avant (fil de rendu, sans allocation).
     *
     * @param alpha opacite voulue par la fenetre ({@code 0..255}), a appliquer au fond ; les traits du motif en
     *              derivent
     */
    void draw(HoloCanvas canvas, float width, float height, HoloTheme theme, int alpha);

    /** Fond sombre commun aux motifs integres : la couleur secondaire tres assombrie. */
    HoloColor BASE = HoloColor.secondary(0xFF).darker(0.84f);

    /**
     * Couleur d'un trait pose sur le fond, calculee d'avance : {@code base} ({@code 0xAARRGGBB}, son alpha est garde)
     * melangee a {@code rgb} dans la proportion {@code fraction}.
     */
    static int mix(int base, int rgb, float fraction)
    {
        return HoloColor.lerp(base, 0xFF000000 | rgb, fraction) & 0xFFFFFF | base & 0xFF000000;
    }

    /** Alpha {@code alpha * fraction}, borne. */
    static int scale(int alpha, float fraction) { return Math.max(0, Math.min(255, Math.round(alpha * fraction))); }
}
