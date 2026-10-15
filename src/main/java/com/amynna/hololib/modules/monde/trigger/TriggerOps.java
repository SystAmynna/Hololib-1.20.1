package com.amynna.hololib.modules.monde.trigger;

import com.amynna.hololib.holo.object.HoloAccess;
import com.amynna.hololib.holo.object.HoloObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Outils communs des fenetres de configuration reservees aux operateurs (Trigger Area Configurator, Trigger
 * Interrupter Configurator) : niveau de permission, operateur local cote client, acces de la fenetre.
 */
public final class TriggerOps
{
    /** Niveau de permission d'un operateur (comme les commandes du mod et l'edition). */
    public static final int OP_LEVEL = 2;

    private static Backend backend;

    private TriggerOps() {}

    /** Pont vers le client, installe au setup client. */
    public interface Backend
    {
        /** Vrai si le joueur local est operateur (niveau {@value TriggerOps#OP_LEVEL}). */
        boolean localOp();
    }

    /** Branche le client (setup client). */
    public static void install(Backend backend)
    {
        TriggerOps.backend = Objects.requireNonNull(backend);
    }

    /** Vrai si le joueur local est operateur ; faux sans backend (serveur dedie). */
    public static boolean localOp() { return backend != null && backend.localOp(); }

    /**
     * Acces de {@code window} = operateurs connectes ; {@code scratch} (vide en entree, vide en sortie) evite
     * d'allouer un ensemble par tick. Reaffecte seulement si l'ensemble change.
     */
    public static void updateAccess(HoloObject window, MinecraftServer server, Set<UUID> scratch)
    {
        scratch.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(OP_LEVEL)) scratch.add(player.getUUID());
        }
        HoloAccess access = window.access();
        Set<UUID> granted = access.mode() == HoloAccess.Mode.PLAYERS ? access.players() : Set.of();
        if (!granted.equals(scratch)) window.setAccess(HoloAccess.players(scratch));
        scratch.clear();
    }
}
