package com.amynna.hololib.holo.widget;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.theme.HoloTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Surface de dessin 2D d'une fenetre, en pixels : origine en haut a gauche, {@code x} vers la droite, {@code y} vers
 * le bas (comme le GUI vanilla). Interface commune : implementee cote client sur un {@code MultiBufferSource}, ce qui
 * permet d'ecrire les widgets sans classe client. Couleurs en {@code 0xAARRGGBB}.
 * <p>
 * Chaque appel est dessine legerement devant le precedent : dessiner du fond vers l'avant. Une forme faite de
 * plusieurs quadrilateres (anneau, couronne, icone) se dessine dans une couche ({@link #beginLayer()} /
 * {@link #endLayer()}) : sinon chaque morceau est un peu plus pres de l'oeil et la forme se deforme en perspective.
 * <p>
 * Le canvas porte le {@linkplain #theme() theme} de l'objet dessine : un widget resout ses {@link HoloColor} par
 * {@link #color(HoloColor)} au moment du dessin.
 */
public interface HoloCanvas
{
    /** Fraction du tick client ecoulee (0..1), pour interpoler les animations tenues par {@code tick()}. */
    float partialTick();

    /** Theme de l'objet en cours de dessin ({@code HoloTheme.DEFAULT} par defaut). */
    default HoloTheme theme() { return HoloTheme.DEFAULT; }

    /** Couleur {@code 0xAARRGGBB} dans le theme courant. */
    default int color(HoloColor color) { return color.resolve(theme()); }

    /** Decale l'origine ; a equilibrer par {@link #pop()}. */
    void push(float dx, float dy);

    void pop();

    /**
     * Debut d'une couche : jusqu'a {@link #endLayer()}, les dessins partagent la meme profondeur (morceaux d'une meme
     * forme, sans recouvrement : a profondeur egale, l'ordre n'est plus garanti). Imbricable ; a equilibrer.
     */
    default void beginLayer() {}

    /** Fin de la couche : les dessins suivants passent devant elle. */
    default void endLayer() {}

    void fill(float x, float y, float width, float height, int argb);

    /** Contour interieur au rectangle. */
    default void outline(float x, float y, float width, float height, float thickness, int argb)
    {
        fill(x, y, width, thickness, argb);
        fill(x, y + height - thickness, width, thickness, argb);
        fill(x, y + thickness, thickness, height - 2f * thickness, argb);
        fill(x + width - thickness, y + thickness, thickness, height - 2f * thickness, argb);
    }

    /**
     * Rectangle en degrade : une couleur par coin (haut gauche, haut droit, bas droit, bas gauche), interpolee. Par
     * defaut (canvas sans couleurs par sommet) : la couleur moyenne.
     */
    default void gradient(float x, float y, float width, float height, int topLeft, int topRight, int bottomRight,
                          int bottomLeft)
    {
        fill(x, y, width, height, HoloColor.lerp(HoloColor.lerp(topLeft, topRight, 0.5f),
                HoloColor.lerp(bottomLeft, bottomRight, 0.5f), 0.5f));
    }

    /**
     * Quadrilatere quelconque aux sommets donnes, dans l'ordre du contour (un triangle : deux sommets confondus).
     * Pour les formes non rectangulaires (disque en eventail, piece tournee...), calculees par le widget.
     */
    void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb);

    /** Trait d'epaisseur {@code thickness} de {@code (x0, y0)} a {@code (x1, y1)} (icones). */
    default void line(float x0, float y0, float x1, float y1, float thickness, int argb)
    {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-4f) return;
        float nx = -dy / length * thickness / 2f;
        float ny = dx / length * thickness / 2f;
        quad(x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny, argb);
    }

    /**
     * Anneau elliptique de centre {@code (cx, cy)}, rayons exterieurs {@code rx} / {@code ry}, epaisseur
     * {@code thickness}, en {@code segments} quadrilateres (icones).
     */
    default void ellipse(float cx, float cy, float rx, float ry, float thickness, int segments, int argb)
    {
        float ix = Math.max(0f, rx - thickness);
        float iy = Math.max(0f, ry - thickness);
        beginLayer();
        for (int i = 0; i < segments; i++) {
            double a0 = i * 2.0 * Math.PI / segments;
            double a1 = (i + 1) * 2.0 * Math.PI / segments;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            quad(cx + rx * c0, cy + ry * s0, cx + rx * c1, cy + ry * s1, cx + ix * c1, cy + iy * s1,
                    cx + ix * c0, cy + iy * s0, argb);
        }
        endLayer();
    }

    /** Profondeur courante (pixels) : a lire puis restaurer par {@link #z(float)} pour que des dessins repartent du meme niveau. */
    default float z() { return 0f; }

    /** Fixe la profondeur courante (voir {@link #z()}). */
    default void z(float z) {}

    /** Decale la profondeur de {@code dz} pixels (positif = vers le spectateur) ; rappeler avec {@code -dz} pour annuler. */
    default void shiftDepth(float dz) {}

    /**
     * Restreint les dessins suivants au rectangle (repere courant), intersecte avec le clip deja actif ; a refermer par
     * {@link #popClip()}. Rognage logiciel : {@code fill}, {@code gradient} et {@code texture} sont rognes ; {@code quad}
     * et {@code text} ne sont dessines que s'ils tiennent entierement dans le rectangle (en hauteur pour le texte).
     */
    default void pushClip(float x, float y, float width, float height) {}

    /** Retire le dernier clip ({@link #pushClip}). */
    default void popClip() {}

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
