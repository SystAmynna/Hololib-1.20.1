package com.amynna.hololib.client.modules.camera.spacepilot;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.client.holo.HoloModes;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.modules.camera.spacepilot.SpacePilotModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Module {@code hololib:space_pilot} du <b>joueur local</b> (memoire active recue), pour les renderers de Space
 * Pilot : jamais celui d'un joueur inspecte, qui n'est d'ailleurs pas envoye (C45). Fil client.
 * <p>
 * Recherche faite une fois par tick, en fin de tick client
 * ({@code HoloModes.find(ClientHoloWorld.find(portee du joueur local), SpacePilotModule.class)}) : les paquets qui
 * ajoutent ou retirent le module sont traites avant le tick, donc la valeur lue pendant les frames qui suivent est a
 * jour ; {@link #module()} ne fait que la rendre (aucune allocation par frame). La portee du joueur local n'est
 * recreee que si son UUID change.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class SpacePilotClient
{
    private static HoloScope.Player scope;
    private static SpacePilotModule module;

    private SpacePilotClient() {}

    /** Module Space Pilot de la memoire active du joueur local, ou {@code null} (absent, hors partie). */
    public static SpacePilotModule module() { return module; }

    /** Vrai si le joueur local a Space Pilot et que son interrupteur general (Quick Action) est actif. */
    public static boolean enabled() { return module != null && module.enabled(); }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            scope = null;
            module = null;
            return;
        }
        UUID id = player.getUUID();
        if (scope == null || !scope.owner().equals(id)) scope = new HoloScope.Player(id);
        module = HoloModes.find(ClientHoloWorld.find(scope), SpacePilotModule.class);
    }
}
