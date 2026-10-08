package com.amynna.hololib.client.holo;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.edit.HoloEditSession;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.module.HoloModuleHost;
import com.amynna.hololib.holo.module.HoloModuleType;
import com.amynna.hololib.holo.module.HoloModuleTypes;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.network.HoloSyncData.ModuleData;
import com.amynna.hololib.network.HoloSyncData.ObjectData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.LogicalSide;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Miroirs clients des modules d'une portee (memoire d'un joueur, de la dimension ou d'un marker). */
public final class ClientHoloContainer
{
    private final HoloScope scope;
    private final Map<UUID, HoloModule> modules = new LinkedHashMap<>();
    private final Collection<HoloModule> modulesView = Collections.unmodifiableCollection(modules.values());
    private Vec3 origin = Vec3.ZERO;

    ClientHoloContainer(HoloScope scope)
    {
        this.scope = scope;
    }

    public HoloScope scope() { return scope; }

    public Collection<HoloModule> modules() { return modulesView; }

    /**
     * Origine des ancrages {@code World} recue du serveur (marker d'une memoire de position) ; origine du monde sinon.
     * Pour une memoire de joueur, l'origine est son entite, lue par le rendu.
     */
    public Vec3 origin() { return origin; }

    void origin(Vec3 origin) { this.origin = origin; }

    void add(ModuleData data, boolean ownerView)
    {
        remove(data.instanceId());
        HoloModuleType<?> type = HoloModuleTypes.get(data.type());
        if (type == null) {
            HoloLib.LOGGER.warn("Type de module inconnu cote client : {}", data.type());
            return;
        }
        HoloModule module = type.create(data.instanceId(), new HoloModuleHost(scope, LogicalSide.CLIENT, ownerView));
        module.creator(data.creator());
        readState(module, data);
        module.onLoad();
        for (ObjectData object : data.objects()) apply(module, object);
        modules.put(module.instanceId(), module);
    }

    void update(ModuleData data)
    {
        HoloModule module = modules.get(data.instanceId());
        if (module == null) return;
        readState(module, data);
        module.onSync();
        for (ObjectData object : data.objects()) apply(module, object);
    }

    void remove(UUID instanceId)
    {
        HoloModule module = modules.remove(instanceId);
        if (module != null) module.unload();
    }

    void object(UUID instanceId, ObjectData data)
    {
        HoloModule module = modules.get(instanceId);
        if (module != null) apply(module, data);
    }

    void clear()
    {
        for (HoloModule module : modules.values()) module.unload();
        modules.clear();
    }

    void tick()
    {
        for (HoloModule module : modules.values()) {
            module.clientTick();
            for (HoloObject object : module.objects()) object.tick();
        }
    }

    private static void readState(HoloModule module, ModuleData data)
    {
        FriendlyByteBuf buf = data.stateBuffer();
        try {
            module.readSync(buf);
        } finally {
            buf.release();
        }
    }

    /**
     * Un objet en cours d'edition locale garde son placement et sa portee : seuls visibilite, affichage et acces sont
     * appliques. L'ouverture n'est appliquee que chez un observateur (le client du proprietaire fait foi).
     */
    private static void apply(HoloModule module, ObjectData data)
    {
        HoloObject object = module.object(data.key());
        if (object == null) return;
        Boolean opened = module.host().ownerView() ? null : data.opened();
        if (HoloEditSession.holds(object)) {
            object.restore(data.visibility(), data.display(), data.access(), null, null, null, null, opened,
                    Float.NaN);
        } else {
            object.restore(data.visibility(), data.display(), data.access(), data.anchor(), data.transform(),
                    data.openOffset(), data.rotationFrame(), opened, data.editRange());
        }
    }
}
