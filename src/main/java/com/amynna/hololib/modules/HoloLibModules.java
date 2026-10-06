package com.amynna.hololib.modules;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.module.HoloModuleTarget;
import com.amynna.hololib.holo.module.HoloModuleType;
import com.amynna.hololib.holo.module.HoloModuleTypes;
import com.amynna.hololib.modules.dimension.planet1.Planet1Module;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Enregistrement des modules du mod. */
public final class HoloLibModules
{
    public static final DeferredRegister<HoloModuleType<?>> REGISTER =
            DeferredRegister.create(HoloModuleTypes.REGISTRY_NAME, HoloLib.MODID);

    public static final RegistryObject<HoloModuleType<Planet1Module>> PLANET_1 = REGISTER.register(
            "planet_1", () -> new HoloModuleType<>(HoloModuleTarget.DIMENSION, Planet1Module::new));

    private HoloLibModules() {}
}
