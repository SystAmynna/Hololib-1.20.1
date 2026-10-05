package com.amynna.hololib.holo.memory;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.module.HoloModuleHost;
import com.amynna.hololib.holo.module.HoloModuleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.LogicalSide;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Operations sur une {@link HoloMemory} qui n'est pas chargee (memoire de joueur inactive, marker hors chargement).
 * Pour une memoire chargee, passer par son {@link HoloContainer}, qui synchronise les clients. Fil serveur.
 */
public final class HoloMemories
{
    private HoloMemories() {}

    /** Types des modules sauvegardes dans la memoire. */
    public static Set<ResourceLocation> types(HoloMemory memory)
    {
        Set<ResourceLocation> types = new LinkedHashSet<>();
        for (HoloMemory.Entry entry : memory.entries()) types.add(entry.type());
        return types;
    }

    public static boolean contains(HoloMemory memory, ResourceLocation typeId)
    {
        for (HoloMemory.Entry entry : memory.entries()) if (entry.type().equals(typeId)) return true;
        return false;
    }

    /**
     * Ajoute un module dans son etat initial : le module est cree le temps de produire sa sauvegarde ({@code onLoad}
     * puis {@code saveAll}). Faux si le type est deja present. La compatibilite est verifiee par l'appelant.
     */
    public static boolean install(HoloMemory memory, HoloModuleType<?> type, HoloScope scope)
    {
        if (contains(memory, type.id())) return false;
        HoloModule module = type.create(UUID.randomUUID(), new HoloModuleHost(scope, LogicalSide.SERVER, true));
        module.onLoad();
        memory.entries().add(new HoloMemory.Entry(type.id(), module.instanceId(), module.saveAll()));
        module.unload();
        return true;
    }

    /** Retire le module de ce type ; faux s'il n'est pas present. */
    public static boolean uninstall(HoloMemory memory, ResourceLocation typeId)
    {
        return memory.entries().removeIf(entry -> entry.type().equals(typeId));
    }
}
