package com.amynna.hololib.command;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.HoloMemory;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.memory.dimension.DimensionHoloContainer;
import com.amynna.hololib.holo.memory.player.HoloMemoryBank;
import com.amynna.hololib.holo.memory.player.PlayerHoloContainer;
import com.amynna.hololib.holo.memory.position.HoloMarkers;
import com.amynna.hololib.holo.module.HoloModuleTarget;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /hololib_module <add|remove>} (operateurs) : ajoute ou retire des modules d'une memoire.
 * <ul>
 *     <li>{@code dimension <here|dim> <module...>} : memoire d'une dimension ; {@code here} = celle de l'executant
 *     (l'overworld depuis la console).</li>
 *     <li>{@code world <marker> <module...>} : memoire de position du marker (chargee ou non).</li>
 *     <li>{@code player <joueur> <current|memoire> <module...>} : une memoire d'un joueur connecte.</li>
 * </ul>
 * Seuls les modules de la compatibilite correspondante sont acceptes et proposes.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID)
public final class HoloModuleCommand
{
    /** Nom reserve designant la memoire active du joueur. */
    private static final String CURRENT = "current";

    private static final DynamicCommandExceptionType UNKNOWN_MARKER =
            new DynamicCommandExceptionType(id -> Component.literal("Marker inconnu : " + id));
    private static final DynamicCommandExceptionType UNKNOWN_MEMORY =
            new DynamicCommandExceptionType(name -> Component.literal("Memoire inconnue : " + name));
    private static final DynamicCommandExceptionType UNAVAILABLE =
            new DynamicCommandExceptionType(what -> Component.literal("Memoire indisponible : " + what));

    private HoloModuleCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("hololib_module").requires(source -> source.hasPermission(2))
                .then(action("add", true))
                .then(action("remove", false)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> action(String name, boolean add)
    {
        return Commands.literal(name)
                .then(Commands.literal("dimension")
                        .then(Commands.literal("here")
                                .then(ModuleListArgument.argument(add,
                                        ctx -> dimension(ctx.getSource().getLevel()))))
                        .then(Commands.argument("dim", DimensionArgument.dimension())
                                .then(ModuleListArgument.argument(add,
                                        ctx -> dimension(DimensionArgument.getDimension(ctx, "dim"))))))
                .then(Commands.literal("world")
                        .then(Commands.argument("marker", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        HoloMarkerCommand.markerIds(ctx.getSource()), builder))
                                .then(ModuleListArgument.argument(add, HoloModuleCommand::position))))
                .then(Commands.literal("player")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("memory", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                memoryNames(ctx), builder))
                                        .then(ModuleListArgument.argument(add, HoloModuleCommand::player)))));
    }

    // ---------------------------------------------------------------- resolution des memoires

    private static ModuleSlot dimension(ServerLevel level) throws CommandSyntaxException
    {
        DimensionHoloContainer container = HoloContainers.dimension(level);
        if (container == null) throw UNAVAILABLE.create(level.dimension().location());
        return ModuleSlot.of(container);
    }

    private static ModuleSlot position(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        String id = StringArgumentType.getString(ctx, "marker");
        HoloMarkers markers = HoloMarkers.of(ctx.getSource().getServer());
        HoloMarkers.Record record = markers.get(id);
        if (record == null) throw UNKNOWN_MARKER.create(id);
        if (record.live() != null) return ModuleSlot.of(record.live());
        return ModuleSlot.offline(record.memory(), record.scope(), HoloModuleTarget.POSITION, markers::setDirty);
    }

    private static ModuleSlot player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String name = StringArgumentType.getString(ctx, "memory");
        HoloMemoryBank bank = HoloMemoryBank.of(player);
        if (name.equals(CURRENT) || name.equals(bank.active().name())) {
            PlayerHoloContainer container = HoloContainers.player(player);
            if (container == null) throw UNAVAILABLE.create(player.getGameProfile().getName());
            return ModuleSlot.of(container);
        }
        HoloMemory memory = bank.find(name);
        if (memory == null) throw UNKNOWN_MEMORY.create(name);
        // Memoire inactive : modifiee au repos, sauvegardee avec le joueur.
        return ModuleSlot.offline(memory, new HoloScope.Player(player.getUUID()), HoloModuleTarget.PLAYER, () -> {});
    }

    // ---------------------------------------------------------------- suggestions

    /** {@code current} en premier, puis les memoires du joueur. */
    private static List<String> memoryNames(CommandContext<CommandSourceStack> ctx)
    {
        List<String> names = new ArrayList<>();
        names.add(CURRENT);
        try {
            for (HoloMemory memory : HoloMemoryBank.of(EntityArgument.getPlayer(ctx, "player")).memories()) {
                names.add(memory.name());
            }
        } catch (CommandSyntaxException | IllegalStateException e) {
            // Joueur introuvable : seul "current" est propose.
        }
        return names;
    }
}
