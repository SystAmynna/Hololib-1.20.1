package com.amynna.hololib.demo;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.module.HoloModuleTarget;
import com.amynna.hololib.holo.module.HoloModuleType;
import com.amynna.hololib.holo.module.HoloModuleTypes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Enregistrement des modules de demonstration, comme le ferait un mod tiers. */
public final class DemoModules
{
    public static final DeferredRegister<HoloModuleType<?>> REGISTER =
            DeferredRegister.create(HoloModuleTypes.REGISTRY_NAME, HoloLib.MODID);

    public static final RegistryObject<HoloModuleType<DemoDimensionModule>> DEMO_DIMENSION = REGISTER.register(
            "demo_dimension", () -> new HoloModuleType<>(HoloModuleTarget.DIMENSION, DemoDimensionModule::new));
    public static final RegistryObject<HoloModuleType<DemoWorldPosModule>> DEMO_WORLD_POS = REGISTER.register(
            "demo_world_pos", () -> new HoloModuleType<>(HoloModuleTarget.POSITION, DemoWorldPosModule::new));
    public static final RegistryObject<HoloModuleType<DemoEditableCubeModule>> DEMO_EDITABLE_CUBE = REGISTER.register(
            "demo_editable_cube", () -> new HoloModuleType<>(HoloModuleTarget.POSITION, DemoEditableCubeModule::new));
    public static final RegistryObject<HoloModuleType<DemoPlayerModule>> DEMO_PLAYER = REGISTER.register(
            "demo_player", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, DemoPlayerModule::new));
    public static final RegistryObject<HoloModuleType<DemoInteractionModule>> DEMO_INTERACTION = REGISTER.register(
            "demo_interaction", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, DemoInteractionModule::new));
    public static final RegistryObject<HoloModuleType<DemoKeyboardModule>> DEMO_KEYBOARD = REGISTER.register(
            "demo_keyboard", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, DemoKeyboardModule::new));

    private DemoModules() {}
}
