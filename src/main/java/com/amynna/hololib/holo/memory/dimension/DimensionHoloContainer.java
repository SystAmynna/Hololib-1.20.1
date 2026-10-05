package com.amynna.hololib.holo.memory.dimension;

import com.amynna.hololib.holo.memory.HoloContainer;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.module.HoloModuleTarget;
import com.amynna.hololib.network.HoloNetwork;
import net.minecraft.server.level.ServerLevel;

/**
 * Memoire de la dimension : chargee avec le niveau, accessible de partout dans la dimension (objets du ciel, annonces
 * globales...). Sans proprietaire : tous les joueurs de la dimension sont observateurs, seuls les objets
 * {@code PUBLIC} sont envoyes.
 */
public final class DimensionHoloContainer extends HoloContainer
{
    private final ServerLevel level;
    private final DimensionHoloData data;

    private DimensionHoloContainer(ServerLevel level, DimensionHoloData data)
    {
        super(HoloScope.DIMENSION);
        this.level = level;
        this.data = data;
    }

    /** Cree le conteneur du niveau et charge sa memoire. */
    public static DimensionHoloContainer open(ServerLevel level)
    {
        DimensionHoloData data = DimensionHoloData.of(level);
        DimensionHoloContainer container = new DimensionHoloContainer(level, data);
        container.load(data.memory());
        data.container(container);
        return container;
    }

    /** Recopie l'etat dans la memoire puis decharge les modules. */
    public void close()
    {
        saveInto(data.memory());
        data.container(null);
        unload();
    }

    @Override
    public HoloModuleTarget target() { return HoloModuleTarget.DIMENSION; }

    @Override
    public ServerLevel level() { return level; }

    @Override
    protected void sendToOwner(Object packet) {}

    @Override
    protected void sendToObservers(Object packet) { HoloNetwork.sendToDimension(level.dimension(), packet); }

    @Override
    protected void markPersistDirty() { data.setDirty(); }
}
