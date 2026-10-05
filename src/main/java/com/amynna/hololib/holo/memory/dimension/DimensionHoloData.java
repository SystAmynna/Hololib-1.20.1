package com.amynna.hololib.holo.memory.dimension;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.memory.HoloMemory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Stockage de la memoire de dimension, dans les donnees du niveau ({@code data/hololib_dimension.dat}). Avant chaque
 * sauvegarde, l'etat vivant du {@link DimensionHoloContainer} est recopie dans la memoire.
 */
public final class DimensionHoloData extends SavedData
{
    private static final String NAME = HoloLib.MODID + "_dimension";

    private final HoloMemory memory;
    private DimensionHoloContainer container;

    private DimensionHoloData(HoloMemory memory)
    {
        this.memory = memory;
    }

    /** Donnees du niveau, creees au premier acces. */
    public static DimensionHoloData of(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(DimensionHoloData::load,
                () -> new DimensionHoloData(new HoloMemory("dimension")), NAME);
    }

    public HoloMemory memory() { return memory; }

    void container(DimensionHoloContainer container) { this.container = container; }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        if (container != null) container.saveInto(memory);
        tag.put("memory", memory.save());
        return tag;
    }

    private static DimensionHoloData load(CompoundTag tag)
    {
        return new DimensionHoloData(HoloMemory.load(tag.getCompound("memory")));
    }
}
