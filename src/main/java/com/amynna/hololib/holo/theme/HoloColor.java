package com.amynna.hololib.holo.theme;

import java.util.Objects;

/**
 * Couleur d'un dessin : un {@linkplain HoloThemeColor role} du theme avec son alpha et une nuance (melange vers le
 * blanc ou le noir), ou une couleur fixe {@code 0xAARRGGBB}. Valeur immuable, a garder en constante : la resolution
 * ({@link #resolve}) ne fait que du calcul entier, sans allocation. L'alpha appartient toujours a l'objet, jamais au
 * theme.
 */
public final class HoloColor
{
    /** Aucune couleur (fond ou bordure absents). */
    public static final HoloColor NONE = fixed(0);

    private final HoloThemeColor role;
    private final int argb;
    private final float shade;

    private HoloColor(HoloThemeColor role, int argb, float shade)
    {
        this.role = role;
        this.argb = argb;
        this.shade = shade;
    }

    /** Couleur fixe, la meme dans tous les themes. */
    public static HoloColor fixed(int argb) { return new HoloColor(null, argb, 0f); }

    /** Role du theme, alpha {@code 0..255}. */
    public static HoloColor of(HoloThemeColor role, int alpha)
    {
        return new HoloColor(Objects.requireNonNull(role), (alpha & 0xFF) << 24, 0f);
    }

    public static HoloColor of(HoloThemeColor role) { return of(role, 0xFF); }

    public static HoloColor primary(int alpha) { return of(HoloThemeColor.PRIMARY, alpha); }

    public static HoloColor secondary(int alpha) { return of(HoloThemeColor.SECONDARY, alpha); }

    public static HoloColor selection(int alpha) { return of(HoloThemeColor.SELECTION, alpha); }

    public static HoloColor action(int alpha) { return of(HoloThemeColor.ACTION, alpha); }

    public static HoloColor special(int alpha) { return of(HoloThemeColor.SPECIAL, alpha); }

    public static HoloColor critical(int alpha) { return of(HoloThemeColor.CRITICAL, alpha); }

    /** Melange vers le blanc ({@code f} dans {@code 0..1}). */
    public HoloColor lighter(float f) { return shaded(Math.max(0f, Math.min(1f, f))); }

    /** Melange vers le noir ({@code f} dans {@code 0..1}). */
    public HoloColor darker(float f) { return shaded(-Math.max(0f, Math.min(1f, f))); }

    private HoloColor shaded(float shade)
    {
        if (role == null) return fixed(argb & 0xFF000000 | shade(argb, shade));
        return new HoloColor(role, argb, shade);
    }

    /** Meme couleur, autre alpha. */
    public HoloColor withAlpha(int alpha) { return new HoloColor(role, (alpha & 0xFF) << 24 | argb & 0xFFFFFF, shade); }

    public int alpha() { return argb >>> 24; }

    /** Role du theme, ou {@code null} pour une couleur fixe. */
    public HoloThemeColor role() { return role; }

    /** Vrai si la couleur n'est rien ({@link #NONE}, ou fixe d'alpha nul). */
    public boolean none() { return role == null && argb == 0; }

    /** Couleur {@code 0xAARRGGBB} dans le theme. */
    public int resolve(HoloTheme theme) { return argb & 0xFF000000 | rgb(theme); }

    /** Couleur dans le theme avec un autre alpha ({@code 0..255}). */
    public int resolve(HoloTheme theme, int alpha) { return (alpha & 0xFF) << 24 | rgb(theme); }

    /** Composantes {@code 0xRRGGBB} dans le theme. */
    public int rgb(HoloTheme theme)
    {
        if (role == null) return argb & 0xFFFFFF;
        int rgb = theme.rgb(role);
        return shade == 0f ? rgb : shade(rgb, shade);
    }

    /** {@code rgb} melange vers le blanc ({@code shade > 0}) ou le noir ({@code shade < 0}). */
    public static int shade(int rgb, float shade)
    {
        int r = channel(rgb >> 16 & 0xFF, shade);
        int g = channel(rgb >> 8 & 0xFF, shade);
        int b = channel(rgb & 0xFF, shade);
        return r << 16 | g << 8 | b;
    }

    private static int channel(int c, float shade)
    {
        return shade > 0f ? (int) (c + (255 - c) * shade) : (int) (c * (1f + shade));
    }

    /** Melange lineaire de deux couleurs {@code 0xAARRGGBB} ({@code t = 0} : {@code a}). */
    public static int lerp(int a, int b, float t)
    {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = a >>> shift & 0xFF;
            int cb = b >>> shift & 0xFF;
            result |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return result;
    }
}
