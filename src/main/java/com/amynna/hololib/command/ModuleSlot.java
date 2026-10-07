package com.amynna.hololib.command;

import com.amynna.hololib.holo.memory.HoloContainer;
import com.amynna.hololib.holo.memory.HoloMemories;
import com.amynna.hololib.holo.memory.HoloMemory;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.module.HoloModuleTarget;
import com.amynna.hololib.holo.module.HoloModuleType;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.UUID;

/**
 * Memoire visee par une commande de modules, chargee ou non : masque la difference entre un {@link HoloContainer}
 * (modules vivants, clients synchronises) et une {@link HoloMemory} au repos (memoire de joueur inactive, marker non
 * charge).
 */
interface ModuleSlot
{
    /** Compatibilite attendue des modules. */
    HoloModuleTarget target();

    /** Types deja installes. */
    Set<ResourceLocation> present();

    /**
     * Installe le module ; faux s'il est deja present (la compatibilite est verifiee par l'appelant).
     *
     * @param creator joueur qui lance la commande (proprietaire du module pour l'edition), ou {@code null}
     */
    boolean add(HoloModuleType<?> type, UUID creator);

    /** Retire le module de ce type ; faux s'il est absent. */
    boolean remove(ResourceLocation typeId);

    static ModuleSlot of(HoloContainer container)
    {
        return new ModuleSlot()
        {
            @Override
            public HoloModuleTarget target() { return container.target(); }

            @Override
            public Set<ResourceLocation> present() { return container.types(); }

            @Override
            public boolean add(HoloModuleType<?> type, UUID creator)
            {
                return container.install(type, creator) != null;
            }

            @Override
            public boolean remove(ResourceLocation typeId) { return container.uninstallType(typeId); }
        };
    }

    /** @param dirty appele apres chaque modification (marquer le stockage a sauvegarder) */
    static ModuleSlot offline(HoloMemory memory, HoloScope scope, HoloModuleTarget target, Runnable dirty)
    {
        return new ModuleSlot()
        {
            @Override
            public HoloModuleTarget target() { return target; }

            @Override
            public Set<ResourceLocation> present() { return HoloMemories.types(memory); }

            @Override
            public boolean add(HoloModuleType<?> type, UUID creator)
            {
                boolean added = HoloMemories.install(memory, type, scope, creator);
                if (added) dirty.run();
                return added;
            }

            @Override
            public boolean remove(ResourceLocation typeId)
            {
                boolean removed = HoloMemories.uninstall(memory, typeId);
                if (removed) dirty.run();
                return removed;
            }
        };
    }
}
