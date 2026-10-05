package com.amynna.hololib.command;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.inspect.HoloInspections;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.List;

/**
 * Acces d'inspection entre joueurs ({@link HoloInspections}), enregistres dans la memoire active de celui qui voit.
 * <ul>
 *     <li>{@code /hololib_inspect <on|off>} : l'executant voit (ou ne voit plus) tous les joueurs.</li>
 *     <li>{@code /hololib_inspect <on|off> <joueurs>} : l'executant voit (ou ne voit plus) ces joueurs.</li>
 *     <li>{@code /hololib_inspect <on|off> <joueurs> <cibles>} : chacun des {@code joueurs} voit (ou ne voit plus)
 *     chacune des {@code cibles}.</li>
 * </ul>
 * Ouverte a tous ; chaque activation passe par {@code HoloInspectEvent}, qui peut la refuser.
 * {@code /hololib_force_inspect} a la meme syntaxe, reservee aux operateurs, et ignore cette politique.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID)
public final class HoloInspectCommand
{
    private HoloInspectCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(command("hololib_inspect", false));
        dispatcher.register(command("hololib_force_inspect", true));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name, boolean force)
    {
        return Commands.literal(name).requires(source -> source.hasPermission(force ? 2 : 0))
                .then(toggle("on", true, force))
                .then(toggle("off", false, force));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toggle(String name, boolean enable, boolean force)
    {
        return Commands.literal(name)
                .executes(ctx -> apply(ctx, List.of(ctx.getSource().getPlayerOrException()), null, enable, force))
                .then(Commands.argument("joueurs", EntityArgument.players())
                        .executes(ctx -> apply(ctx, List.of(ctx.getSource().getPlayerOrException()),
                                EntityArgument.getPlayers(ctx, "joueurs"), enable, force))
                        .then(Commands.argument("cibles", EntityArgument.players())
                                .executes(ctx -> apply(ctx, EntityArgument.getPlayers(ctx, "joueurs"),
                                        EntityArgument.getPlayers(ctx, "cibles"), enable, force))));
    }

    /**
     * @param targets {@code null} = tous les joueurs
     * @return nombre d'acces modifies
     */
    private static int apply(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> viewers,
                             Collection<ServerPlayer> targets, boolean enable, boolean force)
    {
        CommandSourceStack source = ctx.getSource();
        int changed = 0;
        int pairs = 0;
        for (ServerPlayer viewer : viewers) {
            if (targets == null) {
                pairs++;
                if (report(source, viewer, null, enable, force)) changed++;
                continue;
            }
            for (ServerPlayer target : targets) {
                if (target == viewer) continue;
                pairs++;
                if (report(source, viewer, target, enable, force)) changed++;
            }
        }
        if (pairs == 0) source.sendFailure(Component.literal("Un joueur ne peut pas s'inspecter lui-meme"));
        return changed;
    }

    /** Applique une demande et en rend compte ; vrai si l'acces a change. */
    private static boolean report(CommandSourceStack source, ServerPlayer viewer, ServerPlayer target, boolean enable,
                                  boolean force)
    {
        HoloInspections.Result result = HoloInspections.request(viewer, target, enable, force, source.getEntity());
        String who = viewer.getGameProfile().getName();
        String what = target == null ? "tous les joueurs" : target.getGameProfile().getName();
        switch (result) {
            case CHANGED -> source.sendSuccess(() -> Component.literal(enable
                    ? who + " voit maintenant les affichages de " + what
                    : who + " ne voit plus les affichages de " + what), force);
            case UNCHANGED -> source.sendSuccess(() -> Component.literal(enable
                    ? who + " voyait deja les affichages de " + what
                    : who + " ne voyait pas les affichages de " + what), false);
            case DENIED -> source.sendFailure(Component.literal("Acces refuse : " + who + " -> " + what));
            case UNAVAILABLE -> source.sendFailure(Component.literal("Memoire indisponible : " + who));
        }
        return result == HoloInspections.Result.CHANGED;
    }
}
