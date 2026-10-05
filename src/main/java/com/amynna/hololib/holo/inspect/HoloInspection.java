package com.amynna.hololib.holo.inspect;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Joueurs dont un joueur peut voir les affichages (objets {@code PUBLIC} de leur memoire active), enregistre dans
 * chacune de ses memoires de joueur : changer de memoire change les acces. Par defaut, personne.
 * <ul>
 *     <li>{@link #all()} : tous les joueurs, y compris ceux qui se connecteront plus tard, sauf les exclus.</li>
 *     <li>{@link #players()} : joueurs autorises un par un.</li>
 *     <li>{@link #excluded()} : joueurs retires un par un alors que {@code all} est actif.</li>
 * </ul>
 * Simple donnee : la modification passe par {@link HoloInspections}, qui synchronise les clients.
 */
public final class HoloInspection
{
    private boolean all;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> excluded = new LinkedHashSet<>();

    public boolean all() { return all; }

    public Set<UUID> players() { return Collections.unmodifiableSet(players); }

    public Set<UUID> excluded() { return Collections.unmodifiableSet(excluded); }

    /** Vrai si le joueur {@code target} est visible. */
    public boolean allows(UUID target)
    {
        return players.contains(target) || (all && !excluded.contains(target));
    }

    /** Autorise ou retire un joueur ; faux si l'acces etait deja dans cet etat. */
    public boolean set(UUID target, boolean enable)
    {
        boolean before = allows(target);
        if (enable) {
            players.add(target);
            excluded.remove(target);
        } else {
            players.remove(target);
            if (all) excluded.add(target);
        }
        return before != allows(target);
    }

    /**
     * Active l'acces a tous les joueurs (et oublie les exclusions), ou retire tous les acces (drapeau et liste) ; faux
     * si rien n'a change.
     */
    public boolean setAll(boolean enable)
    {
        boolean changed = all != enable || !excluded.isEmpty() || (!enable && !players.isEmpty());
        all = enable;
        excluded.clear();
        if (!enable) players.clear();
        return changed;
    }

    public void copyFrom(HoloInspection other)
    {
        all = other.all;
        players.clear();
        players.addAll(other.players);
        excluded.clear();
        excluded.addAll(other.excluded);
    }

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("all", all);
        tag.put("players", saveSet(players));
        tag.put("excluded", saveSet(excluded));
        return tag;
    }

    public void load(CompoundTag tag)
    {
        all = tag.getBoolean("all");
        loadSet(tag.getList("players", Tag.TAG_INT_ARRAY), players);
        loadSet(tag.getList("excluded", Tag.TAG_INT_ARRAY), excluded);
    }

    private static ListTag saveSet(Set<UUID> set)
    {
        ListTag list = new ListTag();
        for (UUID id : set) list.add(NbtUtils.createUUID(id));
        return list;
    }

    private static void loadSet(ListTag list, Set<UUID> set)
    {
        set.clear();
        for (Tag tag : list) {
            try {
                set.add(NbtUtils.loadUUID(tag));
            } catch (IllegalArgumentException e) {
                // UUID invalide : ignore.
            }
        }
    }
}
