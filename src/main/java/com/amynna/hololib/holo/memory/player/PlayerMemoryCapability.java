package com.amynna.hololib.holo.memory.player;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.memory.HoloContainers;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Capability {@link HoloMemoryBank} attachee aux joueurs cote serveur, sauvegardee avec les donnees du joueur. Avant
 * chaque sauvegarde, l'etat vivant du {@link PlayerHoloContainer} est recopie dans la memoire active.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID)
public final class PlayerMemoryCapability
{
    public static final Capability<HoloMemoryBank> BANK = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, "player_memory");

    private PlayerMemoryCapability() {}

    @SubscribeEvent
    public static void onAttach(AttachCapabilitiesEvent<Entity> event)
    {
        if (event.getObject() instanceof Player player && !player.level().isClientSide())
        {
            event.addCapability(ID, new Provider(player));
        }
    }

    /** Respawn ou retour de l'End : la nouvelle entite reprend la banque de l'ancienne. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event)
    {
        Player original = event.getOriginal();
        original.reviveCaps();
        try {
            original.getCapability(BANK).ifPresent(old -> event.getEntity().getCapability(BANK).ifPresent(bank -> {
                HoloContainers.saveLivePlayer(original.getUUID(), old);
                bank.load(old.save());
            }));
        } finally {
            original.invalidateCaps();
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag>
    {
        private final Player player;
        private final HoloMemoryBank bank = new HoloMemoryBank();
        private final LazyOptional<HoloMemoryBank> optional = LazyOptional.of(() -> bank);

        Provider(Player player)
        {
            this.player = player;
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side)
        {
            return BANK.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT()
        {
            HoloContainers.saveLivePlayer(player.getUUID(), bank);
            return bank.save();
        }

        @Override
        public void deserializeNBT(CompoundTag tag)
        {
            bank.load(tag);
        }
    }
}
