package com.amynna.hololib.holo.theme;

import com.amynna.hololib.HoloLib;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

import java.util.function.Supplier;

/**
 * Registre Forge des {@link HoloThemePattern}. Un autre mod y ajoute ses motifs avec son propre
 * {@code DeferredRegister.create(HoloThemePatterns.REGISTRY_NAME, modid)}. Ni synchronise ni sauvegarde : un theme
 * designe son motif par identifiant, et un motif inconnu (mod absent d'un cote) est remplace par
 * {@link HoloTheme#SOLID}.
 */
public final class HoloThemePatterns
{
    public static final ResourceLocation REGISTRY_NAME =
            ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, "theme_pattern");

    /** {@code DeferredRegister} du registre, a inscrire sur le bus du mod dans le constructeur de {@link HoloLib}. */
    public static final DeferredRegister<HoloThemePattern> REGISTER =
            DeferredRegister.create(REGISTRY_NAME, HoloLib.MODID);

    private static final Supplier<IForgeRegistry<HoloThemePattern>> REGISTRY =
            REGISTER.makeRegistry(() -> new RegistryBuilder<HoloThemePattern>().disableSaving().disableSync());

    private HoloThemePatterns() {}

    public static IForgeRegistry<HoloThemePattern> registry() { return REGISTRY.get(); }

    /** Motif enregistre sous {@code id}, ou {@code null}. */
    public static HoloThemePattern get(ResourceLocation id)
    {
        return registry().containsKey(id) ? registry().getValue(id) : null;
    }

    /** Motif enregistre sous {@code id}, sinon le motif uni. */
    public static HoloThemePattern getOrDefault(ResourceLocation id)
    {
        HoloThemePattern pattern = get(id);
        return pattern != null ? pattern : registry().getValue(HoloTheme.SOLID);
    }

    /** Vrai si le motif existe (validation des demandes clients). */
    public static boolean exists(ResourceLocation id) { return id != null && registry().containsKey(id); }
}
