package com.amynna.hololib.holo.memory;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.module.HoloModuleHost;
import com.amynna.hololib.holo.module.HoloModuleTarget;
import com.amynna.hololib.holo.module.HoloModuleType;
import com.amynna.hololib.holo.module.HoloModuleTypes;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.network.HoloModulePacket;
import com.amynna.hololib.network.HoloObjectPacket;
import com.amynna.hololib.network.HoloSnapshotPacket;
import com.amynna.hololib.network.HoloSyncData.ModuleData;
import com.amynna.hololib.network.HoloSyncData.ObjectData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.LogicalSide;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Modules vivants d'une memoire chargee, cote serveur : l'etat de reference tant que la memoire est chargee, recopie
 * dans la {@link HoloMemory} a chaque sauvegarde. Fil serveur uniquement ; gere par {@link HoloContainers}.
 * <p>
 * Replication : le proprietaire (portee joueur) recoit tout. Les observateurs (definis par la sous-classe) ne recoivent
 * que les modules <b>partages</b>, c'est-a-dire ayant au moins un objet {@code PUBLIC} (ou
 * {@link HoloModule#sharedWithObservers()}), et leur etat public ; un module
 * qui devient (ou cesse d'etre) partage leur est ajoute (ou retire). Les changements sont envoyes en fin de tick.
 * <p>
 * Un module retire laisse sa sauvegarde dans la memoire (<b>dormante</b>) : elle est relue s'il est reinstalle, et
 * effacee par {@link #reset}.
 */
public abstract class HoloContainer
{
    private final HoloScope scope;
    private final Map<UUID, HoloModule> modules = new LinkedHashMap<>();
    private final Collection<HoloModule> modulesView = Collections.unmodifiableCollection(modules.values());
    /** Modules dont le type n'est plus enregistre (mod retire) : conserves tels quels dans la memoire. */
    private final List<HoloMemory.Entry> orphans = new ArrayList<>();
    /** Sauvegardes des modules retires, par type. */
    private final Map<ResourceLocation, HoloMemory.Entry> dormant = new LinkedHashMap<>();
    /** Modules actuellement connus des observateurs. */
    private final Set<UUID> shared = new HashSet<>();

    protected HoloContainer(HoloScope scope)
    {
        this.scope = scope;
    }

    public final HoloScope scope() { return scope; }

    /** Modules actifs, dans l'ordre d'installation. */
    public final Collection<HoloModule> modules() { return modulesView; }

    public final HoloModule module(UUID instanceId) { return modules.get(instanceId); }

    /** Genre de memoire : seuls les types de modules de cette compatibilite peuvent y etre installes. */
    public abstract HoloModuleTarget target();

    /** Niveau dans lequel vivent les modules ; {@code null} si indisponible (joueur en cours de respawn). */
    public abstract ServerLevel level();

    /** Envoie au proprietaire ; sans effet pour une portee sans proprietaire. */
    protected abstract void sendToOwner(Object packet);

    /** Envoie a tous les observateurs actuels de la portee. */
    protected abstract void sendToObservers(Object packet);

    /** Signale au stockage que la memoire doit etre sauvegardee. */
    protected abstract void markPersistDirty();

    /**
     * Origine des objets ancres {@code World}, envoyee aux clients avec l'etat ; {@code null} si le client la connait
     * deja (entite du joueur) ou si c'est l'origine du monde (dimension).
     */
    protected Vec3 origin() { return null; }

    /** Apres le chargement des modules d'une memoire : relire les donnees propres a ce genre de memoire. */
    protected void onMemoryLoaded(HoloMemory memory) {}

    /** Avant la recopie des modules dans une memoire : y ecrire les donnees propres a ce genre de memoire. */
    protected void onMemorySaving(HoloMemory memory) {}

    // ---------------------------------------------------------------- API

    /** Vrai si un module de ce type est installe. */
    public final boolean contains(ResourceLocation typeId) { return find(typeId) != null; }

    /** Types des modules installes. */
    public final Set<ResourceLocation> types()
    {
        Set<ResourceLocation> types = new LinkedHashSet<>();
        for (HoloModule module : modules.values()) types.add(module.type().id());
        return types;
    }

    /** Types des sauvegardes dormantes (modules retires dont la sauvegarde est gardee). */
    public final Set<ResourceLocation> storedTypes() { return new LinkedHashSet<>(dormant.keySet()); }

    /** Types des modules equipes dont le type n'est plus enregistre (conserves tels quels). */
    public final Set<ResourceLocation> unknownTypes()
    {
        Set<ResourceLocation> types = new LinkedHashSet<>();
        for (HoloMemory.Entry entry : orphans) types.add(entry.type());
        return types;
    }

    /**
     * Installe un module et le synchronise ; {@code null} si le type est incompatible avec cette memoire ou deja
     * installe. Une sauvegarde dormante de ce type est relue (sinon le module part de son etat initial).
     *
     * @param creator joueur qui l'installe ({@link HoloModule#creator()}), ou {@code null} ; une sauvegarde relue
     *                garde son createur s'il en a un
     */
    public final HoloModule install(HoloModuleType<?> type, UUID creator)
    {
        if (type.target() != target() || contains(type.id())) return null;
        HoloMemory.Entry saved = dormant.remove(type.id());
        HoloModule module = saved != null ? restore(type, saved) : null;
        if (module == null) module = fresh(type, creator);
        else if (module.creator() == null) module.creator(creator);
        attach(module);
        markPersistDirty();
        return module;
    }

    /** Retire le module de ce type ; faux s'il n'est pas installe. */
    public final boolean uninstallType(ResourceLocation typeId)
    {
        HoloModule module = find(typeId);
        return module != null && uninstall(module.instanceId());
    }

    /** Retire un module, en gardant sa sauvegarde (dormante) ; faux s'il n'existe pas. */
    public final boolean uninstall(UUID instanceId)
    {
        HoloModule module = modules.get(instanceId);
        if (module == null) return false;
        ResourceLocation typeId = module.type().id();
        dormant.put(typeId, new HoloMemory.Entry(typeId, instanceId, module.saveAll()));
        detach(module);
        markPersistDirty();
        return true;
    }

    /**
     * Remet a zero la sauvegarde du module de ce type, sans le retirer : equipe, il est remplace par un module dans son
     * etat initial (meme createur) ; retire, sa sauvegarde dormante est effacee. {@link HoloModule#onReset} est appele
     * avant. Faux si la memoire n'a aucune trace de ce type (un module de type inconnu n'est jamais remis a zero).
     */
    public final boolean reset(ResourceLocation typeId)
    {
        HoloModule module = find(typeId);
        if (module != null) {
            module.onReset(level());
            detach(module);
            dormant.remove(typeId);
            attach(fresh(module.type(), module.creator()));
            markPersistDirty();
            return true;
        }
        HoloMemory.Entry saved = dormant.remove(typeId);
        if (saved == null) return false;
        HoloMemories.discard(saved, scope, level());
        markPersistDirty();
        return true;
    }

    /** Etat complet, pour le proprietaire. */
    public final HoloSnapshotPacket ownerSnapshot()
    {
        return new HoloSnapshotPacket(scope, true, ModuleData.snapshot(modules.values(), true), origin());
    }

    /** Etat des modules partages, pour un observateur qui arrive. */
    public final HoloSnapshotPacket observerSnapshot()
    {
        List<ModuleData> data = new ArrayList<>(shared.size());
        for (HoloModule module : modules.values()) {
            if (shared.contains(module.instanceId())) data.add(ModuleData.of(module, false, true));
        }
        return new HoloSnapshotPacket(scope, false, data, origin());
    }

    // ---------------------------------------------------------------- interne

    /** Usage interne : charge les modules d'une memoire (et garde ses sauvegardes dormantes). */
    @ApiStatus.Internal
    public final void load(HoloMemory memory)
    {
        for (HoloMemory.Entry entry : memory.dormant()) dormant.put(entry.type(), entry);
        for (HoloMemory.Entry entry : memory.entries()) {
            HoloModuleType<?> type = HoloModuleTypes.get(entry.type());
            if (type == null) {
                HoloLib.LOGGER.warn("Type de module inconnu {} dans la memoire '{}', conserve tel quel", entry.type(),
                        memory.name());
                orphans.add(entry);
                continue;
            }
            HoloModule module = type.create(entry.instanceId(), host());
            try {
                module.loadAll(entry.data());
            } catch (RuntimeException e) {
                HoloLib.LOGGER.error("Chargement du module {} impossible, conserve tel quel", entry.type(), e);
                orphans.add(entry);
                continue;
            }
            module.clearDirty();
            modules.put(module.instanceId(), module);
            if (isShared(module)) shared.add(module.instanceId());
        }
        onMemoryLoaded(memory);
    }

    /** Usage interne : recopie l'etat vivant dans {@code memory} (remplace ses entrees). */
    @ApiStatus.Internal
    public final void saveInto(HoloMemory memory)
    {
        onMemorySaving(memory);
        List<HoloMemory.Entry> entries = memory.entries();
        entries.clear();
        for (HoloModule module : modules.values()) {
            entries.add(new HoloMemory.Entry(module.type().id(), module.instanceId(), module.saveAll()));
        }
        entries.addAll(orphans);
        memory.dormant().clear();
        memory.dormant().addAll(dormant.values());
    }

    /** Usage interne : decharge tous les modules (sans sauvegarder ni rien envoyer). */
    @ApiStatus.Internal
    public final void unload()
    {
        for (HoloModule module : modules.values()) module.unload();
        modules.clear();
        orphans.clear();
        dormant.clear();
        shared.clear();
    }

    @ApiStatus.Internal
    public final void tick()
    {
        ServerLevel level = level();
        if (level == null) return;
        for (HoloModule module : modules.values()) module.serverTick(level);
    }

    /** Usage interne : envoie les changements du tick (etat, ancrages, visibilites, partage). */
    @ApiStatus.Internal
    public final void flush()
    {
        boolean changed = false;
        boolean owned = scope.owner() != null;
        for (HoloModule module : modules.values()) {
            UUID id = module.instanceId();
            boolean stateDirty = module.stateDirty();
            boolean objectsDirty = false;
            if (stateDirty && owned) {
                sendToOwner(new HoloModulePacket(scope, true, HoloModulePacket.Op.UPDATE,
                        ModuleData.of(module, true, false)));
            }
            for (HoloObject object : module.objects()) {
                if (!object.syncDirty()) continue;
                objectsDirty = true;
                if (owned) sendToOwner(new HoloObjectPacket(scope, id, ObjectData.of(object, true)));
            }
            changed |= stateDirty || objectsDirty;

            boolean nowShared = isShared(module);
            if (nowShared && shared.add(id)) {
                sendToObservers(new HoloModulePacket(scope, false, HoloModulePacket.Op.ADD,
                        ModuleData.of(module, false, true)));
            } else if (!nowShared && shared.remove(id)) {
                sendToObservers(HoloModulePacket.remove(scope, module));
            } else if (nowShared) {
                if (stateDirty) {
                    sendToObservers(new HoloModulePacket(scope, false, HoloModulePacket.Op.UPDATE,
                            ModuleData.of(module, false, false)));
                }
                if (objectsDirty) {
                    for (HoloObject object : module.objects()) {
                        if (object.syncDirty()) {
                            sendToObservers(new HoloObjectPacket(scope, id, ObjectData.of(object, false)));
                        }
                    }
                }
            }
            module.clearDirty();
        }
        if (changed) markPersistDirty();
    }

    /** Module dans son etat initial (non ajoute). */
    private HoloModule fresh(HoloModuleType<?> type, UUID creator)
    {
        HoloModule module = type.create(UUID.randomUUID(), host());
        module.creator(creator);
        module.onLoad();
        return module;
    }

    /** Module relu depuis une sauvegarde dormante (non ajoute) ; {@code null} si elle est illisible. */
    private HoloModule restore(HoloModuleType<?> type, HoloMemory.Entry saved)
    {
        HoloModule module = type.create(saved.instanceId(), host());
        try {
            module.loadAll(saved.data());
        } catch (RuntimeException e) {
            HoloLib.LOGGER.error("Sauvegarde du module {} illisible, etat initial", saved.type(), e);
            return null;
        }
        return module;
    }

    /** Ajoute un module pret et l'envoie au proprietaire et, s'il est partage, aux observateurs. */
    private void attach(HoloModule module)
    {
        module.clearDirty();
        modules.put(module.instanceId(), module);
        if (scope.owner() != null) {
            sendToOwner(new HoloModulePacket(scope, true, HoloModulePacket.Op.ADD,
                    ModuleData.of(module, true, true)));
        }
        if (isShared(module)) {
            shared.add(module.instanceId());
            sendToObservers(new HoloModulePacket(scope, false, HoloModulePacket.Op.ADD,
                    ModuleData.of(module, false, true)));
        }
    }

    /** Retire et decharge un module, et le fait oublier aux clients. */
    private void detach(HoloModule module)
    {
        modules.remove(module.instanceId());
        module.unload();
        HoloModulePacket packet = HoloModulePacket.remove(scope, module);
        sendToOwner(packet);
        if (shared.remove(module.instanceId())) sendToObservers(packet);
    }

    private HoloModule find(ResourceLocation typeId)
    {
        for (HoloModule module : modules.values()) if (typeId.equals(module.type().id())) return module;
        return null;
    }

    private HoloModuleHost host() { return new HoloModuleHost(scope, LogicalSide.SERVER, true); }

    private static boolean isShared(HoloModule module)
    {
        if (module.sharedWithObservers()) return true;
        for (HoloObject object : module.objects()) if (object.visibility() == HoloVisibility.PUBLIC) return true;
        return false;
    }
}
