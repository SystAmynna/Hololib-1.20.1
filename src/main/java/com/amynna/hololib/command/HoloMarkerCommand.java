package com.amynna.hololib.command;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.position.HoloMarkers;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /hololib_marker} (operateurs) : gestion des markers du mod (entites {@code minecraft:marker} portant le tag
 * {@code hololib} et leur id comme nom), qui portent les memoires de position.
 * <ul>
 *     <li>{@code create <id> [<x> <y> <z>]} : cree un marker a la position donnee, ou a celle de l'executant.</li>
 *     <li>{@code remove <id>} : supprime le marker et sa memoire.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID)
public final class HoloMarkerCommand
{
    private HoloMarkerCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("hololib_marker").requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> create(ctx, ctx.getSource().getPosition()))
                                .then(Commands.argument("position", Vec3Argument.vec3())
                                        .executes(ctx -> create(ctx, Vec3Argument.getVec3(ctx, "position"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        markerIds(ctx.getSource()), builder))
                                .executes(HoloMarkerCommand::remove))));
    }

    private static int create(CommandContext<CommandSourceStack> ctx, Vec3 position)
    {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        if (!HoloMarkers.validId(id)) {
            source.sendFailure(Component.literal("Id invalide (minuscules, chiffres, _ . -) : " + id));
            return 0;
        }
        if (HoloContainers.createMarker(source.getLevel(), id, position) == null) {
            source.sendFailure(Component.literal("Id deja utilise : " + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format("Marker '%s' cree en %.1f %.1f %.1f", id,
                position.x, position.y, position.z)), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx)
    {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "id");
        if (!HoloContainers.removeMarker(source.getServer(), id)) {
            source.sendFailure(Component.literal("Marker inconnu : " + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Marker '" + id + "' supprime"), true);
        return 1;
    }

    /** Identifiants des markers enregistres (charges ou non). */
    static List<String> markerIds(CommandSourceStack source)
    {
        List<String> ids = new ArrayList<>();
        for (HoloMarkers.Record record : HoloMarkers.of(source.getServer()).records()) ids.add(record.id());
        return ids;
    }
}
