package com.amynna.hololib.holo.widget;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Mesure du texte en pixels de fenetre, utilisable hors du dessin (logique d'un widget : retour a la ligne, curseur...)
 * et en code commun. Delegue a la police du client, installee au setup client ; ailleurs (serveur dedie), estimation
 * de {@value #FALLBACK_CHAR_WIDTH} pixels par caractere et {@value #FALLBACK_LINE_HEIGHT} par ligne.
 */
public final class HoloTextMetrics
{
    private static final int FALLBACK_CHAR_WIDTH = 6;
    private static final int FALLBACK_LINE_HEIGHT = 9;

    /** Implementation cote client. */
    @ApiStatus.Internal
    public interface Backend
    {
        int width(String text);

        int lineHeight();
    }

    private static Backend backend;

    private HoloTextMetrics() {}

    /** Largeur du texte sur une ligne, en pixels (meme mesure que {@link HoloCanvas#textWidth}). */
    public static int width(String text)
    {
        return backend != null ? backend.width(text) : FALLBACK_CHAR_WIDTH * text.length();
    }

    /** Hauteur d'une ligne de texte, en pixels. */
    public static int lineHeight() { return backend != null ? backend.lineHeight() : FALLBACK_LINE_HEIGHT; }

    @ApiStatus.Internal
    public static void install(Backend backend) { HoloTextMetrics.backend = Objects.requireNonNull(backend); }
}
