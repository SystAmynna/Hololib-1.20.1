package com.amynna.hololib.command;

import com.amynna.hololib.HoloLib;
import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.HoloMemories;
import com.amynna.hololib.holo.memory.HoloMemory;
import com.amynna.hololib.holo.memory.player.HoloMemoryBank;
import com.amynna.hololib.holo.memory.player.PlayerHoloContainer;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /holo_memory} (operateurs) : sauvegardes des modules d'une memoire et memoires des joueurs. Une memoire
 * contient les modules equipes et les sauvegardes qui lui sont liees, y compris celles de modules retires (dormantes,
 * relues a la reinstallation) ; une remise a zero efface la sauvegarde sans retirer le module.
 * <ul>
 *     <li>{@code dimension <here|dim> list | reset <module...>} : memoire d'une dimension ({@code here} = celle de
 *     l'executant, l'overworld depuis la console).</li>
 *     <li>{@code world <marker> list | reset <module...>} : memoire de position du marker (chargee ou non).</li>
 *     <li>{@code player <joueur> add <memoire> [copy <joueur> <current|memoire>]} : nouvelle memoire, vide ou copiee
 *     d'une memoire d'un joueur connecte.</li>
 *     <li>{@code player <joueur> remove <memoire>} : supprime une memoire inactive.</li>
 *     <li>{@code player <joueur> list} : memoires du joueur.</li>
 *     <li>{@code player <joueur> reset <current|memoire> [<module...>]} : sans module, tous ceux qui ont une
 *     sauvegarde.</li>
 * </ul>
 * L'autocompletion des modules ne propose que ceux dont la memoire garde une trace.
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID)
public final class HoloMemoryCommand
{
    private HoloMemoryCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("hololib_memory").requires(source -> source.hasPermission(2))
                .then(Commands.literal("dimension")
                        .then(slotActions(Commands.literal("here"),
                                ctx -> HoloModuleCommand.dimension(ctx.getSource().getLevel())))
                        .then(slotActions(Commands.argument("dim", DimensionArgument.dimension()),
                                ctx -> HoloModuleCommand.dimension(DimensionArgument.getDimension(ctx, "dim")))))
                .then(Commands.literal("world")
                        .then(slotActions(Commands.argument("marker", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        HoloMarkerCommand.markerIds(ctx.getSource()), builder)),
                                HoloModuleCommand::position)))
                .then(Commands.literal("player")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("add")
                                        .then(Commands.argument("memory", StringArgumentType.word())
                                                .executes(HoloMemoryCommand::add)
                                                .then(Commands.literal("copy")
                                                        .then(Commands.argument("source", EntityArgument.player())
                                                                .then(Commands.argument("sourceMemory",
                                                                                StringArgumentType.word())
                                                                        .suggests((ctx, builder) ->
                                                                                SharedSuggestionProvider.suggest(
                                                                                        HoloModuleCommand.memoryNames(
                                                                                                ctx, "source", true),
                                                                                        builder))
                                                                        .executes(HoloMemoryCommand::copy))))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("memory", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        HoloModuleCommand.memoryNames(ctx, "player", false),
                                                        builder))
                                                .executes(HoloMemoryCommand::remove)))
                                .then(Commands.literal("list").executes(HoloMemoryCommand::listMemories))
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("memory", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        HoloModuleCommand.memoryNames(ctx), builder))
                                                .executes(ctx -> resetAll(ctx.getSource(),
                                                        HoloModuleCommand.player(ctx)))
                                                .then(ModuleListArgument.argument(ModuleListArgument.Mode.RESET,
                                                        HoloModuleCommand::player)))))));
    }

    /** {@code list} et {@code reset <module...>} sous le noeud qui designe la memoire. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T slotActions(
            T node, ModuleListArgument.SlotResolver resolver)
    {
        return node
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), resolver.resolve(ctx))))
                .then(Commands.literal("reset")
                        .then(ModuleListArgument.argument(ModuleListArgument.Mode.RESET, resolver)));
    }

    // ---------------------------------------------------------------- modules d'une memoire

    private static int list(CommandSourceStack source, ModuleSlot slot)
    {
        Set<ResourceLocation> unknown = slot.unknown();
        Set<ResourceLocation> equipped = slot.present();
        equipped.removeAll(unknown);
        Set<ResourceLocation> stored = slot.stored();
        source.sendSuccess(() -> Component.literal("Modules equipes (" + equipped.size() + ") : "
                + join(equipped)), false);
        source.sendSuccess(() -> Component.literal("Sauvegardes de modules non equipes (" + stored.size() + ") : "
                + join(stored)), false);
        if (!unknown.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Types inconnus, conserves (" + unknown.size() + ") : "
                    + join(unknown)), false);
        }
        return equipped.size() + stored.size();
    }

    private static int resetAll(CommandSourceStack source, ModuleSlot slot)
    {
        Set<ResourceLocation> ids = slot.resettable();
        if (ids.isEmpty()) {
            source.sendFailure(Component.literal("Aucune sauvegarde de module dans cette memoire"));
            return 0;
        }
        return ModuleListArgument.execute(source, ModuleListArgument.Mode.RESET, slot, ids);
    }

    // ---------------------------------------------------------------- memoires de joueur

    private static int add(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String name = StringArgumentType.getString(ctx, "memory");
        if (HoloContainers.createMemory(player, name) == null) return nameRefused(source, player, name);
        source.sendSuccess(() -> Component.literal("Memoire '" + name + "' creee pour " + name(player)), true);
        return 1;
    }

    private static int copy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String name = StringArgumentType.getString(ctx, "memory");
        ServerPlayer from = EntityArgument.getPlayer(ctx, "source");
        String fromName = StringArgumentType.getString(ctx, "sourceMemory");
        HoloMemoryBank fromBank = HoloMemoryBank.of(from);
        if (fromName.equals(HoloModuleCommand.CURRENT)) fromName = fromBank.active().name();
        if (fromBank.find(fromName) == null) throw HoloModuleCommand.UNKNOWN_MEMORY.create(fromName);
        if (HoloContainers.copyMemory(player, name, from, fromName) == null) return nameRefused(source, player, name);
        String copied = fromName;
        source.sendSuccess(() -> Component.literal("Memoire '" + name + "' creee pour " + name(player)
                + ", copiee de '" + copied + "' de " + name(from)), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String name = StringArgumentType.getString(ctx, "memory");
        HoloMemoryBank bank = HoloMemoryBank.of(player);
        HoloMemory memory = bank.find(name);
        if (memory == null) throw HoloModuleCommand.UNKNOWN_MEMORY.create(name);
        if (memory == bank.active() || !HoloContainers.deleteMemory(player, name)) {
            source.sendFailure(Component.literal("Memoire active de " + name(player) + " : en changer d'abord"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Memoire '" + name + "' de " + name(player) + " supprimee"), true);
        return 1;
    }

    private static int listMemories(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        HoloMemoryBank bank = HoloMemoryBank.of(player);
        PlayerHoloContainer live = HoloContainers.player(player);
        source.sendSuccess(() -> Component.literal("Memoires de " + name(player) + " (" + bank.memories().size()
                + ") :"), false);
        for (HoloMemory memory : bank.memories()) {
            boolean active = memory == bank.active();
            // Memoire active : l'etat vivant fait foi (la memoire n'est recopiee qu'a la sauvegarde).
            int modules = active && live != null ? live.types().size() + live.unknownTypes().size()
                    : memory.entries().size();
            int stored = active && live != null ? live.storedTypes().size() : HoloMemories.storedTypes(memory).size();
            source.sendSuccess(() -> Component.literal("- " + memory.name() + (active ? " (active)" : "") + " : "
                    + modules + " module(s) equipe(s), " + stored + " sauvegarde(s) non equipee(s)"), false);
        }
        return bank.memories().size();
    }

    private static int nameRefused(CommandSourceStack source, ServerPlayer player, String name)
    {
        source.sendFailure(Component.literal(HoloMemoryBank.of(player).find(name) != null
                ? "Memoire deja existante : " + name
                : "Nom invalide (1 a " + HoloMemoryBank.MAX_NAME_LENGTH + " caracteres) : " + name));
        return 0;
    }

    private static String name(ServerPlayer player) { return player.getGameProfile().getName(); }

    private static String join(Collection<ResourceLocation> ids)
    {
        return ids.isEmpty() ? "aucun" : ids.stream().map(ResourceLocation::toString).collect(Collectors.joining(", "));
    }
}
