package com.amynna.hololib.holo.widget;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Surface de dessin 2D d'une fenetre, en pixels : origine en haut a gauche, {@code x} vers la droite, {@code y} vers
 * le bas (comme le GUI vanilla). Interface commune : implementee cote client sur un {@code MultiBufferSource}, ce qui
 * permet d'ecrire les widgets sans classe client. Couleurs en {@code 0xAARRGGBB}.
 * <p>
 * Chaque appel est dessine legerement devant le precedent : dessiner du fond vers l'avant.
 */
public interface HoloCanvas
{
    /** Fraction du tick client ecoulee (0..1), pour interpoler les animations tenues par {@code tick()}. */
    float partialTick();

    /** Decale l'origine ; a equilibrer par {@link #pop()}. */
    void push(float dx, float dy);

    void pop();

    void fill(float x, float y, float width, float height, int argb);

    /** Contour interieur au rectangle. */
    default void outline(float x, float y, float width, float height, float thickness, int argb)
    {
        fill(x, y, width, thickness, argb);
        fill(x, y + height - thickness, width, thickness, argb);
        fill(x, y + thickness, thickness, height - 2f * thickness, argb);
        fill(x + width - thickness, y + thickness, thickness, height - 2f * thickness, argb);
    }

    /** Texte sur une ligne, coin haut gauche en {@code (x, y)}. */
    void text(Component text, float x, float y, int argb);

    /** Largeur du texte, en pixels. */
    int textWidth(Component text);

    /** Hauteur d'une ligne de texte, en pixels. */
    int lineHeight();

    /** Portion {@code (u0, v0) - (u1, v1)} (0..1) d'une texture, teintee par {@code argb}. */
    void texture(ResourceLocation texture, float x, float y, float width, float height, float u0, float v0, float u1,
                 float v1, int argb);
}
