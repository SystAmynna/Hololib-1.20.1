package com.amynna.hololib.command;

import com.amynna.hololib.holo.memory.HoloContainer;
import com.amynna.hololib.holo.memory.HoloMemories;
import com.amynna.hololib.holo.memory.HoloMemory;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.module.HoloModuleTarget;
import com.amynna.hololib.holo.module.HoloModuleType;
import com.amynna.hololib.holo.module.HoloModuleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashSet;
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

    /** Types deja installes (types inconnus compris). */
    Set<ResourceLocation> present();

    /** Types des sauvegardes dormantes (modules retires dont la sauvegarde est gardee). */
    Set<ResourceLocation> stored();

    /**
     * Installe le module (relit sa sauvegarde dormante s'il en a une) ; faux s'il est deja present (la compatibilite
     * est verifiee par l'appelant).
     *
     * @param creator joueur qui lance la commande (proprietaire du module pour l'edition), ou {@code null}
     */
    boolean add(HoloModuleType<?> type, UUID creator);

    /** Retire le module de ce type (sa sauvegarde est gardee) ; faux s'il est absent. */
    boolean remove(ResourceLocation typeId);

    /** Remet a zero la sauvegarde du module de ce type sans le retirer ; faux sans trace ou si le type est inconnu. */
    boolean reset(ResourceLocation typeId);

    /** Types des modules installes dont le type n'est plus enregistre. */
    default Set<ResourceLocation> unknown()
    {
        Set<ResourceLocation> unknown = new LinkedHashSet<>();
        for (ResourceLocation id : present()) if (HoloModuleTypes.get(id) == null) unknown.add(id);
        return unknown;
    }

    /** Types dont la memoire garde une trace remise a zero possible : installes (types connus) et dormants. */
    default Set<ResourceLocation> resettable()
    {
        Set<ResourceLocation> types = new LinkedHashSet<>(present());
        types.removeAll(unknown());
        types.addAll(stored());
        return types;
    }

    static ModuleSlot of(HoloContainer container)
    {
        return new ModuleSlot()
        {
            @Override
            public HoloModuleTarget target() { return container.target(); }

            @Override
            public Set<ResourceLocation> present()
            {
                Set<ResourceLocation> types = container.types();
                types.addAll(container.unknownTypes());
                return types;
            }

            @Override
            public Set<ResourceLocation> stored() { return container.storedTypes(); }

            @Override
            public Set<ResourceLocation> unknown() { return container.unknownTypes(); }

            @Override
            public boolean add(HoloModuleType<?> type, UUID creator)
            {
                return container.install(type, creator) != null;
            }

            @Override
            public boolean remove(ResourceLocation typeId) { return container.uninstallType(typeId); }

            @Override
            public boolean reset(ResourceLocation typeId) { return container.reset(typeId); }
        };
    }

    /**
     * @param level niveau de la memoire (passe a {@code onReset}), ou {@code null}
     * @param dirty appele apres chaque modification (marquer le stockage a sauvegarder)
     */
    static ModuleSlot offline(HoloMemory memory, HoloScope scope, HoloModuleTarget target, ServerLevel level,
                              Runnable dirty)
    {
        return new ModuleSlot()
        {
            @Override
            public HoloModuleTarget target() { return target; }

            @Override
            public Set<ResourceLocation> present() { return HoloMemories.types(memory); }

            @Override
            public Set<ResourceLocation> stored() { return HoloMemories.storedTypes(memory); }

            @Override
            public boolean add(HoloModuleType<?> type, UUID creator)
            {
                return changed(HoloMemories.install(memory, type, scope, creator));
            }

            @Override
            public boolean remove(ResourceLocation typeId) { return changed(HoloMemories.uninstall(memory, typeId)); }

            @Override
            public boolean reset(ResourceLocation typeId)
            {
                return changed(HoloMemories.reset(memory, typeId, scope, level));
            }

            private boolean changed(boolean changed)
            {
                if (changed) dirty.run();
                return changed;
            }
        };
    }
}
