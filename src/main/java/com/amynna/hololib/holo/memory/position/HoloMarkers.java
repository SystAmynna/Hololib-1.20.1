package com.amynna.hololib.holo.memory.position;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.memory.HoloMemory;
import com.amynna.hololib.holo.memory.HoloScope;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Registre global des markers du mod ({@code data/hololib_markers.dat} de l'overworld) : identifiant unique, derniere
 * position connue, UUID de l'entite et memoire de position. Source de verite des identifiants (ils restent connus
 * quand le marker n'est pas charge) et stockage de la memoire, modifiable meme hors chargement. Fil serveur.
 * <p>
 * Avant chaque sauvegarde, l'etat des memoires ouvertes ({@link PositionHoloContainer}) est recopie.
 */
public final class HoloMarkers extends SavedData
{
    /** Tag porte par les entites marker du mod. */
    public static final String TAG = HoloLib.MODID;

    private static final String NAME = HoloLib.MODID + "_markers";
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.-]+");

    /** Marker enregistre ; position et entite sont mises a jour par son conteneur. */
    public static final class Record
    {
        private final String id;
        private final HoloMemory memory;
        ResourceKey<Level> dimension;
        Vec3 position;
        UUID entity;
        PositionHoloContainer live;

        Record(String id, ResourceKey<Level> dimension, Vec3 position, UUID entity, HoloMemory memory)
        {
            this.id = id;
            this.dimension = dimension;
            this.position = position;
            this.entity = entity;
            this.memory = memory;
        }

        public String id() { return id; }

        public HoloScope.Position scope() { return new HoloScope.Position(id); }

        public ResourceKey<Level> dimension() { return dimension; }

        public Vec3 position() { return position; }

        public UUID entity() { return entity; }

        /** Memoire de position ; tant que le marker est charge, l'etat de reference est dans {@link #live()}. */
        public HoloMemory memory() { return memory; }

        /** Conteneur ouvert, ou {@code null} si le marker n'est pas charge. */
        public PositionHoloContainer live() { return live; }
    }

    private final Map<String, Record> records = new LinkedHashMap<>();
    private final Collection<Record> recordsView = Collections.unmodifiableCollection(records.values());

    private HoloMarkers() {}

    public static HoloMarkers of(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(HoloMarkers::load, HoloMarkers::new, NAME);
    }

    /** Identifiant valide : minuscules, chiffres, {@code _ . -}, longueur limitee. */
    public static boolean validId(String id)
    {
        return id != null && id.length() <= HoloScope.Position.MAX_ID_LENGTH && ID_PATTERN.matcher(id).matches();
    }

    public Record get(String id) { return records.get(id); }

    public Collection<Record> records() { return recordsView; }

    /** Enregistre un nouveau marker avec une memoire vide ; {@code null} si l'id est invalide ou pris. */
    public Record add(String id, ResourceKey<Level> dimension, Vec3 position, UUID entity)
    {
        if (!validId(id) || records.containsKey(id)) return null;
        Record record = new Record(id, dimension, position, entity, new HoloMemory(id));
        records.put(id, record);
        setDirty();
        return record;
    }

    /** Oublie un marker et sa memoire ; son conteneur doit deja etre ferme. */
    public Record remove(String id)
    {
        Record record = records.remove(id);
        if (record != null) setDirty();
        return record;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();
        for (Record record : records.values()) {
            if (record.live != null) record.live.saveInto(record.memory);
            CompoundTag entry = new CompoundTag();
            entry.putString("id", record.id);
            entry.putString("dimension", record.dimension.location().toString());
            entry.putDouble("x", record.position.x);
            entry.putDouble("y", record.position.y);
            entry.putDouble("z", record.position.z);
            entry.putUUID("entity", record.entity);
            entry.put("memory", record.memory.save());
            list.add(entry);
        }
        tag.put("markers", list);
        return tag;
    }

    private static HoloMarkers load(CompoundTag tag)
    {
        HoloMarkers markers = new HoloMarkers();
        ListTag list = tag.getList("markers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String id = entry.getString("id");
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            if (!validId(id) || dimension == null || !entry.hasUUID("entity")) continue;
            markers.records.put(id, new Record(id, ResourceKey.create(Registries.DIMENSION, dimension),
                    new Vec3(entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z")),
                    entry.getUUID("entity"), HoloMemory.load(entry.getCompound("memory"))));
        }
        return markers;
    }
}
