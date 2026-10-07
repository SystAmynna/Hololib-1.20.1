package com.amynna.hololib.holo.memory;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.edit.HoloEditRules;
import com.amynna.hololib.holo.edit.HoloEditorCapability;
import com.amynna.hololib.holo.inspect.HoloInspections;
import com.amynna.hololib.holo.memory.dimension.DimensionHoloContainer;
import com.amynna.hololib.holo.memory.player.HoloMemoryBank;
import com.amynna.hololib.holo.memory.player.PlayerHoloContainer;
import com.amynna.hololib.holo.memory.player.PlayerMemoryCapability;
import com.amynna.hololib.holo.memory.position.HoloMarkers;
import com.amynna.hololib.holo.memory.position.PositionHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.network.HoloEditObjectPacket;
import com.amynna.hololib.network.HoloForgetPacket;
import com.amynna.hololib.network.HoloModuleActionPacket;
import com.amynna.hololib.network.HoloMoveObjectPacket;
import com.amynna.hololib.network.HoloNetwork;
import com.amynna.hololib.network.HoloObjectPacket;
import com.amynna.hololib.network.HoloSyncData.ObjectData;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Index des memoires chargees cote serveur et API serveur. Fil serveur uniquement.
 * <ul>
 *     <li><b>Joueur</b> : memoire active chargee a la connexion ; observateurs = joueurs qui suivent son entite et
 *     l'inspectent ({@link HoloInspections}).</li>
 *     <li><b>Dimension</b> : chargee avec le niveau ; observateurs = joueurs de la dimension.</li>
 *     <li><b>Position</b> : portee par un marker ({@link HoloMarkers}), chargee avec l'entite ; observateurs = joueurs
 *     qui suivent le chunk du marker.</li>
 * </ul>
 * Cote client, les portees autres que celle du joueur local sont oubliees au respawn et au changement de dimension ;
 * le serveur les renvoie a l'arrivee (dimension) et via le suivi des entites et des chunks.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID)
public final class HoloContainers
{
    private static final Map<UUID, PlayerHoloContainer> PLAYERS = new LinkedHashMap<>();
    private static final Collection<PlayerHoloContainer> PLAYERS_VIEW =
            Collections.unmodifiableCollection(PLAYERS.values());
    private static final Map<ResourceKey<Level>, DimensionHoloContainer> DIMENSIONS = new LinkedHashMap<>();
    private static final Map<String, PositionHoloContainer> POSITIONS = new LinkedHashMap<>();
    /** Copie reutilisee pour le tick : un module peut ouvrir ou fermer une memoire pendant son tick. */
    private static final List<HoloContainer> TICKING = new ArrayList<>();

    private HoloContainers() {}

    /** Bus du mod. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.register(HoloMemoryBank.class);
    }

    // ---------------------------------------------------------------- acces

    /** Memoire active du joueur, ou {@code null} s'il n'est pas (encore) connecte. */
    public static PlayerHoloContainer player(ServerPlayer player) { return PLAYERS.get(player.getUUID()); }

    /** Memoire active du joueur d'UUID {@code player}, ou {@code null} s'il n'est pas (encore) connecte. */
    public static PlayerHoloContainer player(UUID player) { return PLAYERS.get(player); }

    /** Memoires actives de tous les joueurs connectes. */
    public static Collection<PlayerHoloContainer> players() { return PLAYERS_VIEW; }

    /** Memoire de la dimension, ou {@code null} si le niveau n'est pas charge. */
    public static DimensionHoloContainer dimension(ServerLevel level) { return DIMENSIONS.get(level.dimension()); }

    /** Memoire de position ouverte du marker, ou {@code null} s'il n'est pas charge. */
    public static PositionHoloContainer position(String markerId) { return POSITIONS.get(markerId); }

    // ---------------------------------------------------------------- markers

