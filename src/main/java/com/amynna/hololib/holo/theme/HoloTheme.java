package com.amynna.hololib.holo.theme;

import com.amynna.hololib.HoloLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Objects;

/**
 * Theme : regles de rendu des objets {@link HoloThemed}. Un motif de fond des fenetres ({@link HoloThemePattern},
 * designe par son identifiant) et une couleur {@code 0xRRGGBB} par {@linkplain HoloThemeColor role}, sans alpha (la
 * transparence appartient aux objets). Valeur immuable ; les {@code with...} renvoient une copie.
 */
public final class HoloTheme
{
    /** Longueur maximale d'un nom de theme. */
    public static final int MAX_NAME = 24;

    public static final ResourceLocation SOLID = id("solid");
    public static final ResourceLocation SCANLINES = id("scanlines");
    public static final ResourceLocation DIAGONAL = id("diagonal");
    public static final ResourceLocation GRID = id("grid");

    /** Theme standard par defaut : l'apparence du mod avant les themes. */
    public static final HoloTheme DEFAULT = new HoloTheme("Defaut", SOLID,
            new int[] {0x40C0FF, 0x20A0FF, 0x50F070, 0xFFD040, 0xFFFFFF, 0xFF5050});

    /** Theme staff par defaut (fenetres reservees aux ops) : violet a bandes diagonales. */
    public static final HoloTheme DEFAULT_STAFF = new HoloTheme("Staff", DIAGONAL,
            new int[] {0xC080FF, 0x8040E0, 0x50F070, 0xFFD040, 0xFFFFFF, 0xFF5050});

    private final String name;
    private final ResourceLocation pattern;
    private final int[] colors;
    /** Motif resolu dans le registre, a la premiere demande (le registre n'existe pas encore a l'init statique). */
    private HoloThemePattern resolvedPattern;

    private HoloTheme(String name, ResourceLocation pattern, int[] colors)
    {
        this.name = cleanName(name);
        this.pattern = Objects.requireNonNull(pattern);
        this.colors = colors;
        for (int i = 0; i < colors.length; i++) colors[i] &= 0xFFFFFF;
    }

    private static ResourceLocation id(String path)
    {
        return ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, path);
    }

    /** Nom borne a {@link #MAX_NAME} caracteres, sans espaces aux bords ; "Theme" s'il est vide. */
    public static String cleanName(String name)
    {
        String clean = name == null ? "" : name.strip();
        if (clean.length() > MAX_NAME) clean = clean.substring(0, MAX_NAME).strip();
        return clean.isEmpty() ? "Theme" : clean;
    }

    public String name() { return name; }

    /** Identifiant du motif de fond des fenetres. */
    public ResourceLocation patternId() { return pattern; }

    /** Motif de fond des fenetres ({@code hololib:solid} si l'identifiant est inconnu). */
    public HoloThemePattern pattern()
    {
        HoloThemePattern resolved = resolvedPattern;
        if (resolved == null) resolvedPattern = resolved = HoloThemePatterns.getOrDefault(pattern);
        return resolved;
    }

    /** Couleur {@code 0xRRGGBB} du role. */
    public int rgb(HoloThemeColor role) { return colors[role.ordinal()]; }

    /** Couleur du role avec l'alpha {@code 0..255}. */
    public int argb(HoloThemeColor role, int alpha) { return (alpha & 0xFF) << 24 | colors[role.ordinal()]; }

    public HoloTheme withName(String name) { return new HoloTheme(name, pattern, colors.clone()); }

    public HoloTheme withPattern(ResourceLocation pattern) { return new HoloTheme(name, pattern, colors.clone()); }

    public HoloTheme withColor(HoloThemeColor role, int rgb)
    {
        int[] copy = colors.clone();
        copy[role.ordinal()] = rgb;
        return new HoloTheme(name, pattern, copy);
    }

    public void write(FriendlyByteBuf buf)
    {
        buf.writeUtf(name, MAX_NAME * 4);
        buf.writeResourceLocation(pattern);
        for (int color : colors) buf.writeMedium(color);
    }

    public static HoloTheme read(FriendlyByteBuf buf)
    {
        String name = buf.readUtf(MAX_NAME * 4);
        ResourceLocation pattern = buf.readResourceLocation();
        int[] colors = new int[HoloThemeColor.count()];
        for (int i = 0; i < colors.length; i++) colors[i] = buf.readUnsignedMedium();
        return new HoloTheme(name, pattern, colors);
    }

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("pattern", pattern.toString());
        tag.putIntArray("colors", colors.clone());
        return tag;
    }

    /** Theme relu ; composantes manquantes prises dans {@code fallback}. */
    public static HoloTheme load(CompoundTag tag, HoloTheme fallback)
    {
        ResourceLocation pattern = ResourceLocation.tryParse(tag.getString("pattern"));
        int[] saved = tag.getIntArray("colors");
        int[] colors = fallback.colors.clone();
        System.arraycopy(saved, 0, colors, 0, Math.min(saved.length, colors.length));
        return new HoloTheme(tag.contains("name") ? tag.getString("name") : fallback.name,
                pattern != null ? pattern : fallback.pattern, colors);
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof HoloTheme other && name.equals(other.name) && pattern.equals(other.pattern)
                && Arrays.equals(colors, other.colors);
    }

    @Override
    public int hashCode() { return Objects.hash(name, pattern, Arrays.hashCode(colors)); }
}
