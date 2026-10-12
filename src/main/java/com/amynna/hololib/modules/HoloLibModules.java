package com.amynna.hololib.modules;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.module.HoloModuleTarget;
import com.amynna.hololib.holo.module.HoloModuleType;
import com.amynna.hololib.holo.module.HoloModuleTypes;
import com.amynna.hololib.modules.camera.attackguard.AttackGuardModule;
import com.amynna.hololib.modules.camera.config.ConfigModule;
import com.amynna.hololib.modules.camera.crosshaircancel.CrosshairCancelModule;
import com.amynna.hololib.modules.camera.editor.DimensionEditorModule;
import com.amynna.hololib.modules.camera.editor.EditorPanelModule;
import com.amynna.hololib.modules.camera.editor.OtherEditorModule;
import com.amynna.hololib.modules.camera.editor.OwnDynEditorModule;
import com.amynna.hololib.modules.camera.editor.OwnFixEditorModule;
import com.amynna.hololib.modules.camera.editor.OwnHudEditorModule;
import com.amynna.hololib.modules.camera.editor.WorldEditorModule;
import com.amynna.hololib.modules.camera.focus.FocusModule;
import com.amynna.hololib.modules.camera.keys.KeysModule;
import com.amynna.hololib.modules.camera.keyseditor.KeysEditorModule;
import com.amynna.hololib.modules.camera.quickaction.QuickActionModule;
import com.amynna.hololib.modules.camera.radar.RadarModule;
import com.amynna.hololib.modules.camera.spaceknowledge.SpaceKnowledgeModule;
import com.amynna.hololib.modules.camera.spacepilot.SpacePilotModule;
import com.amynna.hololib.modules.camera.targeting.TargetingModule;
import com.amynna.hololib.modules.camera.themeconfigurator.AdminThemeConfiguratorModule;
import com.amynna.hololib.modules.camera.themeconfigurator.ThemeConfiguratorModule;
import com.amynna.hololib.modules.dimension.planet1.Planet1Module;
import com.amynna.hololib.modules.dimension.skycancel.SkyCancelModule;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.monde.areaconfigurator.AreaConfiguratorModule;
import com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerModule;
import com.amynna.hololib.modules.monde.spaceconfigurator.SpaceConfiguratorModule;
import com.amynna.hololib.modules.monde.spacecontroller.SpaceControllerModule;
import com.amynna.hololib.modules.monde.spacemap.SpaceMapModule;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Enregistrement des modules du mod. */
public final class HoloLibModules
{
    public static final DeferredRegister<HoloModuleType<?>> REGISTER =
            DeferredRegister.create(HoloModuleTypes.REGISTRY_NAME, HoloLib.MODID);

