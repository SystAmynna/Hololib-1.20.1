package com.amynna.hololib.modules.camera.targeting;

import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.player.PlayerHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Module {@code hololib:targeting} (memoire de joueur) : <b>cible</b> du joueur, une entite ou un bloc, a l'usage
 * d'autres modules. Depend du module {@code hololib:focus} : a l'ouverture du focus, ce que le joueur regarde (jusqu'a
 * {@value #PICK_RANGE} blocs) devient la cible ; elle reste choisie tant que le focus est ouvert (meme si le joueur ou
 * la cible bouge) et est retiree a sa fermeture. Une entite reperee par un module {@link TargetingThroughWalls} (radar)
 * et regardee a travers un mur est preferee au bloc entre les deux.
 * <p>
 * Le <b>client du proprietaire decide</b> ({@code client.modules.camera.targeting.TargetingClient} : choix, validite
 * chaque tick, contour et segment) et envoie la cible ({@link #requestEntity}, {@link #requestBlock},
 * {@link #requestClear}) ; le serveur en garde une copie validee (entite du niveau du proprietaire ou bloc charge, a
 * {@value #SERVER_RANGE} blocs au plus), lue par les modules serveur ({@link #of}, {@link #targetPlayer}). Ni
 * sauvegardee ni renvoyee : le focus redemarre ferme. Aucun objet ; jamais envoye aux observateurs.
 * <p>
 * Action reseau : type (0 rien, 1 entite + id, 2 bloc + position). Code commun : aucune classe client.
 */
public final class TargetingModule extends HoloModule
{
    /** Portee du choix de la cible, en blocs. */
    public static final double PICK_RANGE = 64.0;
    /** Distance maximale acceptee par le serveur, en blocs (la cible peut s'eloigner pendant le focus). */
    public static final double SERVER_RANGE = 128.0;

    private static final byte NONE = 0;
    private static final byte ENTITY = 1;
    private static final byte BLOCK = 2;

    /** Id de l'entite ciblee, ou -1. */
    private int entityId = -1;
    /** Bloc cible, ou {@code null}. */
    private BlockPos block;

    // ---------------------------------------------------------------- lecture (deux cotes)

    public boolean hasTarget() { return entityId >= 0 || block != null; }

    /** Id de l'entite ciblee, ou -1. */
    public int targetEntityId() { return entityId; }

    /** Entite ciblee dans {@code level}, ou {@code null} (pas d'entite ciblee, ou inconnue de ce niveau). */
    public Entity targetEntity(Level level) { return entityId < 0 ? null : level.getEntity(entityId); }

    /** Bloc cible, ou {@code null}. */
    public BlockPos targetBlock() { return block; }

    // ---------------------------------------------------------------- serveur

    /** Cote serveur : module de la memoire active du joueur, ou {@code null}. */
    public static TargetingModule of(UUID player)
    {
        PlayerHoloContainer container = HoloContainers.player(player);
        if (container == null) return null;
        for (HoloModule module : container.modules()) {
            if (module instanceof TargetingModule targeting) return targeting;
        }
        return null;
    }

    /** Cote serveur : joueur cible, ou {@code null} (rien, un bloc, une autre entite). */
    public ServerPlayer targetPlayer(ServerLevel level)
    {
        return targetEntity(level) instanceof ServerPlayer player ? player : null;
    }

    @Override
    public void serverTick(ServerLevel level)
    {
        if (entityId < 0) return;
        Entity entity = level.getEntity(entityId);
        if (entity == null || entity.isRemoved()) entityId = -1;
    }

    @Override
    public void handleAction(ServerPlayer player, FriendlyByteBuf buf)
    {
        if (!player.getUUID().equals(owner())) return;
        entityId = -1;
        block = null;
        byte kind = buf.readByte();
        ServerLevel level = player.serverLevel();
        double max = SERVER_RANGE * SERVER_RANGE;
        if (kind == ENTITY) {
            Entity entity = level.getEntity(buf.readVarInt());
            if (entity != null && entity != player && entity.distanceToSqr(player) <= max) entityId = entity.getId();
        } else if (kind == BLOCK) {
            BlockPos pos = buf.readBlockPos();
            if (level.isLoaded(pos) && pos.getCenter().distanceToSqr(player.position()) <= max) block = pos;
        }
    }

    // ---------------------------------------------------------------- client (proprietaire)

    /** Cote client (proprietaire) : cible une entite, localement puis sur le serveur. */
    public void requestEntity(Entity entity)
    {
        if (!host().canAct() || entity.getId() == entityId && block == null) return;
        entityId = entity.getId();
        block = null;
        int id = entityId;
        sendAction(buf -> {
            buf.writeByte(ENTITY);
            buf.writeVarInt(id);
        });
    }

    /** Cote client (proprietaire) : cible un bloc, localement puis sur le serveur. */
    public void requestBlock(BlockPos pos)
    {
        if (!host().canAct() || entityId < 0 && pos.equals(block)) return;
        entityId = -1;
        block = pos.immutable();
        BlockPos sent = block;
        sendAction(buf -> {
            buf.writeByte(BLOCK);
            buf.writeBlockPos(sent);
        });
    }

    /** Cote client (proprietaire) : retire la cible, localement puis sur le serveur. */
    public void requestClear()
    {
        if (!host().canAct() || !hasTarget()) return;
        entityId = -1;
        block = null;
        sendAction(buf -> buf.writeByte(NONE));
    }
}
