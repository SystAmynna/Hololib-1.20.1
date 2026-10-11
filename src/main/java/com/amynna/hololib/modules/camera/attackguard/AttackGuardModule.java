package com.amynna.hololib.modules.camera.attackguard;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.player.PlayerHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.render.HoloRenderCanceler;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.camera.config.Configurable;
import com.amynna.hololib.modules.camera.keys.KeySet;
import com.amynna.hololib.modules.camera.keys.KeysModule;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Module {@code hololib:attack_guard} (memoire de joueur, {@link Configurable}) : empeche le proprietaire d'attaquer
 * un joueur <b>protege</b>, qui possede (module {@code hololib:keys}) l'une des cles de la <b>whitelist</b> du module.
 * <ul>
 *     <li>Client du proprietaire ({@code client.modules.camera.attackguard.AttackGuardClient}) : en regard direct
 *     seulement (rayon du regard occulte par les blocs ; le radar n'y change rien), <b>sans limite de distance</b>
 *     (armes a distance), l'attaque est interceptee et le reticule remplace par une croix rouge.</li>
 *     <li>Serveur : {@code AttackEntityEvent} annule pour un joueur protege (latence, client modifie).</li>
 * </ul>
 * Le client ne connait pas les cles des autres joueurs : le serveur lui envoie, toutes les {@value #SCAN_TICKS}
 * ticks, les ids des joueurs proteges de son niveau, sans limite de distance ({@link #isProtected}). Whitelist
 * sauvegardee, editee dans la fenetre de Config ({@link AttackGuardConfigWindow}) ; whitelist et ids envoyes au
 * proprietaire seulement. Actions : {@code ADD} / {@code REMOVE} d'une cle.
 */
public final class AttackGuardModule extends HoloModule implements Configurable
{
    static final int SCAN_TICKS = 10;

    private static final byte ACTION_ADD = 0;
    private static final byte ACTION_REMOVE = 1;
    private static final Component NAME = Component.literal("Attack Guard");

    private final KeySet whitelist = new KeySet();
    /** Ids des joueurs proteges proches (serveur -> proprietaire). */
    private final IntOpenHashSet protectedIds = new IntOpenHashSet();
    private final IntOpenHashSet scratch = new IntOpenHashSet();
    private int scanTicks;

    private AttackGuardConfigWindow window;

    /** Whitelist (lecture). */
    KeySet whitelist() { return whitelist; }

    /** Vrai si l'entite d'id {@code entityId} est un joueur protege proche (etat recu du serveur). */
    public boolean isProtected(int entityId) { return protectedIds.contains(entityId); }

    @Override
    public void onLoad()
    {
        window = addObject(new AttackGuardConfigWindow("config", NAME, this));
        window.refresh();
    }

    @Override
    public void onUnload() { HoloRenderCanceler.restoreAll(this); }

    @Override
    public void onSync() { window.refresh(); }

    @Override
    public Component configName() { return NAME; }

    @Override
    public ConfigWindow configWindow() { return window; }

    // ---------------------------------------------------------------- client (proprietaire)

    void requestAdd(String key)
    {
        if (host().canAct()) sendAction(buf -> writeKey(buf, ACTION_ADD, key));
    }

    void requestRemove(String key)
    {
        if (host().canAct()) sendAction(buf -> writeKey(buf, ACTION_REMOVE, key));
    }

    private static void writeKey(FriendlyByteBuf buf, byte action, String key)
    {
        buf.writeByte(action);
        buf.writeUtf(key, KeySet.MAX_LENGTH);
    }

    // ---------------------------------------------------------------- serveur

    /** Cote serveur : module de la memoire active du joueur, ou {@code null}. */
    public static AttackGuardModule of(UUID player)
    {
        PlayerHoloContainer container = HoloContainers.player(player);
        if (container == null) return null;
        for (HoloModule module : container.modules()) {
            if (module instanceof AttackGuardModule guard) return guard;
        }
        return null;
    }

    /** Cote serveur : vrai si {@code target} possede une cle de la whitelist. */
    public boolean protects(Player target)
    {
        if (whitelist.size() == 0 || target.getUUID().equals(owner())) return false;
        KeysModule keys = KeysModule.of(target.getUUID());
        return keys != null && keys.haveAnyKey(whitelist);
    }

    @Override
    public void serverTick(ServerLevel level)
    {
        if (--scanTicks > 0) return;
        scanTicks = SCAN_TICKS;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(owner());
        scratch.clear();
        if (owner != null) {
            for (ServerPlayer player : owner.serverLevel().players()) {
                if (player != owner && protects(player)) scratch.add(player.getId());
            }
        }
        if (scratch.equals(protectedIds)) return;
        protectedIds.clear();
        protectedIds.addAll(scratch);
        markDirty();
    }

    @Override
    public void handleAction(ServerPlayer player, FriendlyByteBuf buf)
    {
        if (!player.getUUID().equals(owner())) return;
        byte action = buf.readByte();
        String key = buf.readUtf(KeySet.MAX_LENGTH);
        boolean changed = action == ACTION_ADD ? whitelist.add(key) : action == ACTION_REMOVE && whitelist.remove(key);
        if (!changed) return;
        scanTicks = 0;
        markDirty();
    }

    @Override
    public void save(CompoundTag tag) { whitelist.save(tag, "whitelist"); }

    @Override
    public void load(CompoundTag tag) { whitelist.load(tag, "whitelist"); }

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeBoolean(forOwner);
        if (!forOwner) return;
        whitelist.write(buf);
        buf.writeVarInt(protectedIds.size());
        for (int id : protectedIds) buf.writeVarInt(id);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        if (!buf.readBoolean()) return;
        whitelist.read(buf);
        protectedIds.clear();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) protectedIds.add(buf.readVarInt());
    }

    /** Verification serveur : attaque d'un joueur protege annulee. */
    @Mod.EventBusSubscriber(modid = HoloLib.MODID)
    public static final class ServerGuard
    {
        private ServerGuard() {}

        @SubscribeEvent
        public static void onAttack(AttackEntityEvent event)
        {
            if (!(event.getEntity() instanceof ServerPlayer attacker)) return;
            if (!(event.getTarget() instanceof Player target)) return;
            AttackGuardModule guard = of(attacker.getUUID());
            if (guard != null && guard.protects(target)) event.setCanceled(true);
        }
    }
}
