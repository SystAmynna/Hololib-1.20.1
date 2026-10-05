package com.amynna.hololib.holo.inspect;

import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.player.PlayerHoloContainer;
import com.amynna.hololib.network.HoloForgetPacket;
import com.amynna.hololib.network.HoloNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Acces d'inspection entre joueurs, cote serveur (fil serveur uniquement). Le serveur fait autorite : la memoire d'un
 * joueur n'est envoyee a un autre joueur (ses <b>viewers</b>, voir {@link PlayerHoloContainer#viewers()}) que si
 * celui-ci suit son entite <b>et</b> que l'inspection de sa memoire active l'autorise ({@link HoloInspection}).
 * <p>
 * Le suivi des entites est indexe ici ({@link #startTracking}), independamment de l'existence des conteneurs : Forge
 * signale le suivi avant {@code PlayerLoggedInEvent}. Les ensembles de viewers sont recalcules a chaque changement
 * (suivi, connexion, commande, changement de memoire) et les clients recoivent l'etat ou l'oubli en consequence.
 */
public final class HoloInspections
{
    /** Resultat d'une demande. */
    public enum Result
    {
        /** Acces modifie. */
        CHANGED,
        /** L'acces etait deja dans l'etat demande. */
        UNCHANGED,
        /** Refuse par un abonne a {@link HoloInspectEvent}. */
        DENIED,
        /** Memoire du joueur indisponible (connexion en cours). */
        UNAVAILABLE
    }

    /** Joueur suivi -> joueurs qui suivent son entite. */
    private static final Map<UUID, Set<UUID>> TRACKERS = new HashMap<>();

    private HoloInspections() {}

    /**
     * Donne ou retire a {@code viewer} l'acces aux affichages de {@code target} ({@code null} = tous les joueurs), dans
     * sa memoire active, puis synchronise les clients.
     *
     * @param force     ignore la politique ({@link HoloInspectEvent} n'est pas poste)
     * @param requester entite a l'origine de la demande, transmise a l'evenement
     */
    public static Result request(ServerPlayer viewer, @Nullable ServerPlayer target, boolean enable, boolean force,
                                 @Nullable Entity requester)
    {
        PlayerHoloContainer container = HoloContainers.player(viewer);
        if (container == null) return Result.UNAVAILABLE;
        if (enable && !force && MinecraftForge.EVENT_BUS.post(new HoloInspectEvent(viewer, target, requester))) {
            return Result.DENIED;
        }
        HoloInspection inspection = container.inspection();
        boolean changed = target == null ? inspection.setAll(enable) : inspection.set(target.getUUID(), enable);
        if (!changed) return Result.UNCHANGED;
        refresh(viewer.server, viewer.getUUID());
        return Result.CHANGED;
    }

    /** Vrai si {@code viewer} doit recevoir les affichages de {@code owner}. */
    public static boolean canView(UUID viewer, UUID owner)
    {
        if (viewer.equals(owner) || !TRACKERS.getOrDefault(owner, Collections.emptySet()).contains(viewer)) {
            return false;
        }
        PlayerHoloContainer container = HoloContainers.player(viewer);
        return container != null && container.inspection().allows(owner);
    }

    /** Recalcule ce que {@code viewer} recoit de chaque autre joueur (son inspection a change). */
    public static void refresh(MinecraftServer server, UUID viewer)
    {
        for (PlayerHoloContainer owner : HoloContainers.players()) update(server, owner, viewer);
    }

    // ---------------------------------------------------------------- evenements (appeles par HoloContainers)

    /** {@code tracker} commence a suivre l'entite du joueur {@code owner}. */
    public static void startTracking(ServerPlayer tracker, UUID owner)
    {
        TRACKERS.computeIfAbsent(owner, id -> new HashSet<>()).add(tracker.getUUID());
        PlayerHoloContainer container = HoloContainers.player(owner);
        if (container != null) update(tracker.server, container, tracker.getUUID());
    }

    /** {@code tracker} ne suit plus l'entite du joueur {@code owner}. */
    public static void stopTracking(ServerPlayer tracker, UUID owner)
    {
        Set<UUID> trackers = TRACKERS.get(owner);
        if (trackers != null && trackers.remove(tracker.getUUID()) && trackers.isEmpty()) TRACKERS.remove(owner);
        PlayerHoloContainer container = HoloContainers.player(owner);
        if (container != null) update(tracker.server, container, tracker.getUUID());
    }

    /** Le conteneur de {@code player} vient d'etre ouvert : envoie sa memoire a ses viewers, et la leur a lui. */
    public static void loggedIn(ServerPlayer player, PlayerHoloContainer container)
    {
        for (UUID tracker : TRACKERS.getOrDefault(player.getUUID(), Collections.emptySet())) {
            update(player.server, container, tracker);
        }
        refresh(player.server, player.getUUID());
    }

    /**
     * Le conteneur de {@code player} va etre ferme : ses viewers l'oublient, et il n'est plus viewer de personne (sans
     * rien lui envoyer : il se deconnecte).
     */
    public static void loggedOut(ServerPlayer player, PlayerHoloContainer container)
    {
        HoloForgetPacket forget = new HoloForgetPacket(container.scope());
        for (UUID viewer : container.viewers()) {
            ServerPlayer target = player.server.getPlayerList().getPlayer(viewer);
            if (target != null) HoloNetwork.send(target, forget);
        }
        container.viewers().clear();
        for (PlayerHoloContainer owner : HoloContainers.players()) owner.viewers().remove(player.getUUID());
    }

    public static void clear() { TRACKERS.clear(); }

    // ---------------------------------------------------------------- interne

    /** Ajoute ou retire {@code viewer} des viewers de {@code owner}, et lui envoie l'etat ou l'oubli. */
    private static void update(MinecraftServer server, PlayerHoloContainer owner, UUID viewer)
    {
        boolean view = canView(viewer, owner.owner());
        Set<UUID> viewers = owner.viewers();
        if (view == viewers.contains(viewer)) return;
        ServerPlayer player = server.getPlayerList().getPlayer(viewer);
        if (view) {
            viewers.add(viewer);
            if (player != null) HoloNetwork.send(player, owner.observerSnapshot());
        } else {
            viewers.remove(viewer);
            if (player != null) HoloNetwork.send(player, new HoloForgetPacket(owner.scope()));
        }
    }
}
