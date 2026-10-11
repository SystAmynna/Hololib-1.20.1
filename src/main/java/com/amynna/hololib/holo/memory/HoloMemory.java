package com.amynna.hololib.holo.memory;

import com.amynna.hololib.holo.inspect.HoloInspection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Sauvegarde de l'etat d'un ensemble de modules : memoire de joueur, de dimension ou de position. Ne contient que des
 * donnees, jamais de reference vers un module vivant : pendant que la memoire est chargee, l'etat de reference est dans
 * son {@link HoloContainer}, recopie ici a chaque sauvegarde. Une memoire de joueur porte aussi ses acces
 * d'inspection ({@link #inspection()}).
 * <p>
 * Deux listes : les modules <b>equipes</b> ({@link #entries()}) et les sauvegardes <b>dormantes</b>
 * ({@link #dormant()}) de modules retires, gardees pour etre relues a leur reinstallation.
 */
public final class HoloMemory
{
    /**
     * Module sauvegarde.
     *
     * @param data etat complet ({@code HoloModule.saveAll})
     */
    public record Entry(ResourceLocation type, UUID instanceId, CompoundTag data) {}

    private final String name;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> dormant = new ArrayList<>();
    private final HoloInspection inspection = new HoloInspection();

    public HoloMemory(String name)
    {
        this.name = Objects.requireNonNull(name);
    }

    public String name() { return name; }

    /** Modules sauvegardes (liste modifiable, usage par la session). */
    public List<Entry> entries() { return entries; }

    /** Sauvegardes de modules retires, au plus une par type (liste modifiable, usage par la session). */
    public List<Entry> dormant() { return dormant; }

    /** Joueurs que le proprietaire peut inspecter (memoire de joueur uniquement ; recopie par la session). */
    public HoloInspection inspection() { return inspection; }

    /** Copie profonde sous un autre nom : modules equipes, sauvegardes dormantes et acces d'inspection. */
    public HoloMemory copy(String name)
    {
        // save() partage les donnees des entrees : copy() les duplique.
        CompoundTag tag = save().copy();
        tag.putString("name", name);
        return load(tag);
    }

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.put("modules", saveEntries(entries));
        if (!dormant.isEmpty()) tag.put("dormant", saveEntries(dormant));
        tag.put("inspect", inspection.save());
        return tag;
    }

    public static HoloMemory load(CompoundTag tag)
    {
        HoloMemory memory = new HoloMemory(tag.getString("name"));
        loadEntries(tag.getList("modules", Tag.TAG_COMPOUND), memory.entries);
        loadEntries(tag.getList("dormant", Tag.TAG_COMPOUND), memory.dormant);
        memory.inspection.load(tag.getCompound("inspect"));
        return memory;
    }

    private static ListTag saveEntries(List<Entry> entries)
    {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("type", entry.type().toString());
            entryTag.putUUID("id", entry.instanceId());
            entryTag.put("data", entry.data());
            list.add(entryTag);
        }
        return list;
    }

    private static void loadEntries(ListTag list, List<Entry> into)
    {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            ResourceLocation type = ResourceLocation.tryParse(entryTag.getString("type"));
            if (type == null || !entryTag.hasUUID("id")) continue;
            into.add(new Entry(type, entryTag.getUUID("id"), entryTag.getCompound("data")));
        }
    }
}
