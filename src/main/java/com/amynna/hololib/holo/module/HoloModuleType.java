package com.amynna.hololib.holo.module;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Type de module : fabrique enregistree dans {@link HoloModuleTypes}, avec sa compatibilite
 * ({@link HoloModuleTarget}). Son identifiant est sauvegarde dans la {@code HoloMemory} et envoye aux clients, qui
 * recreent le miroir du module a partir de lui. Une memoire contient au plus une instance de chaque type.
 */
public final class HoloModuleType<M extends HoloModule>
{
    private final HoloModuleTarget target;
    private final Supplier<M> factory;

    /** @param target seul genre de memoire dans lequel le module peut etre installe */
    public HoloModuleType(HoloModuleTarget target, Supplier<M> factory)
    {
        this.target = Objects.requireNonNull(target);
        this.factory = Objects.requireNonNull(factory);
    }

    public HoloModuleTarget target() { return target; }

    /** Identifiant dans le registre ; {@code null} si le type n'est pas enregistre. */
    public ResourceLocation id() { return HoloModuleTypes.registry().getKey(this); }

    /** Nouvelle instance liee a son type, son identifiant et son hote ; {@code onLoad} n'est pas encore appele. */
    public M create(UUID instanceId, HoloModuleHost host)
    {
        M module = factory.get();
        module.bind(this, instanceId, host);
        return module;
    }

    @Override
    public String toString() { return "HoloModuleType[" + id() + "]"; }
}
