package com.amynna.hololib.client.render.levelview;

import com.amynna.hololib.HoloLib;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Commande client de diagnostic {@code /hololib_views pause|resume} : fige toutes les vues secondaires (derniere image
 * gardee) ou les relance, pour isoler leur effet (performances, rendu). Les mesures sont dans l'ecran F3.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT)
public final class LevelViewCommand
{
    private LevelViewCommand() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("hololib_views")
                .then(Commands.literal("pause").executes(ctx -> {
                    HoloLevelViews.setPaused(true);
                    ctx.getSource().sendSuccess(() -> Component.literal("Vues secondaires figees"), false);
                    return 1;
                }))
                .then(Commands.literal("resume").executes(ctx -> {
                    HoloLevelViews.setPaused(false);
                    ctx.getSource().sendSuccess(() -> Component.literal("Vues secondaires relancees"), false);
                    return 1;
                })));
    }
}
