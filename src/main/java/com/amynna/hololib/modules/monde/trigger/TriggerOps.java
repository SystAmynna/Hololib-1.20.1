package com.amynna.hololib.modules.monde.trigger;

import com.amynna.hololib.holo.object.HoloAccess;
import com.amynna.hololib.holo.object.HoloObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Outils communs des fenetres de configuration reservees aux operateurs de niveau {@value #CONFIG_LEVEL} (Trigger
 * Area Configurator, Trigger Interrupter Configurator) : niveau de permission, operateur local cote client, acces de
 * la fenetre.
 */
public final class TriggerOps
{
    /** Niveau de permission d'un operateur (comme les commandes du mod et l'edition ; regles d'usage « OP requis »). */
    public static final int OP_LEVEL = 2;
    /**
     * Niveau requis pour <b>configurer</b> les Trigger : les commandes saisies s'executent sans les droits du
     * declencheur ({@link TriggerCommands#PERMISSION}), donc seuls les operateurs complets les ecrivent.
     */
    public static final int CONFIG_LEVEL = 4;

    private static Backend backend;

    private TriggerOps() {}

    /** Pont vers le client, installe au setup client. */
    public interface Backend
    {
        /** Vrai si le joueur local peut configurer les Trigger (niveau {@value TriggerOps#CONFIG_LEVEL}). */
        boolean localOp();
    }

    /** Branche le client (setup client). */
    public static void install(Backend backend)
    {
        TriggerOps.backend = Objects.requireNonNull(backend);
    }

    /** Vrai si le joueur local peut configurer les Trigger ; faux sans backend (serveur dedie). */
    public static boolean localOp() { return backend != null && backend.localOp(); }

    /**
     * Acces de {@code window} = operateurs connectes de niveau {@value #CONFIG_LEVEL} ; {@code scratch} (vide en entree, vide en sortie) evite
     * d'allouer un ensemble par tick. Reaffecte seulement si l'ensemble change.
     */
    public static void updateAccess(HoloObject window, MinecraftServer server, Set<UUID> scratch)
    {
        scratch.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(CONFIG_LEVEL)) scratch.add(player.getUUID());
        }
        HoloAccess access = window.access();
        Set<UUID> granted = access.mode() == HoloAccess.Mode.PLAYERS ? access.players() : Set.of();
        if (!granted.equals(scratch)) window.setAccess(HoloAccess.players(scratch));
        scratch.clear();
    }
}
