package com.amynna.hololib.client.theme;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.holo.ClientHoloContainer;
import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloThemeEntry;
import com.amynna.hololib.holo.theme.HoloThemeLibrary;
import com.amynna.hololib.holo.theme.HoloThemeSelector;
import com.amynna.hololib.holo.theme.HoloThemeSource;
import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.network.HoloThemesPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Backend client de {@link HoloThemes} : miroir de la bibliotheque du serveur ({@link HoloThemesPacket}) et selection
 * du joueur local, lue en fin de tick client dans sa memoire active (premier module {@link HoloThemeSelector}). Les
 * quatre themes resolus (source x jeu) sont gardes en cache : les renderers les lisent sans allocation. Vide a la
 * deconnexion. Fil client.
 */
public final class ClientThemes implements HoloThemes.Backend
{
    private static final Map<Integer, HoloThemeEntry> ENTRIES = new LinkedHashMap<>();
    private static final Collection<HoloThemeEntry> ENTRIES_VIEW = Collections.unmodifiableCollection(ENTRIES.values());
    /** Themes resolus : {@code [source.ordinal() * 2 + (staff ? 1 : 0)]}. */
    private static final HoloTheme[] RESOLVED = {HoloTheme.DEFAULT, HoloTheme.DEFAULT_STAFF, HoloTheme.DEFAULT,
            HoloTheme.DEFAULT_STAFF};

    private static int serverStandard = HoloThemeLibrary.BUILTIN_STANDARD;
    private static int serverStaff = HoloThemeLibrary.BUILTIN_STAFF;
    private static int playerStandard = HoloThemeSelector.FOLLOW_SERVER;
    private static int playerStaff = HoloThemeSelector.FOLLOW_SERVER;
    private static boolean op;
    private static int revision;
    private static int libraryRevision;
    private static HoloScope.Player scope;

    private ClientThemes() {}

    @Override
    public Collection<HoloThemeEntry> library() { return ENTRIES_VIEW; }

    @Override
    public HoloThemeEntry entry(int id) { return ENTRIES.get(id); }

    @Override
    public int serverSelection(boolean staff) { return staff ? serverStaff : serverStandard; }

    @Override
    public int libraryRevision() { return libraryRevision; }

    @Override
    public boolean localOp() { return op; }

    @Override
    public HoloTheme theme(HoloThemeSource source, boolean staff)
    {
        return RESOLVED[source.ordinal() * 2 + (staff ? 1 : 0)];
    }

    @Override
    public int revision() { return revision; }

    /** Instantane recu du serveur. */
    public static void receive(HoloThemesPacket packet)
    {
        ENTRIES.clear();
        for (HoloThemeEntry entry : packet.entries()) ENTRIES.put(entry.id(), entry);
        serverStandard = packet.standard();
        serverStaff = packet.staff();
        libraryRevision++;
        resolve();
    }

    private static void resolve()
    {
        HoloTheme standard = theme(serverStandard, HoloTheme.DEFAULT);
        HoloTheme staff = theme(serverStaff, HoloTheme.DEFAULT_STAFF);
        set(HoloThemeSource.SERVER, false, standard);
        set(HoloThemeSource.SERVER, true, staff);
        set(HoloThemeSource.CLIENT, false, theme(playerStandard, standard));
        set(HoloThemeSource.CLIENT, true, op ? theme(playerStaff, staff) : staff);
    }

    private static HoloTheme theme(int id, HoloTheme fallback)
    {
        HoloThemeEntry entry = id == HoloThemeSelector.FOLLOW_SERVER ? null : ENTRIES.get(id);
        return entry != null ? entry.theme() : fallback;
    }

    private static void set(HoloThemeSource source, boolean staff, HoloTheme theme)
    {
        int index = source.ordinal() * 2 + (staff ? 1 : 0);
        if (RESOLVED[index] == theme) return;
        RESOLVED[index] = theme;
        revision++;
    }

    // ---------------------------------------------------------------- evenements

    @Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            event.enqueueWork(() -> HoloThemes.install(new ClientThemes()));
        }
    }

    @Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
    public static final class Events
    {
        /** Selection du joueur local et statut op, relus en fin de tick (apres les paquets du tick). */
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event)
        {
            if (event.phase != TickEvent.Phase.END) return;
            LocalPlayer player = Minecraft.getInstance().player;
            int standard = HoloThemeSelector.FOLLOW_SERVER;
            int staff = HoloThemeSelector.FOLLOW_SERVER;
            boolean isOp = false;
            if (player != null) {
                UUID id = player.getUUID();
                if (scope == null || !scope.owner().equals(id)) scope = new HoloScope.Player(id);
                HoloThemeSelector selector = selector(ClientHoloWorld.find(scope));
                if (selector != null) {
                    standard = selector.selectedTheme(false);
                    staff = selector.selectedTheme(true);
                }
                isOp = player.hasPermissions(HoloThemeLibrary.OP_LEVEL);
            }
            if (standard == playerStandard && staff == playerStaff && isOp == op) return;
            playerStandard = standard;
            playerStaff = staff;
            op = isOp;
            resolve();
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
        {
            ENTRIES.clear();
            serverStandard = HoloThemeLibrary.BUILTIN_STANDARD;
            serverStaff = HoloThemeLibrary.BUILTIN_STAFF;
            playerStandard = HoloThemeSelector.FOLLOW_SERVER;
            playerStaff = HoloThemeSelector.FOLLOW_SERVER;
            op = false;
            scope = null;
            libraryRevision++;
            resolve();
        }

        private static HoloThemeSelector selector(ClientHoloContainer container)
        {
            if (container == null) return null;
            for (HoloModule module : container.modules()) {
                if (module instanceof HoloThemeSelector selector) return selector;
            }
            return null;
        }
    }
}
