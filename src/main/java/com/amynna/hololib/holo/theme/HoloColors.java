package com.amynna.hololib.holo.theme;

import net.minecraft.util.Mth;

import java.util.Locale;

/** Conversions de couleurs : texte {@code #rrggbb}, teinte / saturation / valeur. Code commun. */
public final class HoloColors
{
    private HoloColors() {}

    /** {@code #rrggbb} (ou {@code rrggbb}, casse libre) -> {@code 0xRRGGBB} ; -1 si le texte est invalide. */
    public static int parseHex(String text)
    {
        if (text == null) return -1;
        String hex = text.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() != 6) return -1;
        int value = 0;
        for (int i = 0; i < 6; i++) {
            int digit = Character.digit(hex.charAt(i), 16);
            if (digit < 0) return -1;
            value = value << 4 | digit;
        }
        return value;
    }

    /** {@code 0xRRGGBB} -> {@code #rrggbb}. */
    public static String toHex(int rgb) { return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF); }

    /** {@code 0xRRGGBB} -> {@code {teinte, saturation, valeur}} dans {@code 0..1}, ecrit dans {@code out}. */
    public static float[] rgbToHsv(int rgb, float[] out)
    {
        float r = (rgb >> 16 & 0xFF) / 255f;
        float g = (rgb >> 8 & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta <= 0f) hue = 0f;
        else if (max == r) hue = ((g - b) / delta) / 6f;
        else if (max == g) hue = ((b - r) / delta + 2f) / 6f;
        else hue = ((r - g) / delta + 4f) / 6f;
        if (hue < 0f) hue += 1f;
        out[0] = hue;
        out[1] = max <= 0f ? 0f : delta / max;
        out[2] = max;
        return out;
    }

    /** Teinte, saturation, valeur ({@code 0..1}) -> {@code 0xRRGGBB}. */
    public static int hsvToRgb(float hue, float saturation, float value)
    {
        float h = hue - (float) Math.floor(hue);
        return Mth.hsvToRgb(h >= 1f ? 0f : h, Mth.clamp(saturation, 0f, 1f), Mth.clamp(value, 0f, 1f)) & 0xFFFFFF;
    }
}
