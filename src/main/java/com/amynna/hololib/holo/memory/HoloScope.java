package com.amynna.hololib.holo.memory;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Portee d'un ensemble de modules : a qui ou a quoi appartient la memoire qui les sauvegarde. Sert d'identifiant
 * reseau commun au serveur et au client.
 * <ul>
 *     <li>{@link Player} : memoire active d'un joueur.</li>
 *     <li>{@link Dimension} : memoire de la dimension (celle du destinataire cote client), accessible partout.</li>
 *     <li>{@link Position} : memoire portee par un marker du monde, chargee avec lui.</li>
 * </ul>
 * Seule la portee joueur a un proprietaire ; pour les autres, tous les destinataires sont des observateurs.
 */
public sealed interface HoloScope permits HoloScope.Player, HoloScope.Dimension, HoloScope.Position
{
    Dimension DIMENSION = new Dimension();

    /** Proprietaire, ou {@code null} pour une portee sans proprietaire. */
    default UUID owner() { return null; }

    void write(FriendlyByteBuf buf);

    record Player(UUID owner) implements HoloScope
    {
        @Override
        public void write(FriendlyByteBuf buf)
        {
            buf.writeByte(0);
            buf.writeUUID(owner);
        }
    }

    record Dimension() implements HoloScope
    {
        @Override
        public void write(FriendlyByteBuf buf) { buf.writeByte(1); }
    }

    /** @param markerId identifiant du marker qui porte la memoire */
    record Position(String markerId) implements HoloScope
    {
        /** Longueur maximale d'un identifiant de marker. */
        public static final int MAX_ID_LENGTH = 64;

        @Override
        public void write(FriendlyByteBuf buf)
        {
            buf.writeByte(2);
            buf.writeUtf(markerId, MAX_ID_LENGTH);
        }
    }

    static HoloScope read(FriendlyByteBuf buf)
    {
        int kind = buf.readByte();
        return switch (kind) {
            case 0 -> new Player(buf.readUUID());
            case 1 -> DIMENSION;
            case 2 -> new Position(buf.readUtf(Position.MAX_ID_LENGTH));
            default -> throw new IllegalArgumentException("Portee inconnue : " + kind);
        };
    }
}
