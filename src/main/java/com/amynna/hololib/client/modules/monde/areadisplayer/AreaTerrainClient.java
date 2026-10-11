package com.amynna.hololib.client.modules.monde.areadisplayer;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerModule;
import com.amynna.hololib.modules.monde.areadisplayer.AreaGrid;
import com.amynna.hololib.modules.monde.areadisplayer.terrain.AreaTerrainNetwork;
import com.amynna.hololib.modules.monde.areadisplayer.terrain.AreaTerrainPackets;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Cote client du canal du terrain de l'Area Displayer ({@link AreaTerrainNetwork}), fil client : une {@link AreaView}
 * par miroir abonne (cle = marker), creee a l'abonnement ({@link #subscribe}) et oubliee au retrait du miroir ou a la
 * deconnexion. Handlers des paquets (generation verifiee : un paquet d'une ancienne grille est ignore), remaillage des
 * sections changees ({@value #MESH_PER_TICK} sections au plus par tick) et horloge des positions suivies (ticks
 * client).
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class AreaTerrainClient
{
    /** Sections remaillees au plus par tick client. */
    private static final int MESH_PER_TICK = 32;
    /** Distance (voxels) au-dela de laquelle une nouvelle position allonge la trainee. */
    private static final float TRAIL_STEP = 0.05f;

    private static final Map<String, AreaView> VIEWS = new HashMap<>();
    private static final Inflater INFLATER = new Inflater();
    private static final byte[] PACKED = new byte[AreaGrid.PACKED_BYTES];
    private static final float[] LAST = new float[3];
    /** Horloge des positions suivies et des trainees. */
    private static int ticks;

    private AreaTerrainClient() {}

    /** Vue du Displayer du marker {@code marker}, {@code null} s'il n'est pas abonne. */
    public static AreaView view(String marker) { return VIEWS.get(marker); }

    /** Tick client courant (horloge des trainees). */
    public static int ticks() { return ticks; }

    /** Miroir cree : vue neuve et abonnement. */
    static void subscribe(AreaDisplayerModule module)
    {
        String marker = module.markerId();
        VIEWS.put(marker, new AreaView(marker, module.instanceId()));
        if (Minecraft.getInstance().getConnection() != null) {
            AreaTerrainNetwork.sendToServer(new AreaTerrainPackets.Subscribe(marker, module.instanceId()));
        }
    }

    /** Miroir retire : vue oubliee, desabonnement (si la connexion existe encore). */
    static void unsubscribe(AreaDisplayerModule module)
    {
        String marker = module.markerId();
        AreaView view = VIEWS.get(marker);
        if (view == null || !view.instance.equals(module.instanceId())) return;
        VIEWS.remove(marker);
        if (Minecraft.getInstance().getConnection() != null) {
            AreaTerrainNetwork.sendToServer(new AreaTerrainPackets.Unsubscribe(marker, module.instanceId()));
        }
    }

    // ---------------------------------------------------------------- paquets

    public static void onHeader(AreaTerrainPackets.Header packet)
    {
        AreaView view = VIEWS.get(packet.marker());
        if (view == null || !view.instance.equals(packet.instance())) return;
        view.generation = packet.generation();
        view.reset(packet.zone() ? new AreaGrid(packet.minX(), packet.minY(), packet.minZ(), packet.blocksX(),
                packet.blocksY(), packet.blocksZ(), packet.requestedStep()) : null);
    }

    public static void onSections(AreaTerrainPackets.Sections packet)
    {
        AreaView view = VIEWS.get(packet.marker());
        if (view == null || view.grid == null || view.generation != packet.generation()) return;
        AreaGrid grid = view.grid;
        int[] indices = packet.indices();
        for (int i = 0; i < indices.length; i++) {
            int section = indices[i];
            if (section < 0 || section >= grid.sections()) continue;
            int uniform = packet.uniform()[i];
            if (uniform >= 0) {
                grid.unpack(null, uniform, section, view.voxels);
            } else if (inflate(packet.data()[i])) {
                grid.unpack(PACKED, 0, section, view.voxels);
            } else {
                continue;
            }
            view.markDirty(section);
        }
    }

    private static boolean inflate(byte[] data)
    {
        INFLATER.reset();
        INFLATER.setInput(data);
        try {
            int read = 0;
            while (read < PACKED.length) {
                int n = INFLATER.inflate(PACKED, read, PACKED.length - read);
                if (n == 0) return false;
                read += n;
            }
            return true;
        } catch (DataFormatException e) {
            return false;
        }
    }

    public static void onTracks(AreaTerrainPackets.Tracks packet)
    {
        AreaView view = VIEWS.get(packet.marker());
        if (view == null || view.grid == null || view.generation != packet.generation()) return;
        float inv = 1f / view.grid.step();
        for (AreaView.Track track : view.tracks.values()) track.seen = false;
        int[] ids = packet.ids();
        float[] xyz = packet.xyz();
        for (int i = 0; i < ids.length; i++) {
            float x = xyz[3 * i] * inv;
            float y = xyz[3 * i + 1] * inv;
            float z = xyz[3 * i + 2] * inv;
            AreaView.Track track = view.tracks.get(ids[i]);
            if (track == null) {
                track = new AreaView.Track();
                track.px = track.cx = x;
                track.py = track.cy = y;
                track.pz = track.cz = z;
                track.addTrail(x, y, z, ticks);
                view.tracks.put(ids[i], track);
            } else {
                // Depart de l'interpolation : la position affichee a cet instant.
                float t = Math.min(1f, Math.max(0f, (ticks - track.received) / AreaView.TRACK_PERIOD));
                track.px = track.px + (track.cx - track.px) * t;
                track.py = track.py + (track.cy - track.py) * t;
                track.pz = track.pz + (track.cz - track.pz) * t;
                track.cx = x;
                track.cy = y;
                track.cz = z;
                if (track.trailSize == 0) track.addTrail(x, y, z, ticks);
                else {
                    track.trailPoint(track.trailSize - 1, LAST);
                    float dx = x - LAST[0];
                    float dy = y - LAST[1];
                    float dz = z - LAST[2];
                    if (dx * dx + dy * dy + dz * dz > TRAIL_STEP * TRAIL_STEP) track.addTrail(x, y, z, ticks);
                }
            }
            track.kind = packet.kinds()[i];
            if (packet.names()[i] != null) track.name = packet.names()[i];
            track.received = ticks;
            track.seen = true;
        }
        ObjectIterator<Int2ObjectMap.Entry<AreaView.Track>> iterator = view.tracks.int2ObjectEntrySet().iterator();
        while (iterator.hasNext()) if (!iterator.next().getValue().seen) iterator.remove();
        view.trackList.clear();
        view.trackList.addAll(view.tracks.values());
    }

    // ---------------------------------------------------------------- evenements

    /** Fin de chaque tick client : horloge, remaillage des sections changees. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        int budget = MESH_PER_TICK;
        for (AreaView view : VIEWS.values()) {
            if (view.dirtyCount == 0 || view.grid == null) continue;
            for (int s = 0; s < view.dirty.length && budget > 0 && view.dirtyCount > 0; s++) {
                if (!view.dirty[s]) continue;
                view.dirty[s] = false;
                view.dirtyCount--;
                view.meshes[s] = AreaMesher.mesh(view.grid, view.voxels, s);
                budget--;
            }
            if (budget == 0) break;
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        VIEWS.clear();
    }
}