    public static final RegistryObject<HoloModuleType<Planet1Module>> PLANET_1 = REGISTER.register(
            "planet_1", () -> new HoloModuleType<>(HoloModuleTarget.DIMENSION, Planet1Module::new));
    public static final RegistryObject<HoloModuleType<SpaceEnvironmentModule>> SPACE_ENVIRONMENT = REGISTER.register(
            "space_environment", () -> new HoloModuleType<>(HoloModuleTarget.DIMENSION, SpaceEnvironmentModule::new));
    public static final RegistryObject<HoloModuleType<SpaceControllerModule>> SPACE_CONTROLLER = REGISTER.register(
            "space_controller", () -> new HoloModuleType<>(HoloModuleTarget.POSITION, SpaceControllerModule::new));
    public static final RegistryObject<HoloModuleType<SpaceConfiguratorModule>> SPACE_CONFIGURATOR =
            REGISTER.register("space_configurator",
                    () -> new HoloModuleType<>(HoloModuleTarget.POSITION, SpaceConfiguratorModule::new));
    public static final RegistryObject<HoloModuleType<SpaceMapModule>> SPACE_MAP = REGISTER.register(
            "space_map", () -> new HoloModuleType<>(HoloModuleTarget.POSITION, SpaceMapModule::new));
    public static final RegistryObject<HoloModuleType<AreaDisplayerModule>> AREA_DISPLAYER = REGISTER.register(
            "area_displayer", () -> new HoloModuleType<>(HoloModuleTarget.POSITION, AreaDisplayerModule::new));
    public static final RegistryObject<HoloModuleType<AreaConfiguratorModule>> AREA_DISPLAYER_CONFIGURATOR =
            REGISTER.register("area_displayer_configurator",
                    () -> new HoloModuleType<>(HoloModuleTarget.POSITION, AreaConfiguratorModule::new));
    public static final RegistryObject<HoloModuleType<SpacePilotModule>> SPACE_PILOT = REGISTER.register(
            "space_pilot", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, SpacePilotModule::new));
    public static final RegistryObject<HoloModuleType<SpaceKnowledgeModule>> SPACE_KNOWLEDGE = REGISTER.register(
            "space_knowledge", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, SpaceKnowledgeModule::new));
    public static final RegistryObject<HoloModuleType<FocusModule>> FOCUS = REGISTER.register(
            "focus", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, FocusModule::new));
    public static final RegistryObject<HoloModuleType<OwnFixEditorModule>> OWN_FIX_EDITOR = REGISTER.register(
            "own_fix_editor", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, OwnFixEditorModule::new));
    public static final RegistryObject<HoloModuleType<OwnDynEditorModule>> OWN_DYN_EDITOR = REGISTER.register(
            "own_dyn_editor", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, OwnDynEditorModule::new));
    public static final RegistryObject<HoloModuleType<OwnHudEditorModule>> OWN_HUD_EDITOR = REGISTER.register(
            "own_hud_editor", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, OwnHudEditorModule::new));
    public static final RegistryObject<HoloModuleType<WorldEditorModule>> WORLD_EDITOR = REGISTER.register(
            "world_editor", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, WorldEditorModule::new));
    public static final RegistryObject<HoloModuleType<DimensionEditorModule>> DIMENSION_EDITOR = REGISTER.register(
            "dimension_editor", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, DimensionEditorModule::new));
    public static final RegistryObject<HoloModuleType<OtherEditorModule>> OTHER_EDITOR = REGISTER.register(
            "other_editor", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, OtherEditorModule::new));
    public static final RegistryObject<HoloModuleType<EditorPanelModule>> EDITOR_PANEL = REGISTER.register(
            "editor_panel", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, EditorPanelModule::new));
    public static final RegistryObject<HoloModuleType<ConfigModule>> CONFIG = REGISTER.register(
            "config", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, ConfigModule::new));
    public static final RegistryObject<HoloModuleType<QuickActionModule>> QUICK_ACTION = REGISTER.register(
            "quick_action", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, QuickActionModule::new));
    public static final RegistryObject<HoloModuleType<SkyCancelModule>> SKY_CANCEL = REGISTER.register(
            "sky_cancel", () -> new HoloModuleType<>(HoloModuleTarget.DIMENSION, SkyCancelModule::new));
    public static final RegistryObject<HoloModuleType<CrosshairCancelModule>> CROSSHAIR_CANCEL = REGISTER.register(
            "crosshair_cancel", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, CrosshairCancelModule::new));
    public static final RegistryObject<HoloModuleType<RadarModule>> RADAR = REGISTER.register(
            "radar", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, RadarModule::new));
    public static final RegistryObject<HoloModuleType<TargetingModule>> TARGETING = REGISTER.register(
            "targeting", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, TargetingModule::new));
    public static final RegistryObject<HoloModuleType<KeysModule>> KEYS = REGISTER.register(
            "keys", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, KeysModule::new));
    public static final RegistryObject<HoloModuleType<KeysEditorModule>> KEYS_EDITOR = REGISTER.register(
            "keys_editor", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, KeysEditorModule::new));
    public static final RegistryObject<HoloModuleType<ThemeConfiguratorModule>> THEME_CONFIGURATOR = REGISTER.register(
            "theme_configurator", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, ThemeConfiguratorModule::new));
    public static final RegistryObject<HoloModuleType<AdminThemeConfiguratorModule>> ADMIN_THEME_CONFIGURATOR =
            REGISTER.register("admin_theme_configurator",
                    () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, AdminThemeConfiguratorModule::new));
    public static final RegistryObject<HoloModuleType<AttackGuardModule>> ATTACK_GUARD = REGISTER.register(
            "attack_guard", () -> new HoloModuleType<>(HoloModuleTarget.PLAYER, AttackGuardModule::new));

    private HoloLibModules() {}
}
