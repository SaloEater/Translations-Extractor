package com.saloeater.translations_extractor;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.saloeater.translations_extractor.config.Config;
import com.saloeater.translations_extractor.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
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

import static net.minecraft.util.datafix.fixes.BlockEntitySignTextStrictJsonFix.GSON;

@Mod(TranslationsExtractor.MODID)
public class TranslationsExtractor
{
    public static final String MODID = "translations_extractor";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TranslationsExtractor()
    {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        var forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.register(this);
    }

    @SubscribeEvent
    public void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("extract").then(Commands.literal("translations").executes(context -> {
                var packName = Config.CLIENT.resourcePackName.get();
                Path resourcePackPath = Minecraft.getInstance().getResourcePackDirectory().resolve(packName);

                backupExistingPack(resourcePackPath, packName);

                ModuleManager moduleManager = new ModuleManager();
                var folderCreated = moduleManager.executeAll(resourcePackPath);

                if (folderCreated) {
                    writePackMcmeta(resourcePackPath);
                    context.getSource().sendSuccess(
                            () -> net.minecraft.network.chat.Component.translatable("translations_extractor.extract.translations.success", resourcePackPath),
                            false
                    );
                } else {
                    context.getSource().sendSuccess(
                            () -> net.minecraft.network.chat.Component.translatable("translations_extractor.extract.translations.no_data"),
                            false
                    );
                }



                return 1;
            }).build().createBuilder()
        ));
    }

    private void backupExistingPack(Path resourcePackPath, String packName) {
        if (Files.exists(resourcePackPath)) {
            Path backupPath = resourcePackPath.resolveSibling(packName + "_BACKUP");
            try {
                if (Files.exists(backupPath)) {
                    deleteDirectory(backupPath);
                }
                Files.move(resourcePackPath, backupPath);
                LOGGER.info("Backed up existing resource pack to: {}", backupPath);
            } catch (IOException e) {
                LOGGER.error("Failed to backup existing resource pack", e);
            }
        }
    }

    private void writePackMcmeta(Path resourcePackPath) {
        Path outputPath = resourcePackPath.resolve("pack.mcmeta");
        try (var writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
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
