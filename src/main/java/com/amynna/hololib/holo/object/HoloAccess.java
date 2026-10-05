package com.amynna.hololib.holo.object;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Qui peut <b>utiliser</b> un {@link HoloObject} interactible (le voir ne suffit pas : voir {@link HoloVisibility}).
 * Valeur immuable, synchronisee et sauvegardee par le framework comme la visibilite. Le proprietaire d'une memoire de
 * joueur est toujours autorise.
 * <ul>
 *     <li>{@link #OWNER} (defaut) : le proprietaire seul ; sans proprietaire (dimension, position) : personne, comme
 *     {@code PRIVATE}.</li>
 *     <li>{@link #players(Collection)} : le proprietaire et ces joueurs.</li>
 *     <li>{@link #EVERYONE} : tout joueur qui voit l'objet.</li>
 * </ul>
 * Le client ne propose l'interaction qu'aux joueurs autorises ; le serveur verifie a nouveau chaque action envoyee
 * avec l'objet pour source ({@code HoloModule.sendAction(HoloObject, ...)}).
 */
public final class HoloAccess
{
    /** Nombre maximal de joueurs lus depuis le reseau. */
    private static final int MAX_PLAYERS = 1024;

    public enum Mode
    {
        OWNER, PLAYERS, EVERYONE
    }

    public static final HoloAccess OWNER = new HoloAccess(Mode.OWNER, Set.of());
    public static final HoloAccess EVERYONE = new HoloAccess(Mode.EVERYONE, Set.of());

    private final Mode mode;
    private final Set<UUID> players;

    private HoloAccess(Mode mode, Set<UUID> players)
    {
        this.mode = mode;
        this.players = players;
    }

    /** Le proprietaire et ces joueurs ; {@link #OWNER} si la liste est vide. */
    public static HoloAccess players(Collection<UUID> players)
    {
        if (players.isEmpty()) return OWNER;
        return new HoloAccess(Mode.PLAYERS, Set.copyOf(players));
    }

    public Mode mode() { return mode; }

    /** Joueurs autorises en plus du proprietaire (mode {@code PLAYERS}), vue non modifiable. */
    public Set<UUID> players() { return players; }

    /**
     * Vrai si {@code player} peut utiliser l'objet.
     *
     * @param owner proprietaire de la memoire de l'objet, {@code null} pour une dimension ou une position
     */
    public boolean allows(UUID player, UUID owner)
    {
        if (player == null) return false;
        if (player.equals(owner)) return true;
        return switch (mode) {
            case OWNER -> false;
            case PLAYERS -> players.contains(player);
            case EVERYONE -> true;
        };
    }

    public void write(FriendlyByteBuf buf)
    {
        buf.writeByte(mode.ordinal());
        if (mode != Mode.PLAYERS) return;
        buf.writeVarInt(players.size());
        for (UUID player : players) buf.writeUUID(player);
    }

    public static HoloAccess read(FriendlyByteBuf buf)
    {
        int ordinal = buf.readByte();
        if (ordinal == Mode.EVERYONE.ordinal()) return EVERYONE;
        if (ordinal != Mode.PLAYERS.ordinal()) return OWNER;
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_PLAYERS) throw new IllegalArgumentException("Trop de joueurs : " + count);
        Set<UUID> players = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) players.add(buf.readUUID());
        return players(players);
    }

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        if (mode == Mode.PLAYERS) {
            ListTag list = new ListTag();
            for (UUID player : players) list.add(NbtUtils.createUUID(player));
            tag.put("players", list);
        }
        return tag;
    }

    public static HoloAccess load(CompoundTag tag)
    {
        String mode = tag.getString("mode");
        if (Mode.EVERYONE.name().equals(mode)) return EVERYONE;
        if (!Mode.PLAYERS.name().equals(mode)) return OWNER;
        Set<UUID> players = new LinkedHashSet<>();
        for (Tag entry : tag.getList("players", Tag.TAG_INT_ARRAY)) players.add(NbtUtils.loadUUID(entry));
        return players(players);
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || o instanceof HoloAccess other && mode == other.mode && players.equals(other.players);
    }

    @Override
    public int hashCode() { return Objects.hash(mode, players); }

    @Override
    public String toString() { return mode == Mode.PLAYERS ? "PLAYERS" + players : mode.name(); }
}
