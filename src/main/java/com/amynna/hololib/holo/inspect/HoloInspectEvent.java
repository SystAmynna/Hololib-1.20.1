package com.amynna.hololib.holo.inspect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * Demande d'acces d'inspection : {@link #viewer()} veut voir les affichages de {@link #target()} (ou de tous les
 * joueurs). Poste sur {@code MinecraftForge.EVENT_BUS}, cote serveur, avant chaque activation non forcee
 * ({@code /hololib_inspect on}) ; l'annuler refuse la demande. Jamais poste pour un retrait ni pour
 * {@code /hololib_force_inspect}.
 */
@Cancelable
public class HoloInspectEvent extends Event
{
    private final ServerPlayer viewer;
    private final ServerPlayer target;
    private final Entity requester;

    public HoloInspectEvent(ServerPlayer viewer, @Nullable ServerPlayer target, @Nullable Entity requester)
    {
        this.viewer = viewer;
        this.target = target;
        this.requester = requester;
    }

    /** Joueur qui obtiendrait l'acces. */
    public ServerPlayer viewer() { return viewer; }

    /** Joueur inspecte, ou {@code null} pour tous les joueurs. */
    @Nullable
    public ServerPlayer target() { return target; }

    /** Entite a l'origine de la demande (souvent {@link #viewer()}), ou {@code null} (console, bloc de commande). */
    @Nullable
    public Entity requester() { return requester; }
}
