package com.amynna.hololib.holo.memory.player;

import com.amynna.hololib.holo.inspect.HoloInspection;
import com.amynna.hololib.holo.inspect.HoloInspections;
import com.amynna.hololib.holo.memory.HoloContainer;
import com.amynna.hololib.holo.memory.HoloMemory;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.module.HoloModuleTarget;
import com.amynna.hololib.network.HoloNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Memoire active d'un joueur connecte. Toujours chargee pour son proprietaire ; repliquee a ses <b>viewers</b>, les
 * joueurs qui suivent son entite et l'inspectent ({@link HoloInspections}), et seulement pour les modules partages
 * (objets {@code PUBLIC}). Par defaut, personne ne voit les affichages d'un autre joueur.
 * <p>
 * Porte aussi l'inspection vivante du joueur (qui il peut voir), relue de la memoire active et recopiee dedans a
 * chaque sauvegarde. L'entite du joueur change au respawn : elle est retrouvee par UUID a chaque usage.
 */
public final class PlayerHoloContainer extends HoloContainer
{
    private final MinecraftServer server;
    private final UUID owner;
    private final HoloInspection inspection = new HoloInspection();
    private final Set<UUID> viewers = new HashSet<>();

    public PlayerHoloContainer(MinecraftServer server, UUID owner)
    {
        super(new HoloScope.Player(owner));
        this.server = server;
        this.owner = owner;
    }

    public UUID owner() { return owner; }

    /** Joueurs que ce joueur peut inspecter (etat vivant de la memoire active) ; modifier via {@link HoloInspections}. */
    public HoloInspection inspection() { return inspection; }

    /** Usage interne : joueurs qui recoivent actuellement cette memoire (geres par {@link HoloInspections}). */
    @ApiStatus.Internal
    public Set<UUID> viewers() { return viewers; }

    /** Entite actuelle du joueur, ou {@code null} s'il n'est pas connecte. */
    public ServerPlayer player() { return server.getPlayerList().getPlayer(owner); }

    /**
     * Active une autre memoire du joueur : sauvegarde et decharge les modules courants, charge ceux de la memoire
     * choisie, puis renvoie l'etat au proprietaire et a ses observateurs. L'inspection change avec la memoire :
     * l'appelant doit ensuite appeler {@code HoloInspections.refresh}. Faux si la memoire n'existe pas.
     */
    public boolean selectMemory(HoloMemoryBank bank, String name)
    {
        if (bank.find(name) == null) return false;
        if (bank.find(name) == bank.active()) return true;
        saveInto(bank.active());
        unload();
        bank.select(name);
        load(bank.active());
        sendToOwner(ownerSnapshot());
        sendToObservers(observerSnapshot());
        return true;
    }

    @Override
    public HoloModuleTarget target() { return HoloModuleTarget.PLAYER; }

    @Override
    public ServerLevel level()
    {
        ServerPlayer player = player();
        return player != null ? player.serverLevel() : null;
    }

    @Override
    protected void sendToOwner(Object packet)
    {
        ServerPlayer player = player();
        if (player != null) HoloNetwork.send(player, packet);
    }

    @Override
    protected void sendToObservers(Object packet)
    {
        for (UUID viewer : viewers) {
            ServerPlayer player = server.getPlayerList().getPlayer(viewer);
            if (player != null) HoloNetwork.send(player, packet);
        }
    }

    @Override
    protected void onMemoryLoaded(HoloMemory memory) { inspection.copyFrom(memory.inspection()); }

    @Override
    protected void onMemorySaving(HoloMemory memory) { memory.inspection().copyFrom(inspection); }

    /** Les donnees du joueur sont sauvegardees periodiquement et a la deconnexion. */
    @Override
    protected void markPersistDirty() {}
}
