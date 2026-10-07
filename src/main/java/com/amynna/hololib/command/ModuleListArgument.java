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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Argument {@code <module...>} : un ou plusieurs identifiants de modules separes par des espaces (dernier argument de
 * la commande). L'autocompletion porte sur le dernier mot : modules compatibles avec la memoire visee, absents pour un
 * ajout, presents pour un retrait, sans reproposer ceux deja ecrits.
 */
final class ModuleListArgument
{
    private static final String NAME = "modules";

    /** Resout la memoire visee a partir des arguments precedents. */
    @FunctionalInterface
    interface SlotResolver
    {
        ModuleSlot resolve(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    private ModuleListArgument() {}

    static RequiredArgumentBuilder<CommandSourceStack, String> argument(boolean add, SlotResolver resolver)
    {
        return Commands.argument(NAME, StringArgumentType.greedyString())
                .suggests((ctx, builder) -> suggest(ctx, builder, add, resolver))
                .executes(ctx -> execute(ctx, add, resolver.resolve(ctx)));
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> ctx,
                                                          SuggestionsBuilder builder, boolean add,
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
        Set<ResourceLocation> present = slot.present();
        List<ResourceLocation> candidates = new ArrayList<>();
        for (Map.Entry<ResourceKey<HoloModuleType<?>>, HoloModuleType<?>> entry
                : HoloModuleTypes.registry().getEntries()) {
            ResourceLocation id = entry.getKey().location();
            if (entry.getValue().target() != slot.target() || typed.contains(id)) continue;
            if (add != present.contains(id)) candidates.add(id);
        }
        SuggestionsBuilder word = builder.createOffset(builder.getStart() + lastWord);
        return SharedSuggestionProvider.suggestResource(candidates, word);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, boolean add, ModuleSlot slot)
    {
        CommandSourceStack source = ctx.getSource();
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (String word : StringArgumentType.getString(ctx, NAME).trim().split("\\s+")) {
            ResourceLocation id = ResourceLocation.tryParse(word);
            if (id == null) {
                source.sendFailure(Component.literal("Identifiant invalide : " + word));
            } else {
                ids.add(id);
            }
        }
        // Le joueur qui installe devient proprietaire du module pour l'edition (la console : personne).
        UUID creator = source.getPlayer() != null ? source.getPlayer().getUUID() : null;
        int done = 0;
        for (ResourceLocation id : ids) {
            String error = add ? add(slot, id, creator) : remove(slot, id);
            if (error != null) {
                source.sendFailure(Component.literal(id + " : " + error));
            } else {
                done++;
            }
        }
        int count = done;
        String verb = add ? "ajoute(s)" : "retire(s)";
        source.sendSuccess(() -> Component.literal(count + " module(s) " + verb), true);
        return count;
    }

    /** @return message d'erreur, ou {@code null} si le module a ete ajoute */
    private static String add(ModuleSlot slot, ResourceLocation id, UUID creator)
    {
        HoloModuleType<?> type = HoloModuleTypes.get(id);
        if (type == null) return "module inconnu";
        if (type.target() != slot.target()) return "module de type " + type.target() + ", attendu " + slot.target();
        return slot.add(type, creator) ? null : "deja present";
    }

    /** @return message d'erreur, ou {@code null} si le module a ete retire */
    private static String remove(ModuleSlot slot, ResourceLocation id)
    {
        return slot.remove(id) ? null : "absent";
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
