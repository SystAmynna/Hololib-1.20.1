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
    private final HoloInspection inspection = new HoloInspection();

    public HoloMemory(String name)
    {
        this.name = Objects.requireNonNull(name);
    }

    public String name() { return name; }

    /** Modules sauvegardes (liste modifiable, usage par la session). */
    public List<Entry> entries() { return entries; }

    /** Joueurs que le proprietaire peut inspecter (memoire de joueur uniquement ; recopie par la session). */
    public HoloInspection inspection() { return inspection; }

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("type", entry.type().toString());
            entryTag.putUUID("id", entry.instanceId());
            entryTag.put("data", entry.data());
            list.add(entryTag);
        }
        tag.put("modules", list);
        tag.put("inspect", inspection.save());
        return tag;
    }

    public static HoloMemory load(CompoundTag tag)
    {
        HoloMemory memory = new HoloMemory(tag.getString("name"));
        ListTag list = tag.getList("modules", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            ResourceLocation type = ResourceLocation.tryParse(entryTag.getString("type"));
            if (type == null || !entryTag.hasUUID("id")) continue;
            memory.entries.add(new Entry(type, entryTag.getUUID("id"), entryTag.getCompound("data")));
        }
        memory.inspection.load(tag.getCompound("inspect"));
        return memory;
    }
}
