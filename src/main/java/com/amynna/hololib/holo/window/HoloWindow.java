package com.amynna.hololib.holo.window;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.theme.HoloThemeSource;
import com.amynna.hololib.holo.theme.HoloThemed;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.HoloWidget;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Fenetre holographique : un {@link HoloObject} plat contenant une arborescence de widgets, a partir d'un
 * {@link HoloPanel} racine. Taille en pixels ; {@link #pixelScale()} donne la taille d'un pixel en blocs. La fenetre
 * est centree sur l'origine de l'objet.
 * <p>
 * La fenetre est interactible : elle arrete le regard sur toute sa surface (vue de face) et y cherche le widget
 * {@link HoloInteractible} le plus profond ; les controles qu'aucun widget n'intercepte remontent a la fenetre, qui
 * n'en intercepte aucun par defaut ({@link #setInterceptedInputs}). Son droit d'usage est {@code access()}.
 * <p>
 * Toute fenetre prend un theme ({@link HoloThemed}) : le module choisit sa source ({@link #setThemeSource},
 * {@code CLIENT} par defaut) et le jeu staff pour une fenetre reservee aux ops ({@link #setStaffTheme}) ; le fond de la
 * racine est typiquement le motif du theme ({@code root().setPattern(alpha)}).
 */
public class HoloWindow extends HoloObject implements HoloInteractible, HoloThemed
{
    private final HoloPanel root;
    private final int width;
    private final int height;
    private final float pixelScale;
    private final Set<HoloControl> intercepted = new HashSet<>();
    private HoloThemeSource themeSource = HoloThemeSource.CLIENT;
    private boolean staffTheme;

    /**
     * @param transform  placement initial dans le repere de l'ancrage (copie)
     * @param width      largeur en pixels
     * @param height     hauteur en pixels
     * @param pixelScale taille d'un pixel en blocs (ex. {@code 1/160f} : 160 pixels = 1 bloc)
     */
    public HoloWindow(String key, HoloAnchor anchor, HoloTransform transform, int width, int height, float pixelScale)
    {
        super(key, anchor, transform);
        if (width <= 0 || height <= 0 || !(pixelScale > 0f)) throw new IllegalArgumentException("Taille invalide");
        this.width = width;
        this.height = height;
        this.pixelScale = pixelScale;
        this.root = new HoloPanel(0, 0, width, height);
        root.ownerWindow(this);
    }

    public final HoloPanel root() { return root; }

    public final int width() { return width; }

    public final int height() { return height; }

    public final float pixelScale() { return pixelScale; }

    @Override
    public HoloThemeSource themeSource() { return themeSource; }

    @Override
    public boolean staffTheme() { return staffTheme; }

    /** Theme du serveur ou du client qui regarde ; choix du module, le meme des deux cotes (non synchronise). */
    public HoloWindow setThemeSource(HoloThemeSource source)
    {
        this.themeSource = Objects.requireNonNull(source);
        return this;
    }

    /** Vrai pour une fenetre reservee aux ops : theme staff de sa source. */
    public HoloWindow setStaffTheme(boolean staff)
    {
        this.staffTheme = staff;
        return this;
    }

    /** Widget le plus en avant sous le point (pixels de la fenetre), ou {@code null} hors de la fenetre. */
    public HoloWidget widgetAt(float px, float py) { return root.widgetAt(px, py); }

    /**
     * Controles pris par la fenetre elle-meme quand aucun widget vise ne les prend (ex. les clics, pour que viser le
     * fond ne casse pas le bloc derriere). Remplace les precedents.
     */
    public HoloWindow setInterceptedInputs(HoloControl... controls)
    {
        intercepted.clear();
        for (HoloControl control : controls) intercepted.add(control);
        return this;
    }

    @Override
    public boolean intercepts(HoloControl control) { return intercepted.contains(control); }

    /** Convertit vers les pixels de la fenetre (meme convention que son renderer), puis widget interactible vise. */
    @Override
    public HoloInteractible interactibleAt(float localX, float localY)
    {
        HoloWidget widget = widgetAt(pixelX(localX), pixelY(localY));
        if (widget == null) return null;
        for (; widget != null; widget = widget.parent()) {
            if (widget instanceof HoloInteractible interactible) return interactible;
        }
        return this;
    }

    /** Abscisse en pixels de la fenetre d'un point du plan local de l'objet. */
    public final float pixelX(float localX) { return localX / pixelScale + 0.5f * width; }

    /** Ordonnee en pixels de la fenetre ({@code y} vers le bas) d'un point du plan local de l'objet. */
    public final float pixelY(float localY) { return 0.5f * height - localY / pixelScale; }

    @Override
    public float boundingRadius()
    {
        return 0.5f * pixelScale * (float) Math.sqrt((double) width * width + (double) height * height);
    }

    @Override
    public float halfWidth() { return 0.5f * width * pixelScale; }

    @Override
    public float halfHeight() { return 0.5f * height * pixelScale; }

    @Override
    public boolean flat() { return true; }

    @Override
    public void tick() { root.tick(); }
}
