package com.amynna.hololib.command;

import com.amynna.hololib.holo.module.HoloModuleType;
import com.amynna.hololib.holo.module.HoloModuleTypes;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Argument {@code <module...>} : un ou plusieurs identifiants de modules separes par des espaces (dernier argument de
 * la commande). L'autocompletion porte sur le dernier mot, sans reproposer ceux deja ecrits : modules compatibles avec
 * la memoire visee absents pour un ajout, presents pour un retrait, ayant une trace (installes ou sauvegarde dormante)
 * pour une remise a zero.
 */
final class ModuleListArgument
{
    private static final String NAME = "modules";

    /** Operation appliquee a chaque module de la liste. */
    enum Mode
    {
        ADD("ajoute(s)"),
        REMOVE("retire(s)"),
        RESET("remis a zero");

        private final String verb;

        Mode(String verb) { this.verb = verb; }
    }

    /** Resout la memoire visee a partir des arguments precedents. */
    @FunctionalInterface
    interface SlotResolver
    {
        ModuleSlot resolve(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    private ModuleListArgument() {}

    static RequiredArgumentBuilder<CommandSourceStack, String> argument(Mode mode, SlotResolver resolver)
    {
        return Commands.argument(NAME, StringArgumentType.greedyString())
                .suggests((ctx, builder) -> suggest(ctx, builder, mode, resolver))
                .executes(ctx -> {
                    Set<ResourceLocation> ids = new LinkedHashSet<>();
                    for (String word : StringArgumentType.getString(ctx, NAME).trim().split("\\s+")) {
                        ResourceLocation id = ResourceLocation.tryParse(word);
                        if (id == null) {
                            ctx.getSource().sendFailure(Component.literal("Identifiant invalide : " + word));
                        } else {
                            ids.add(id);
                        }
                    }
                    return execute(ctx.getSource(), mode, resolver.resolve(ctx), ids);
                });
    }

    /** Applique l'operation a chacun des {@code ids} et rend compte ; nombre de modules traites. */
    static int execute(CommandSourceStack source, Mode mode, ModuleSlot slot, Collection<ResourceLocation> ids)
    {
        // Le joueur qui installe devient proprietaire du module pour l'edition (la console : personne).
        UUID creator = source.getPlayer() != null ? source.getPlayer().getUUID() : null;
        int done = 0;
        for (ResourceLocation id : ids) {
            String error = switch (mode) {
                case ADD -> add(slot, id, creator);
                case REMOVE -> slot.remove(id) ? null : "absent";
                case RESET -> reset(slot, id);
            };
            if (error != null) {
                source.sendFailure(Component.literal(id + " : " + error));
            } else {
                done++;
            }
        }
        int count = done;
        source.sendSuccess(() -> Component.literal(count + " module(s) " + mode.verb), true);
        return count;
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> ctx,
                                                          SuggestionsBuilder builder, Mode mode,
                                                          SlotResolver resolver)
    {
        ModuleSlot slot;
        try {
            slot = resolver.resolve(ctx);
        } catch (CommandSyntaxException e) {
            return Suggestions.empty();
        }
        String remaining = builder.getRemaining();
        int lastWord = remaining.lastIndexOf(' ') + 1;
        Set<ResourceLocation> typed = parse(remaining.substring(0, lastWord));
        List<ResourceLocation> candidates = new ArrayList<>();
        if (mode == Mode.RESET) {
            for (ResourceLocation id : slot.resettable()) if (!typed.contains(id)) candidates.add(id);
        } else {
            Set<ResourceLocation> present = slot.present();
            for (Map.Entry<ResourceKey<HoloModuleType<?>>, HoloModuleType<?>> entry
                    : HoloModuleTypes.registry().getEntries()) {
                ResourceLocation id = entry.getKey().location();
                if (entry.getValue().target() != slot.target() || typed.contains(id)) continue;
                if ((mode == Mode.ADD) != present.contains(id)) candidates.add(id);
            }
        }
        SuggestionsBuilder word = builder.createOffset(builder.getStart() + lastWord);
        return SharedSuggestionProvider.suggestResource(candidates, word);
    }

    /** @return message d'erreur, ou {@code null} si le module a ete ajoute */
    private static String add(ModuleSlot slot, ResourceLocation id, UUID creator)
    {
        HoloModuleType<?> type = HoloModuleTypes.get(id);
        if (type == null) return "module inconnu";
        if (type.target() != slot.target()) return "module de type " + type.target() + ", attendu " + slot.target();
        return slot.add(type, creator) ? null : "deja present";
    }

    /** @return message d'erreur, ou {@code null} si la sauvegarde du module a ete remise a zero */
    private static String reset(ModuleSlot slot, ResourceLocation id)
    {
        if (slot.unknown().contains(id)) return "type inconnu, conserve tel quel";
        return slot.reset(id) ? null : "aucune sauvegarde dans cette memoire";
    }

    private static Set<ResourceLocation> parse(String words)
    {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (String word : words.trim().split("\\s+")) {
            ResourceLocation id = ResourceLocation.tryParse(word);
            if (!word.isEmpty() && id != null) ids.add(id);
        }
        return ids;
    }
}
