package com.amynna.hololib.holo.theme;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.network.HoloNetwork;
import com.amynna.hololib.network.HoloThemesPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Bibliotheque des themes du serveur ({@code data/hololib_themes.dat} de l'overworld) : themes nommes partages par
 * tous, et les deux selections du serveur (standard, staff). Fil serveur.
 * <p>
 * Deux themes integres, jamais supprimes : "Defaut" ({@value #BUILTIN_STANDARD}) et "Staff" ({@value #BUILTIN_STAFF}).
 * Un joueur cree et edite ses propres themes (au plus {@value #MAX_PER_PLAYER}) ; un configurateur admin (op verifie
 * par l'appelant) edite tous les themes et les selections du serveur. Chaque changement est envoye a tous les
 * joueurs au tick suivant ({@link HoloThemesPacket}, instantane complet) ; a la connexion, le joueur recoit
 * l'instantane.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID)
public final class HoloThemeLibrary extends SavedData
{
    public static final int BUILTIN_STANDARD = 0;
    public static final int BUILTIN_STAFF = 1;
    public static final int MAX_THEMES = 64;
    public static final int MAX_PER_PLAYER = 8;
    /** Niveau de permission des configurateurs admin. */
    public static final int OP_LEVEL = 2;

    private static final String NAME = HoloLib.MODID + "_themes";

    private final Map<Integer, HoloThemeEntry> entries = new LinkedHashMap<>();
    private final Collection<HoloThemeEntry> entriesView = Collections.unmodifiableCollection(entries.values());
    private int nextId = 2;
    private int standard = BUILTIN_STANDARD;
    private int staff = BUILTIN_STAFF;
    /** Changement a envoyer a tous au prochain tick. */
    private boolean pending;

    private HoloThemeLibrary()
    {
        entries.put(BUILTIN_STANDARD, new HoloThemeEntry(BUILTIN_STANDARD, null, null, HoloTheme.DEFAULT));
        entries.put(BUILTIN_STAFF, new HoloThemeEntry(BUILTIN_STAFF, null, null, HoloTheme.DEFAULT_STAFF));
    }

    public static HoloThemeLibrary of(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(HoloThemeLibrary::load, HoloThemeLibrary::new,
                NAME);
    }

    /** Themes, dans l'ordre de creation (vue non modifiable). */
    public Collection<HoloThemeEntry> entries() { return entriesView; }

    public HoloThemeEntry get(int id) { return entries.get(id); }

    /** Identifiant du theme selectionne par le serveur. */
    public int selected(boolean staff) { return staff ? this.staff : standard; }

    /** Vrai pour un theme integre (non supprimable). */
    public static boolean builtin(int id) { return id == BUILTIN_STANDARD || id == BUILTIN_STAFF; }

    /** Vrai si {@code player} peut modifier l'entree : un admin tout, un joueur ses propres themes. */
    public static boolean mayEdit(UUID player, boolean admin, HoloThemeEntry entry)
    {
        return entry != null && (admin || player.equals(entry.creator()));
    }

    /**
     * Nouveau theme, copie de {@code from} renommee ; {@code creator == null} = theme du serveur. {@code null} si une
     * borne est atteinte.
     */
    public HoloThemeEntry create(ServerPlayer creator, String name, HoloTheme from)
    {
        if (entries.size() >= MAX_THEMES) return null;
        UUID id = creator != null ? creator.getUUID() : null;
        if (id != null && entries.values().stream().filter(e -> id.equals(e.creator())).count() >= MAX_PER_PLAYER) {
            return null;
        }
        HoloThemeEntry entry = new HoloThemeEntry(nextId++, id, creator != null ? creator.getGameProfile().getName()
                : null, from.withName(name));
        entries.put(entry.id(), entry);
        changed();
        return entry;
    }

    public boolean rename(int id, String name) { return replace(id, entry -> entry.theme().withName(name)); }

    public boolean setColor(int id, HoloThemeColor role, int rgb)
    {
        return replace(id, entry -> entry.theme().withColor(role, rgb));
    }

    public boolean setPattern(int id, ResourceLocation pattern)
    {
        if (!HoloThemePatterns.exists(pattern)) return false;
        return replace(id, entry -> entry.theme().withPattern(pattern));
    }

    /** Supprime un theme (pas un integre) ; une selection du serveur qui le designait revient a l'integre. */
    public boolean delete(int id)
    {
        if (builtin(id) || entries.remove(id) == null) return false;
        if (standard == id) standard = BUILTIN_STANDARD;
        if (staff == id) staff = BUILTIN_STAFF;
        changed();
        return true;
    }

    /** Selection du serveur pour le jeu standard ou staff. */
    public boolean select(boolean staff, int id)
    {
        if (!entries.containsKey(id) || selected(staff) == id) return false;
        if (staff) this.staff = id;
        else standard = id;
        changed();
        return true;
    }

    private boolean replace(int id, Function<HoloThemeEntry, HoloTheme> edit)
    {
        HoloThemeEntry entry = entries.get(id);
        if (entry == null) return false;
        HoloTheme theme = edit.apply(entry);
        if (theme.equals(entry.theme())) return false;
        entries.put(id, entry.with(theme));
        changed();
        return true;
    }

    private void changed()
    {
        pending = true;
        setDirty();
    }

    /** Instantane complet pour les clients. */
    public HoloThemesPacket snapshot()
    {
        return new HoloThemesPacket(List.copyOf(entries.values()), standard, staff);
    }

    // ---------------------------------------------------------------- evenements

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        HoloThemeLibrary library = of(event.getServer());
        if (!library.pending) return;
        library.pending = false;
        HoloNetwork.sendToAll(library.snapshot());
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) HoloNetwork.send(player, of(player.server).snapshot());
    }

    // ---------------------------------------------------------------- sauvegarde

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();
        for (HoloThemeEntry entry : entries.values()) list.add(entry.save());
        tag.put("themes", list);
        tag.putInt("nextId", nextId);
        tag.putInt("standard", standard);
        tag.putInt("staff", staff);
        return tag;
    }

    private static HoloThemeLibrary load(CompoundTag tag)
    {
        HoloThemeLibrary library = new HoloThemeLibrary();
        ListTag list = tag.getList("themes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            HoloThemeEntry entry = HoloThemeEntry.load(list.getCompound(i));
            if (entry.id() < 0 || library.entries.size() >= MAX_THEMES && !builtin(entry.id())) continue;
            // Un integre sauvegarde n'a jamais de createur.
            if (builtin(entry.id())) entry = new HoloThemeEntry(entry.id(), null, null, entry.theme());
            library.entries.put(entry.id(), entry);
            library.nextId = Math.max(library.nextId, entry.id() + 1);
        }
        library.nextId = Math.max(library.nextId, tag.getInt("nextId"));
        int standard = tag.contains("standard") ? tag.getInt("standard") : BUILTIN_STANDARD;
        int staff = tag.contains("staff") ? tag.getInt("staff") : BUILTIN_STAFF;
        library.standard = library.entries.containsKey(standard) ? standard : BUILTIN_STANDARD;
        library.staff = library.entries.containsKey(staff) ? staff : BUILTIN_STAFF;
        return library;
    }
}
