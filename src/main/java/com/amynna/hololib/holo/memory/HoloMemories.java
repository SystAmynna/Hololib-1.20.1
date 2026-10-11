package com.amynna.hololib.holo.memory;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.module.HoloModuleHost;
import com.amynna.hololib.holo.module.HoloModuleType;
import com.amynna.hololib.holo.module.HoloModuleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.LogicalSide;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Operations sur une {@link HoloMemory} qui n'est pas chargee (memoire de joueur inactive, marker hors chargement).
 * Pour une memoire chargee, passer par son {@link HoloContainer}, qui synchronise les clients. Memes regles : un module
 * retire laisse sa sauvegarde (dormante), relue a sa reinstallation. Fil serveur.
 */
public final class HoloMemories
{
    private HoloMemories() {}

    /** Types des modules equipes dans la memoire (types inconnus compris). */
    public static Set<ResourceLocation> types(HoloMemory memory) { return types(memory.entries()); }

    /** Types des sauvegardes dormantes de la memoire. */
    public static Set<ResourceLocation> storedTypes(HoloMemory memory) { return types(memory.dormant()); }

    public static boolean contains(HoloMemory memory, ResourceLocation typeId)
    {
        return find(memory.entries(), typeId) != null;
    }

    /**
     * Ajoute un module : sa sauvegarde dormante si elle existe, sinon son etat initial (le module est cree le temps de
     * produire sa sauvegarde : {@code onLoad} puis {@code saveAll}). Faux si le type est deja present. La
     * compatibilite est verifiee par l'appelant.
     *
     * @param creator joueur qui l'installe, ou {@code null}
     */
    public static boolean install(HoloMemory memory, HoloModuleType<?> type, HoloScope scope, UUID creator)
    {
        if (contains(memory, type.id())) return false;
        HoloMemory.Entry saved = find(memory.dormant(), type.id());
        if (saved != null) {
            memory.dormant().remove(saved);
            memory.entries().add(saved);
            return true;
        }
        memory.entries().add(initial(type, scope, creator));
        return true;
    }

    /** Retire le module de ce type en gardant sa sauvegarde (dormante) ; faux s'il n'est pas present. */
    public static boolean uninstall(HoloMemory memory, ResourceLocation typeId)
    {
        HoloMemory.Entry entry = find(memory.entries(), typeId);
        if (entry == null) return false;
        memory.entries().remove(entry);
        memory.dormant().removeIf(dormant -> dormant.type().equals(typeId));
        memory.dormant().add(entry);
        return true;
    }

    /**
     * Remet a zero la sauvegarde du module de ce type, sans le retirer : equipe, son entree est remplacee par son etat
     * initial (meme createur, meme place) ; retire, sa sauvegarde dormante est effacee. {@link HoloModule#onReset} est
     * appele avant sur une instance recreee depuis la sauvegarde. Faux si la memoire n'a aucune trace de ce type, ou
     * si le module equipe est d'un type inconnu.
     *
     * @param level niveau de la memoire, passe a {@code onReset} (peut etre {@code null})
     */
    public static boolean reset(HoloMemory memory, ResourceLocation typeId, HoloScope scope, ServerLevel level)
    {
        List<HoloMemory.Entry> entries = memory.entries();
        HoloMemory.Entry entry = find(entries, typeId);
        if (entry != null) {
            HoloModuleType<?> type = HoloModuleTypes.get(typeId);
            if (type == null) return false;
            discard(entry, scope, level);
            UUID creator = entry.data().hasUUID("creator") ? entry.data().getUUID("creator") : null;
            entries.set(entries.indexOf(entry), initial(type, scope, creator));
            memory.dormant().removeIf(dormant -> dormant.type().equals(typeId));
            return true;
        }
        HoloMemory.Entry saved = find(memory.dormant(), typeId);
        if (saved == null) return false;
        memory.dormant().remove(saved);
        discard(saved, scope, level);
        return true;
    }

    /**
     * Sauvegarde abandonnee par une remise a zero : le module est recree depuis elle le temps d'appeler
     * {@link HoloModule#onReset}, puis decharge. Sans effet pour un type inconnu ou une sauvegarde illisible.
     */
    static void discard(HoloMemory.Entry entry, HoloScope scope, ServerLevel level)
    {
        HoloModuleType<?> type = HoloModuleTypes.get(entry.type());
        if (type == null) return;
        HoloModule module = type.create(entry.instanceId(), new HoloModuleHost(scope, LogicalSide.SERVER, true));
        try {
            module.loadAll(entry.data());
        } catch (RuntimeException e) {
            HoloLib.LOGGER.warn("Sauvegarde du module {} illisible, remise a zero sans onReset", entry.type(), e);
            return;
        }
        module.onReset(level);
        module.unload();
    }

    /** Entree dans l'etat initial du module (cree le temps de produire sa sauvegarde). */
    private static HoloMemory.Entry initial(HoloModuleType<?> type, HoloScope scope, UUID creator)
    {
        HoloModule module = type.create(UUID.randomUUID(), new HoloModuleHost(scope, LogicalSide.SERVER, true));
        module.creator(creator);
        module.onLoad();
        HoloMemory.Entry entry = new HoloMemory.Entry(type.id(), module.instanceId(), module.saveAll());
        module.unload();
        return entry;
    }

    private static HoloMemory.Entry find(List<HoloMemory.Entry> entries, ResourceLocation typeId)
    {
        for (HoloMemory.Entry entry : entries) if (entry.type().equals(typeId)) return entry;
        return null;
    }

    private static Set<ResourceLocation> types(List<HoloMemory.Entry> entries)
    {
        Set<ResourceLocation> types = new LinkedHashSet<>();
        for (HoloMemory.Entry entry : entries) types.add(entry.type());
        return types;
    }
}