    /**
     * Cree un marker du mod en {@code position} et l'enregistre avec une memoire vide ; {@code null} si l'id est
     * invalide ou deja pris.
     */
    public static HoloMarkers.Record createMarker(ServerLevel level, String id, Vec3 position)
    {
        HoloMarkers markers = HoloMarkers.of(level.getServer());
        if (!HoloMarkers.validId(id) || markers.get(id) != null) return null;
        Marker marker = EntityType.MARKER.create(level);
        if (marker == null) return null;
        marker.moveTo(position.x, position.y, position.z);
        marker.addTag(HoloMarkers.TAG);
        marker.setCustomName(Component.literal(id));
        HoloMarkers.Record record = markers.add(id, level.dimension(), position, marker.getUUID());
        // L'ajout au niveau ouvre la memoire (onEntityJoin).
        level.addFreshEntity(marker);
        return record;
    }

    /**
     * Supprime un marker et sa memoire. L'entite est retiree si elle est chargee, sinon elle le sera a son prochain
     * chargement (id inconnu). Faux si l'id n'existe pas.
     */
    public static boolean removeMarker(MinecraftServer server, String id)
    {
        HoloMarkers markers = HoloMarkers.of(server);
        HoloMarkers.Record record = markers.get(id);
        if (record == null) return false;
        PositionHoloContainer container = POSITIONS.remove(id);
        if (container != null) {
            container.close();
            container.entity().discard();
        }
        markers.remove(id);
        return true;
    }

    // ---------------------------------------------------------------- memoires de joueur

    /** Cree une memoire de joueur vide ; {@code null} si le nom est invalide ou pris. */
    public static HoloMemory createMemory(ServerPlayer player, String name)
    {
        return HoloMemoryBank.of(player).create(name);
    }

    /** Supprime une memoire de joueur inactive ; faux si elle n'existe pas ou est active. */
    public static boolean deleteMemory(ServerPlayer player, String name)
    {
        return HoloMemoryBank.of(player).delete(name);
    }

    /** Active une autre memoire du joueur (et ses acces d'inspection) ; faux si elle n'existe pas. */
    public static boolean selectMemory(ServerPlayer player, String name)
    {
        PlayerHoloContainer container = player(player);
        if (container == null || !container.selectMemory(HoloMemoryBank.of(player), name)) return false;
        HoloInspections.refresh(player.server, player.getUUID());
        return true;
    }

    // ---------------------------------------------------------------- reseau entrant

    public static void handleMove(ServerPlayer player, HoloMoveObjectPacket packet)
    {
        if (!player.getUUID().equals(packet.scope().owner())) return;
        PlayerHoloContainer container = player(player);
        HoloModule module = container != null ? container.module(packet.module()) : null;
        HoloObject object = module != null ? module.object(packet.key()) : null;
        if (object == null) return;
        if (packet.anchor().acceptable(packet.transform())) {
            object.setAnchor(packet.anchor());
            object.transform().set(packet.transform());
        } else {
            // Refus : on renvoie le placement serveur au client, qui l'avait deja applique localement.
            HoloNetwork.send(player, new HoloObjectPacket(container.scope(), module.instanceId(),
                    ObjectData.of(object, true)));
        }
    }

    public static void handleAction(ServerPlayer player, HoloModuleActionPacket packet)
    {
        boolean sourced = packet.source() != null;
        HoloContainer container = actionTarget(player, packet.scope(), sourced);
        HoloModule module = container != null ? container.module(packet.module()) : null;
        if (module == null) return;
        HoloObject source = null;
        if (sourced) {
            source = module.object(packet.source());
            if (source == null || !mayUse(player, source)) return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(packet.payload()));
        try {
            module.handleAction(player, source, buf);
        } catch (RuntimeException e) {
            HoloLib.LOGGER.warn("Action invalide de {} pour le module {}", player.getGameProfile().getName(),
                    module.type().id(), e);
        } finally {
            buf.release();
        }
    }

