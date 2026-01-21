package com.saloeater.translations_extractor.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.saloeater.translations_extractor.module.ModuleManager;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class ModuleArgument implements ArgumentType<String> {
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_MODULE = new DynamicCommandExceptionType(
        (module) -> Component.translatable("translations_extractor.extract.translations.unknown_module", module)
    );

    private final ModuleManager moduleManager;

    private ModuleArgument(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public static ModuleArgument module(ModuleManager moduleManager) {
        return new ModuleArgument(moduleManager);
    }

    public static String getModule(CommandContext<?> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String moduleName = reader.readUnquotedString();
        if (!moduleManager.getModuleNames().contains(moduleName)) {
            throw ERROR_UNKNOWN_MODULE.create(moduleName);
        }
        return moduleName;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(moduleManager.getModuleNames(), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return moduleManager.getModuleNames();
    }
}
