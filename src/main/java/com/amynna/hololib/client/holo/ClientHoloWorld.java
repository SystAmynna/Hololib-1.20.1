package com.amynna.hololib.client.holo;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.render.holo.HoloRouting;
import com.amynna.hololib.holo.memory.HoloScope;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Etat holographique connu du client : un {@link ClientHoloContainer} par portee recue (memoire du joueur local,
 * memoires des joueurs suivis, de la dimension, des markers dont le chunk est suivi). Fil client uniquement ;
 * alimente par {@link ClientHoloPackets}.
 * <p>
 * Au respawn et au changement de dimension, tout est oublie sauf la memoire du joueur local : le serveur renvoie la
 * dimension, et les entites et chunks suivis au fur et a mesure.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class ClientHoloWorld
{
    private static final Map<HoloScope, ClientHoloContainer> CONTAINERS = new LinkedHashMap<>();
    private static final Collection<ClientHoloContainer> CONTAINERS_VIEW =
            Collections.unmodifiableCollection(CONTAINERS.values());

    private ClientHoloWorld() {}

    public static Collection<ClientHoloContainer> containers() { return CONTAINERS_VIEW; }

    static ClientHoloContainer container(HoloScope scope)
    {
        return CONTAINERS.computeIfAbsent(scope, ClientHoloContainer::new);
    }

    static ClientHoloContainer existing(HoloScope scope) { return CONTAINERS.get(scope); }

    static void forget(HoloScope scope)
    {
        ClientHoloContainer container = CONTAINERS.remove(scope);
        if (container != null) container.clear();
    }

    /** Oublie toutes les portees sauf {@code keep} (peut etre nul). */
    private static void forgetAllBut(HoloScope keep)
    {
        Iterator<ClientHoloContainer> iterator = CONTAINERS.values().iterator();
        while (iterator.hasNext()) {
            ClientHoloContainer container = iterator.next();
            if (container.scope().equals(keep)) continue;
            container.clear();
            iterator.remove();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) {
            for (ClientHoloContainer container : CONTAINERS.values()) container.tick();
        }
        HoloRouting.refresh(mc);
    }

    /** Respawn ou changement de dimension (traite avant les paquets qui suivent). */
    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event)
    {
        forgetAllBut(new HoloScope.Player(event.getNewPlayer().getUUID()));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        forgetAllBut(null);
        HoloRouting.refresh(Minecraft.getInstance());
    }
}
