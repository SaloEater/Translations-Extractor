package com.saloeater.translations_extractor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.saloeater.translations_extractor.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
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
            Commands.literal("extract").then(Commands.literal("translations")).executes(context -> {
                ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
                var langFrom = Config.CLIENT.sourceLanguage.get();
                var langTo = Config.CLIENT.targetLanguage.get();
                var hideExisting = Config.CLIENT.hideExistingTranslations.get();
                var includeSource = Config.CLIENT.includeSourceLanguageFiles.get();
                var packName = Config.CLIENT.resourcePackName.get();
                var onlyNamespaces = Config.CLIENT.only.get();
                var skipNamespaces = Config.CLIENT.skip.get();
                Map<ResourceLocation, List<InputStream>> langsFrom = new HashMap<>();
                Map<ResourceLocation, List<InputStream>> langsTo = new HashMap<>();
                resourceManager.listPacks().forEach(pack -> {
                    var namespaces = pack.getNamespaces(PackType.CLIENT_RESOURCES);
                    namespaces.forEach(namespace ->
                        pack.listResources(PackType.CLIENT_RESOURCES, namespace, "lang", (resourceLocation, ioSupplier) -> {
                            if (resourceLocation.getPath().endsWith(langFrom + ".json")) {
                                try {
                                    if (!langsFrom.containsKey(resourceLocation)) {
                                        langsFrom.put(resourceLocation, new java.util.ArrayList<>());
                                    }
                                    langsFrom.get(resourceLocation).add(ioSupplier.get());
                                } catch (IOException e) {
                                    LOGGER.error("Error reading structure from pack: {}", resourceLocation, e);
                                }
                            } else if (resourceLocation.getPath().endsWith(langTo + ".json")) {
                                try {
                                    if (!langsTo.containsKey(resourceLocation)) {
                                        langsTo.put(resourceLocation, new java.util.ArrayList<>());
                                    }
                                    langsTo.get(resourceLocation).add(ioSupplier.get());
                                } catch (IOException e) {
                                    LOGGER.error("Error reading structure from pack: {}", resourceLocation, e);
                                }
                            }
                        })
                    );
                });

                Map<String, Map<String, String>> squashedFrom = squashLanguageMap(langsFrom);
                Map<String, Map<String, String>> squashedTo = squashLanguageMap(langsTo);

                Path resourcePackPath = Minecraft.getInstance().getResourcePackDirectory().resolve(packName);

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

                squashedFrom.forEach((namespace, fromMap) -> {
                    if (!onlyNamespaces.isEmpty() && !onlyNamespaces.contains(namespace)) {
                        return;
                    }
                    if (skipNamespaces.contains(namespace)) {
                        return;
                    }

                    Map<String, String> toMap = squashedTo.get(namespace);
                    Map<String, String> missingMap = new HashMap<>();
                    Map<String, String> existingMap = new HashMap<>();
                    fromMap.forEach((key, fromValue) -> {
                        if (toMap != null && toMap.containsKey(key)) {
                            existingMap.put(key, toMap.get(key));
                        } else {
                            missingMap.put(key, fromValue);
                        }
                    });

                    if (missingMap.isEmpty() && hideExisting) {
                        return;
                    }

                    String fileName = langTo + ".json";
                    Path outputPath = resourcePackPath.resolve("assets").resolve(namespace).resolve("lang").resolve(fileName);

                    try {
                        Files.createDirectories(outputPath.getParent());
                        try (var writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                            HashMap<String, String> finalMap = new HashMap<>();
                            finalMap.putAll(missingMap);
                            if (!hideExisting) {
                                finalMap.putAll(existingMap);
                            }
                            GSON.newBuilder().setPrettyPrinting().create().toJson(finalMap, writer);
                        }
                        LOGGER.info("Wrote merged translations to: {}", outputPath);

                        if (includeSource) {
                            String sourceFileName = langFrom + ".json";
                            Path sourceOutputPath = resourcePackPath.resolve("assets").resolve(namespace).resolve("lang").resolve(sourceFileName);
                            try (var writer = Files.newBufferedWriter(sourceOutputPath, StandardCharsets.UTF_8)) {
                                GSON.newBuilder().setPrettyPrinting().create().toJson(fromMap, writer);
                            }
                            LOGGER.info("Wrote source translations to: {}", sourceOutputPath);
                        }
                    } catch (IOException e) {
                        LOGGER.error("Error writing translations for {}", namespace, e);
                    }
                });

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


                return 1;
            }).build().createBuilder()
        );
    }

    private static @NotNull Map<String, Map<String, String>> squashLanguageMap(Map<ResourceLocation, List<InputStream>> langsFrom) {
        Map<String, Map<String, String>> squashedFrom = new HashMap<>();
        langsFrom.forEach((resourceLocation, streams) -> {
            streams.forEach(stream -> {
                try (stream) {
                    var jsonobject = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
                    for (Map.Entry<String, JsonElement> entry : jsonobject.entrySet()) {
                        String namespace = resourceLocation.getNamespace();
                        if (!squashedFrom.containsKey(namespace)) {
                            squashedFrom.put(namespace, new HashMap<>());
                        }
                        String value = entry.getValue().getAsString();
                        String key = entry.getKey();
                        squashedFrom.get(namespace).put(key, value);
                    }

                } catch (IOException e) {
                    LOGGER.error("Error reading language from pack: {}", resourceLocation, e);
                }
            });
        });
        return squashedFrom;
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
