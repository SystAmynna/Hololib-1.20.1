package com.amynna.hololib.client.modules.camera.radar;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.client.holo.HoloModes;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.modules.camera.radar.RadarModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.List;
import java.util.UUID;

/**
 * Radar du <b>joueur local</b> : module {@code hololib:radar} de sa memoire active (jamais celui d'un joueur inspecte)
 * et etat de l'onde ({@link RadarScan}). Fil client. Meme patron que {@code SpaceKnowledgeClient} : module recherche
 * une fois par tick, en fin de tick client.
 * <p>
 * Implemente le {@link RadarModule.Backend} (installe au setup client) : Activer fige l'origine a la position du
 * joueur et relance l'onde, Desactiver oublie tout. Le radar s'arrete aussi sans module, hors partie ou au changement
 * de niveau.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class RadarClient implements RadarModule.Backend
{
    private static final RadarScan SCAN = new RadarScan();
    private static final String IDLE = "Radar : inactif";

    private static HoloScope.Player scope;
    private static RadarModule module;

    // Texte d'etat : recree seulement quand les valeurs affichees changent.
    private static String status = IDLE;
    private static int shownRadius = -1;
    private static int shownCount = -1;
    private static boolean shownGrowing;

    private RadarClient() {}

    /** Etat du radar local (lu par le renderer). */
    public static RadarScan scan() { return SCAN; }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            scope = null;
            module = null;
            SCAN.clear();
            return;
        }
        UUID id = player.getUUID();
        if (scope == null || !scope.owner().equals(id)) scope = new HoloScope.Player(id);
        module = HoloModes.find(ClientHoloWorld.find(scope), RadarModule.class);
        if (module == null) {
            SCAN.clear();
            return;
        }
        SCAN.tick(level, player, module);
    }

    // ---------------------------------------------------------------- Backend

    @Override
    public void activate(RadarModule module)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        SCAN.start(mc.level, mc.player.position(), module.radius());
    }

    @Override
    public void deactivate(RadarModule module) { SCAN.clear(); }

    @Override
    public List<? extends Entity> detected() { return SCAN.detected(); }

    @Override
    public String status()
    {
        if (!SCAN.active()) {
            status = IDLE;
            shownRadius = -1;
            return status;
        }
        boolean growing = SCAN.growing();
        int radius = (int) SCAN.radius(0f);
        int count = SCAN.detected().size();
        if (radius != shownRadius || count != shownCount || growing != shownGrowing || status == IDLE) {
            shownRadius = radius;
            shownCount = count;
            shownGrowing = growing;
            String found = count + (count > 1 ? " reperes" : " repere");
            status = growing ? "Onde " + radius + " / " + SCAN.maxRadius() + " - " + found : "Actif - " + found;
        }
        return status;
    }

    /** Installation du backend au setup client. */
    @Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup
    {
        private Setup() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            event.enqueueWork(() -> RadarModule.install(new RadarClient()));
        }
    }
}
