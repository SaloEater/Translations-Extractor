package com.saloeater.translations_extractor;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.saloeater.translations_extractor.command.ModuleArgument;
import com.saloeater.translations_extractor.config.Config;
import com.saloeater.translations_extractor.module.ModuleManager;
import com.saloeater.translations_extractor.module.ModuleResult;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Mod(TranslationsExtractor.MODID)
public class TranslationsExtractor
{
    public static final String MODID = "translations_extractor";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ModuleManager moduleManager = new ModuleManager();

    public TranslationsExtractor()
    {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        var forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.register(this);
    }

    @SubscribeEvent
    public void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("extract")
                .then(Commands.literal("translations")
                    .then(Commands.argument("module", ModuleArgument.module(moduleManager))
                        .executes(context -> {
                            String moduleName = ModuleArgument.getModule(context, "module");
                            return executeModule(context.getSource(), moduleName);
                        })
                    )
                )
        );
    }

    private int executeModule(net.minecraft.commands.CommandSourceStack source, String moduleName) {
        var packName = Config.CLIENT.resourcePackName.get();
        Path resourcePackPath = Minecraft.getInstance().getResourcePackDirectory().resolve(packName + "_" + moduleName);

        if (Files.exists(resourcePackPath)) {
            try {
                deleteDirectory(resourcePackPath);
            } catch (IOException e) {
                LOGGER.error("Failed to clear existing resource pack at: {}", resourcePackPath, e);
            }
        }

        ModuleResult result = moduleManager.execute(moduleName, resourcePackPath);

        if (result.isFilesCreated()) {
            writePackMcmeta(resourcePackPath);
            source.sendSuccess(
                () -> Component.translatable("translations_extractor.extract.translations.success", resourcePackPath),
                false
            );
            source.sendSuccess(result::getSummary, false);
        } else {
            source.sendSuccess(
                () -> Component.translatable("translations_extractor.extract.translations.no_data"),
                false
            );
        }

        return 1;
    }

    private void writePackMcmeta(Path resourcePackPath) {
        Path outputPath = resourcePackPath.resolve("pack.mcmeta");
        try (var writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            var GSON = (new GsonBuilder()).create();
            var mcmeta = new JsonObject();
            mcmeta.add("pack", GSON.toJsonTree(Map.of(
                "pack_format", 15,
                "description", "Extracted Translations"
            )));
            GSON.newBuilder().setPrettyPrinting().create().toJson(mcmeta, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteDirectory(entry);
                }
            }
        }
        Files.delete(path);
    }
}
