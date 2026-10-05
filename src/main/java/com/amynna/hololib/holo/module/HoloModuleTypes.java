package com.amynna.hololib.holo.module;

import com.amynna.hololib.HoloLib;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

import java.util.function.Supplier;

/**
 * Registre Forge des {@link HoloModuleType}. Un autre mod y ajoute ses modules avec son propre
 * {@code DeferredRegister.create(HoloModuleTypes.REGISTRY_NAME, modid)}. Ni synchronise ni sauvegarde (les modules sont
 * designes par leur identifiant, jamais par un numero).
 */
public final class HoloModuleTypes
{
    public static final ResourceLocation REGISTRY_NAME =
            ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, "module_type");

    /** {@code DeferredRegister} du mod, a inscrire sur le bus du mod dans le constructeur de {@link HoloLib}. */
    public static final DeferredRegister<HoloModuleType<?>> REGISTER =
            DeferredRegister.create(REGISTRY_NAME, HoloLib.MODID);

    private static final Supplier<IForgeRegistry<HoloModuleType<?>>> REGISTRY =
            REGISTER.makeRegistry(() -> new RegistryBuilder<HoloModuleType<?>>().disableSaving().disableSync());

    private HoloModuleTypes() {}

    public static IForgeRegistry<HoloModuleType<?>> registry() { return REGISTRY.get(); }

    /** Type enregistre sous {@code id}, ou {@code null}. */
    public static HoloModuleType<?> get(ResourceLocation id)
    {
        return registry().containsKey(id) ? registry().getValue(id) : null;
    }
}