    /**
     * Edition en jeu : appliquee si le joueur voit l'objet (conteneur qu'il recoit, objet {@code PUBLIC} s'il n'en est
     * pas proprietaire), peut l'editer ({@link HoloEditRules#mayEdit}, capacites des modules editeurs de sa memoire)
     * et ne change que ce que ses interfaces permettent ; sinon l'etat serveur lui est renvoye (il l'avait deja
     * applique localement).
     */
    public static void handleEdit(ServerPlayer player, HoloEditObjectPacket packet)
    {
        HoloContainer container = actionTarget(player, packet.scope(), true);
        HoloModule module = container != null ? container.module(packet.module()) : null;
        HoloObject object = module != null ? module.object(packet.key()) : null;
        if (object == null) return;
        UUID owner = module.owner();
        boolean ownerView = player.getUUID().equals(owner);
        boolean visible = ownerView || object.visibility() == HoloVisibility.PUBLIC;
        PlayerHoloContainer own = player(player);
        Set<HoloEditorCapability> capabilities = own != null ? HoloEditRules.capabilities(own.modules())
                : Set.of();
        if (visible && HoloEditRules.mayEdit(player.getUUID(), player.hasPermissions(2), container.scope(),
                module.editOwner(), object, capabilities)
                && HoloEditRules.accepts(object, owner != null, packet.anchor(), packet.transform(), packet.display(),
                packet.openOffset(), packet.editRange())) {
            object.setAnchor(packet.anchor());
            object.transform().set(packet.transform());
            object.setDisplay(packet.display());
            object.setEditRange(packet.editRange());
            if (packet.openOffset() != null) object.openOffset().set(packet.openOffset());
            return;
        }
        if (visible) {
            HoloNetwork.send(player, new HoloObjectPacket(container.scope(), module.instanceId(),
                    ObjectData.of(object, ownerView)));
        }
    }

    /**
     * Conteneur vise par une action, si le joueur a le droit d'y agir. Memoire de joueur : son proprietaire ; avec un
     * objet source, aussi un joueur qui la recoit (viewer : il suit et inspecte le proprietaire).
     */
    private static HoloContainer actionTarget(ServerPlayer player, HoloScope scope, boolean sourced)
    {
        if (scope instanceof HoloScope.Player owned) {
            if (player.getUUID().equals(owned.owner())) return player(player);
            PlayerHoloContainer container = sourced ? PLAYERS.get(owned.owner()) : null;
            return container != null && container.viewers().contains(player.getUUID()) ? container : null;
        }
        if (scope instanceof HoloScope.Dimension) return dimension(player.serverLevel());
        if (scope instanceof HoloScope.Position position) {
            PositionHoloContainer container = position(position.markerId());
            if (container == null || container.level() != player.serverLevel()) return null;
            double reach = (player.server.getPlayerList().getViewDistance() + 1) * 16.0;
            return container.entity().distanceToSqr(player) <= reach * reach ? container : null;
        }
        return null;
    }

    /** Vrai si le joueur peut utiliser l'objet : acces accorde, et objet visible pour lui. */
    private static boolean mayUse(ServerPlayer player, HoloObject object)
    {
        UUID owner = object.module().owner();
        if (player.getUUID().equals(owner)) return true;
        return object.visibility() == HoloVisibility.PUBLIC && object.access().allows(player.getUUID(), owner);
    }

