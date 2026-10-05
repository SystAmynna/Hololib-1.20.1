package com.amynna.hololib.holo.memory.player;

import com.amynna.hololib.holo.memory.HoloMemory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Memoires de joueur : ensemble des {@link HoloMemory} d'un joueur, dont une seule est active (toujours chargee tant
 * que le joueur est connecte). Capability du joueur, cote serveur ({@link PlayerMemoryCapability}). Pour changer de
 * memoire en jeu, passer par {@code HoloContainers.selectMemory}, qui decharge et recharge les modules.
 */
public final class HoloMemoryBank
{
    public static final String DEFAULT_NAME = "default";
    public static final int MAX_NAME_LENGTH = 32;

    private final List<HoloMemory> memories = new ArrayList<>();
    private final List<HoloMemory> memoriesView = Collections.unmodifiableList(memories);
    private int active;

    public HoloMemoryBank()
    {
        memories.add(new HoloMemory(DEFAULT_NAME));
    }

    /** Banque du joueur ; leve une exception si la capability manque (joueur client). */
    public static HoloMemoryBank of(ServerPlayer player)
    {
        return player.getCapability(PlayerMemoryCapability.BANK)
                .orElseThrow(() -> new IllegalStateException("HoloMemoryBank absente pour " + player));
    }

    public static boolean validName(String name)
    {
        return name != null && !name.isEmpty() && name.length() <= MAX_NAME_LENGTH;
    }

    public List<HoloMemory> memories() { return memoriesView; }

    public HoloMemory active() { return memories.get(active); }

    public HoloMemory find(String name)
    {
        for (HoloMemory memory : memories) if (memory.name().equals(name)) return memory;
        return null;
    }

    /** Cree une memoire vide ; {@code null} si le nom est invalide ou deja pris. */
    public HoloMemory create(String name)
    {
        if (!validName(name) || find(name) != null) return null;
        HoloMemory memory = new HoloMemory(name);
        memories.add(memory);
        return memory;
    }

    /** Supprime une memoire inactive ; faux si elle n'existe pas ou est active. */
    public boolean delete(String name)
    {
        HoloMemory memory = find(name);
        if (memory == null || memory == active()) return false;
        HoloMemory current = active();
        memories.remove(memory);
        active = memories.indexOf(current);
        return true;
    }

    /** Usage interne (session) : change l'indice de la memoire active ; faux si elle n'existe pas. */
    boolean select(String name)
    {
        HoloMemory memory = find(name);
        if (memory == null) return false;
        active = memories.indexOf(memory);
        return true;
    }

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (HoloMemory memory : memories) list.add(memory.save());
        tag.put("memories", list);
        tag.putInt("active", active);
        return tag;
    }

    public void load(CompoundTag tag)
    {
        memories.clear();
        ListTag list = tag.getList("memories", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            HoloMemory memory = HoloMemory.load(list.getCompound(i));
            if (validName(memory.name()) && find(memory.name()) == null) memories.add(memory);
        }
        if (memories.isEmpty()) memories.add(new HoloMemory(DEFAULT_NAME));
        active = Math.max(0, Math.min(tag.getInt("active"), memories.size() - 1));
    }
}
