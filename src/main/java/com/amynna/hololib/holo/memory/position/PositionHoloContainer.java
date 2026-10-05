package com.amynna.hololib.holo.memory.position;

import com.amynna.hololib.holo.memory.HoloContainer;
import com.amynna.hololib.holo.module.HoloModuleTarget;
import com.amynna.hololib.network.HoloForgetPacket;
import com.amynna.hololib.network.HoloNetwork;
import com.amynna.hololib.network.HoloOriginPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Memoire de position ouverte : chargee quand l'entite marker rejoint le niveau, fermee quand elle le quitte. Sans
 * proprietaire ; observateurs = joueurs qui suivent le chunk ou se trouve le marker (les markers ne sont jamais envoyes
 * aux clients, on ne peut pas suivre l'entite). Si le marker change de chunk, l'ancien chunk l'oublie et le nouveau
 * le recoit.
 */
public final class PositionHoloContainer extends HoloContainer
{
    private final Marker entity;
    private final HoloMarkers.Record record;
    private final HoloMarkers markers;
    private ChunkPos chunk;
    /** Derniere origine envoyee aux observateurs. */
    private Vec3 sentOrigin;

    private PositionHoloContainer(Marker entity, HoloMarkers.Record record, HoloMarkers markers)
    {
        super(record.scope());
        this.entity = entity;
        this.record = record;
        this.markers = markers;
        this.chunk = entity.chunkPosition();
        this.sentOrigin = entity.position();
    }

    /** Ouvre la memoire du marker et l'envoie aux joueurs qui suivent deja son chunk. */
    public static PositionHoloContainer open(Marker entity, HoloMarkers.Record record, HoloMarkers markers)
    {
        PositionHoloContainer container = new PositionHoloContainer(entity, record, markers);
        container.load(record.memory());
        record.live = container;
        container.updateRecord();
        container.sendToObservers(container.observerSnapshot());
        return container;
    }

    /** Recopie l'etat dans la memoire, decharge les modules ; les observateurs l'oublient. */
    public void close()
    {
        saveInto(record.memory());
        updateRecord();
        record.live = null;
        markers.setDirty();
        sendToObservers(new HoloForgetPacket(scope()));
        unload();
    }

    public String id() { return record.id(); }

    public Marker entity() { return entity; }

    /** Chunk ou se trouve le marker (mis a jour a chaque tick). */
    public ChunkPos chunk() { return chunk; }

    /**
     * Usage interne : suit les deplacements du marker. Changement de chunk : l'ancien chunk oublie la memoire, le
     * nouveau la recoit (avec l'origine) ; sinon, seule la nouvelle origine est envoyee.
     */
    public void trackMovement()
    {
        Vec3 position = entity.position();
        if (position.equals(sentOrigin)) return;
        sentOrigin = position;
        ChunkPos current = entity.chunkPosition();
        if (current.equals(chunk)) {
            sendToObservers(new HoloOriginPacket(scope(), position));
            return;
        }
        sendToObservers(new HoloForgetPacket(scope()));
        chunk = current;
        updateRecord();
        markers.setDirty();
        sendToObservers(observerSnapshot());
    }

    /** Position du marker. */
    @Override
    protected Vec3 origin() { return entity.position(); }

    @Override
    public HoloModuleTarget target() { return HoloModuleTarget.POSITION; }

    @Override
    public ServerLevel level() { return (ServerLevel) entity.level(); }

    @Override
    protected void sendToOwner(Object packet) {}

    @Override
    protected void sendToObservers(Object packet)
    {
        LevelChunk levelChunk = level().getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (levelChunk != null) HoloNetwork.sendToTracking(levelChunk, packet);
    }

    @Override
    protected void markPersistDirty() { markers.setDirty(); }

    private void updateRecord()
    {
        record.dimension = entity.level().dimension();
        record.position = entity.position();
        record.entity = entity.getUUID();
    }
}
