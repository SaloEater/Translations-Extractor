package com.saloeater.translations_extractor.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.saloeater.translations_extractor.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import vazkii.patchouli.api.PatchouliAPI;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.minecraft.util.datafix.fixes.BlockEntitySignTextStrictJsonFix.GSON;

public class PatchouliModule implements Module {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public boolean execute(Path resourcePackPath) {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        var langFrom = Config.CLIENT.sourceLanguage.get();
        var langTo = Config.CLIENT.targetLanguage.get();
        var hideExisting = Config.CLIENT.hideExistingTranslations.get();
        var includeSource = Config.CLIENT.includeSourceLanguageFiles.get();
        var onlyNamespaces = Config.CLIENT.only.get();
        var skipNamespaces = Config.CLIENT.skip.get();

        Map<ResourceLocation, List<InputStream>> langsFrom = new HashMap<>();
        Map<ResourceLocation, List<InputStream>> langsTo = new HashMap<>();

        AtomicBoolean folderCreated = new AtomicBoolean(false);

        resourceManager.listPacks().forEach(pack -> {
            var namespaces = pack.getNamespaces(PackType.CLIENT_RESOURCES);
            namespaces.forEach(namespace ->
                    pack.listResources(PackType.CLIENT_RESOURCES, namespace, "patchouli_books", (resourceLocation, ioSupplier) -> {
                        if (resourceLocation.getPath().endsWith(langFrom + ".json")) {
                            try {
                                if (!langsFrom.containsKey(resourceLocation)) {
                                    langsFrom.put(resourceLocation, new ArrayList<>());
                                }
                                langsFrom.get(resourceLocation).add(ioSupplier.get());
                            } catch (IOException e) {
                                LOGGER.error("Error reading structure from pack: {}", resourceLocation, e);
                            }
                        } else if (resourceLocation.getPath().endsWith(langTo + ".json")) {
                            try {
                                if (!langsTo.containsKey(resourceLocation)) {
                                    langsTo.put(resourceLocation, new ArrayList<>());
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

        squashedFrom.forEach((namespace, fromMap) -> {
            if (!onlyNamespaces.isEmpty() && !onlyNamespaces.contains(namespace)) {
                return;
            }
            if (skipNamespaces.contains(namespace)) {
                return;
            }

            Map<String, String> toMap = squashedTo.get(namespace);
            Map<String, String> missingMap = new TreeMap<>();
            Map<String, String> existingMap = new TreeMap<>();
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
                    Map<String, String> finalMap = new LinkedHashMap<>(missingMap);
                    if (!hideExisting) {
                        finalMap.putAll(existingMap);
                    }

                    GSON.newBuilder().setPrettyPrinting().create().toJson(finalMap, writer);
                    folderCreated.set(true);
                }
                LOGGER.info("Wrote merged translations to: {}", outputPath);

                if (includeSource) {
                    String sourceFileName = langFrom + ".json";
                    Path sourceOutputPath = resourcePackPath.resolve("assets").resolve(namespace).resolve("lang").resolve(sourceFileName);
                    try (var writer = Files.newBufferedWriter(sourceOutputPath, StandardCharsets.UTF_8)) {
                        GSON.newBuilder().setPrettyPrinting().create().toJson(fromMap, writer);
                        folderCreated.set(true);
                    }
                    LOGGER.info("Wrote source translations to: {}", sourceOutputPath);
                }
            } catch (IOException e) {
                LOGGER.error("Error writing translations for {}", namespace, e);
            }
        });

        return folderCreated.get();
    }

    private static @NotNull Map<String, Map<String, String>> squashLanguageMap(Map<ResourceLocation, List<InputStream>> langsFrom) {
        Map<String, Map<String, String>> squashedFrom = new HashMap<>();
        langsFrom.forEach((resourceLocation, streams) -> {
            streams.forEach(stream -> {
                try (stream) {
                    var jsonobject = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
                    if (jsonobject == null) {
                        return;
                    }
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
}