    // ---------------------------------------------------------------- joueurs

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerHoloContainer container = new PlayerHoloContainer(player.server, player.getUUID());
        container.load(HoloMemoryBank.of(player).active());
        PLAYERS.put(player.getUUID(), container);
        HoloNetwork.send(player, container.ownerSnapshot());
        // Les joueurs proches suivent deja l'entite : ils n'ont pas pu recevoir l'etat a StartTracking.
        HoloInspections.loggedIn(player, container);
        sendDimension(player);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerHoloContainer container = PLAYERS.remove(player.getUUID());
        if (container == null) return;
        HoloInspections.loggedOut(player, container);
        // Le joueur est sauvegarde juste apres cet evenement : la memoire active doit etre a jour.
        player.getCapability(PlayerMemoryCapability.BANK).ifPresent(bank -> container.saveInto(bank.active()));
        container.unload();
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) sendDimension(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player) sendDimension(player);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event)
    {
        if (event.getEntity() instanceof ServerPlayer tracker && event.getTarget() instanceof Player target) {
            HoloInspections.startTracking(tracker, target.getUUID());
        }
    }

    @SubscribeEvent
    public static void onStopTracking(PlayerEvent.StopTracking event)
    {
        if (event.getEntity() instanceof ServerPlayer tracker && event.getTarget() instanceof Player target) {
            HoloInspections.stopTracking(tracker, target.getUUID());
        }
    }

    /** Synchronisation persistante du joueur (capability) : recopie l'etat vivant dans la memoire active. */
    public static void saveLivePlayer(UUID owner, HoloMemoryBank bank)
    {
        PlayerHoloContainer container = PLAYERS.get(owner);
        if (container != null) container.saveInto(bank.active());
    }

    private static void sendDimension(ServerPlayer player)
    {
        DimensionHoloContainer container = dimension(player.serverLevel());
        if (container != null) HoloNetwork.send(player, container.observerSnapshot());
    }

    // ---------------------------------------------------------------- dimensions

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event)
    {
        if (event.getLevel() instanceof ServerLevel level) {
            DIMENSIONS.put(level.dimension(), DimensionHoloContainer.open(level));
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event)
    {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        DimensionHoloContainer container = DIMENSIONS.remove(level.dimension());
        if (container != null) container.close();
        POSITIONS.values().removeIf(position -> position.level() == level);
    }

    // ---------------------------------------------------------------- markers (memoires de position)

    /** Un marker du mod rejoint le niveau : ouvre sa memoire, ou le retire s'il n'est plus enregistre. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level) || !isHoloMarker(event.getEntity())) return;
        Marker marker = (Marker) event.getEntity();
        String id = marker.getCustomName() != null ? marker.getCustomName().getString() : "";
        HoloMarkers markers = HoloMarkers.of(level.getServer());
        HoloMarkers.Record record = markers.get(id);
        if (record == null || !record.entity().equals(marker.getUUID()) || record.live() != null) {
            // Marker supprime pendant qu'il n'etait pas charge, ou copie : il n'a pas de memoire.
            event.setCanceled(true);
            return;
        }
        POSITIONS.put(id, PositionHoloContainer.open(marker, record, markers));
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel) || !isHoloMarker(event.getEntity())) return;
        Entity entity = event.getEntity();
        String id = entity.getCustomName() != null ? entity.getCustomName().getString() : "";
        PositionHoloContainer container = POSITIONS.get(id);
        if (container != null && container.entity() == entity) {
            POSITIONS.remove(id);
            container.close();
        }
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event)
    {
        for (PositionHoloContainer container : POSITIONS.values()) {
            if (container.level() == event.getLevel() && container.chunk().equals(event.getPos())) {
                HoloNetwork.send(event.getPlayer(), container.observerSnapshot());
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnWatch(ChunkWatchEvent.UnWatch event)
    {
        for (PositionHoloContainer container : POSITIONS.values()) {
            if (container.level() == event.getLevel() && container.chunk().equals(event.getPos())) {
                HoloNetwork.send(event.getPlayer(), new HoloForgetPacket(container.scope()));
            }
        }
    }

    private static boolean isHoloMarker(Entity entity)
    {
        return entity instanceof Marker && entity.getTags().contains(HoloMarkers.TAG);
    }

    // ---------------------------------------------------------------- tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        TICKING.clear();
        TICKING.addAll(PLAYERS.values());
        TICKING.addAll(DIMENSIONS.values());
        TICKING.addAll(POSITIONS.values());
        for (int i = 0; i < TICKING.size(); i++) {
            HoloContainer container = TICKING.get(i);
            if (container instanceof PositionHoloContainer position) position.trackMovement();
            container.tick();
            container.flush();
        }
        TICKING.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        PLAYERS.clear();
        HoloInspections.clear();
        DIMENSIONS.clear();
        POSITIONS.clear();
    }
}
