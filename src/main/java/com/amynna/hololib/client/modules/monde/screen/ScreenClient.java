package com.amynna.hololib.client.modules.monde.screen;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.levelview.HoloLevelView;
import com.amynna.hololib.client.render.levelview.HoloLevelViews;
import com.amynna.hololib.modules.monde.screen.ScreenModule;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Backend client du module {@code hololib:screen} ({@link ScreenModule.Backend}) : une {@link HoloLevelView} par
 * miroir, ouverte a sa creation, reglee depuis l'etat du module quand sa revision change, fermee a son retrait
 * (deconnexion comprise). Le joueur suivi est retrouve par la vue elle-meme (UUID). Installe au setup client. Fil
 * client.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ScreenClient implements ScreenModule.Backend
{
    private static final Map<ScreenModule, Entry> VIEWS = new IdentityHashMap<>();

    private ScreenClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> ScreenModule.install(new ScreenClient()));
    }

    /** Vue du miroir {@code module}, {@code null} s'il n'en a pas (lue par le renderer de l'ecran). */
    public static HoloLevelView view(ScreenModule module)
    {
        Entry entry = VIEWS.get(module);
        return entry != null ? entry.view : null;
    }

    @Override
    public void attach(ScreenModule module)
    {
        if (VIEWS.containsKey(module)) return;
        Entry entry = new Entry(HoloLevelViews.create());
        VIEWS.put(module, entry);
        apply(module, entry);
    }

    @Override
    public void tick(ScreenModule module)
    {
        Entry entry = VIEWS.get(module);
        if (entry != null && entry.revision != module.revision()) apply(module, entry);
    }

    @Override
    public void detach(ScreenModule module)
    {
        Entry entry = VIEWS.remove(module);
        if (entry != null) entry.view.close();
    }

    private static void apply(ScreenModule module, Entry entry)
    {
        entry.revision = module.revision();
        HoloLevelView view = entry.view;
        if (module.mode() == ScreenModule.Mode.PLAYER) {
            if (module.target() != null) view.setPlayer(module.target());
            else view.setEntity(null);
        } else {
            view.setPosition(module.x(), module.y(), module.z(), module.yaw(), module.pitch(), module.roll());
        }
        view.setFov(module.fov()).setResolution(module.width(), module.height()).setMaxFps(module.maxFps())
                .setViewDistance(module.viewDistance());
    }

    /** Vue d'un miroir et revision de l'etat appliquee. */
    private static final class Entry
    {
        final HoloLevelView view;
        int revision;

        Entry(HoloLevelView view) { this.view = view; }
    }
}
