package com.amynna.hololib.client.modules.dimension.space;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.client.holo.ClientHoloWorld;
import com.amynna.hololib.client.holo.HoloModes;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.UUID;

/**
 * Backend client du module {@code hololib:space_environment} ({@link SpaceEnvironmentModule.Backend}) : miroir de la
 * dimension du joueur local, temps de jeu du niveau client, joueur local ; sons du vaisseau
 * ({@link SpaceSoundController}, T36). Installe au setup client. Fil client.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpaceEnvironmentClient implements SpaceEnvironmentModule.Backend
{
    private SpaceEnvironmentClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> SpaceEnvironmentModule.install(new SpaceEnvironmentClient()));
    }

    @Override
    public SpaceEnvironmentModule clientInstance()
    {
        return HoloModes.find(ClientHoloWorld.find(HoloScope.DIMENSION), SpaceEnvironmentModule.class);
    }

    @Override
    public long gameTime()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0L : mc.level.getGameTime();
    }

    @Override
    public UUID localPlayer()
    {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getUUID();
    }

    /** Sons du vaisseau (T36) : {@link SpaceSoundController}. */
    @Override
    public void tickSounds(SpaceEnvironmentModule environment) { SpaceSoundController.tick(environment); }

    @Override
    public void released(SpaceEnvironmentModule environment) { SpaceSoundController.release(environment); }
}
