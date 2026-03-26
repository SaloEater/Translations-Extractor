package com.saloeater.translations_extractor.module;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import com.saloeater.translations_extractor.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PatchouliModule implements Module {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String getName() {
        return "patchouli";
    }

    @Override
    public ModuleResult execute(Path resourcePackPath, boolean onlyMissing) {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        var langFrom = Config.CLIENT.sourceLanguage.get();
        var langTo = Config.CLIENT.targetLanguage.get();
        var onlyNamespaces = Config.CLIENT.only.get();
        var skipNamespaces = Config.CLIENT.skip.get();

        AtomicBoolean folderCreated = new AtomicBoolean(false);
        AtomicInteger filesProcessed = new AtomicInteger(0);
        Set<String> booksProcessed = new HashSet<>();

        String sourceLangPath = "/" + langFrom + "/";
        String targetLangPath = "/" + langTo + "/";

        // Collect all patchouli files: source and target
        Map<String, JsonElement> sourceFiles = new HashMap<>();
        Map<String, JsonElement> targetFiles = new HashMap<>();

        resourceManager.listPacks().forEach(pack -> {
            var namespaces = pack.getNamespaces(PackType.CLIENT_RESOURCES);
            namespaces.forEach(namespace -> {
                if (!onlyNamespaces.isEmpty() && !onlyNamespaces.contains(namespace)) {
                    return;
                }
                if (skipNamespaces.contains(namespace)) {
                    return;
                }

                pack.listResources(PackType.CLIENT_RESOURCES, namespace, "patchouli_books", (resourceLocation, ioSupplier) -> {
                    String path = resourceLocation.getPath();
                    String fullPath = namespace + ":" + path;

                    try (InputStream inputStream = ioSupplier.get()) {
                        JsonElement jsonElement = new GsonBuilder().create()
                                .fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonElement.class);

                        if (jsonElement == null) {
                            return;
                        }

                        if (path.contains(sourceLangPath)) {
                            // Normalize path to use as key (replace source lang with placeholder)
                            String normalizedPath = fullPath.replace(sourceLangPath, "/<LANG>/");
                            sourceFiles.put(normalizedPath, jsonElement);
                        } else if (path.contains(targetLangPath)) {
                            String normalizedPath = fullPath.replace(targetLangPath, "/<LANG>/");
                            targetFiles.put(normalizedPath, jsonElement);
                        }

                    } catch (JsonSyntaxException e) {
                        LOGGER.error("Malformed JSON in patchouli file: {}", resourceLocation, e);
                    } catch (IOException e) {
                        LOGGER.error("Error reading patchouli file: {}", resourceLocation, e);
                    }
                });
            });
        });

        // Process source files and merge with target if exists
        sourceFiles.forEach((normalizedPath, sourceJson) -> {
            JsonElement targetJson = targetFiles.get(normalizedPath);
            if (onlyMissing && targetJson != null) {
                return;
            }
            JsonElement mergedJson = mergeJson(sourceJson, targetJson);

            // Build output path
            String actualPath = normalizedPath.replace("/<LANG>/", targetLangPath);
            String[] parts = actualPath.split(":", 2);
            String namespace = parts[0];
            String filePath = parts[1];

            // Extract book name from path (patchouli_books/<book_name>/...)
            String[] pathParts = filePath.split("/");
            if (pathParts.length >= 2) {
                booksProcessed.add(namespace + ":" + pathParts[1]);
            }

            Path outputPath = resourcePackPath.resolve("assets").resolve(namespace).resolve(filePath);

            try {
                Files.createDirectories(outputPath.getParent());
                try (var writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                    new GsonBuilder().setPrettyPrinting().create().toJson(mergedJson, writer);
                    folderCreated.set(true);
                    filesProcessed.incrementAndGet();
                }
            } catch (IOException e) {
                LOGGER.error("Error writing patchouli file: {}", outputPath, e);
            }
        });

        Component summary = Component.translatable(
                "translations_extractor.module.patchouli.summary",
                booksProcessed.size(),
                filesProcessed.get()
        );

        return ModuleResult.of(folderCreated.get(), summary);
    }

    /**
     * Recursively merge source and target JSON.
     * - If both have property: use target's value
     * - If only source has property: use source's value
     * - If only target has property: skip it
     */
    private JsonElement mergeJson(JsonElement source, JsonElement target) {
        if (target == null) {
            return source.deepCopy();
        }

        if (source.isJsonObject() && target.isJsonObject()) {
            JsonObject sourceObj = source.getAsJsonObject();
            JsonObject targetObj = target.getAsJsonObject();
            JsonObject result = new JsonObject();

            // Only iterate over source keys
            for (String key : sourceObj.keySet()) {
                JsonElement sourceValue = sourceObj.get(key);
                JsonElement targetValue = targetObj.get(key);

                if (targetValue != null) {
                    // Both have the property - use target's value (recursively for objects)
                    result.add(key, mergeJson(sourceValue, targetValue));
                } else {
                    // Only source has it - use source's value
                    result.add(key, sourceValue.deepCopy());
                }
            }
            // Keys only in target are skipped

            return result;
        }

        if (source.isJsonArray() && target.isJsonArray()) {
            JsonArray sourceArr = source.getAsJsonArray();
            JsonArray targetArr = target.getAsJsonArray();
            JsonArray result = new JsonArray();

            // Merge arrays by index
            for (int i = 0; i < sourceArr.size(); i++) {
                if (i < targetArr.size()) {
                    result.add(mergeJson(sourceArr.get(i), targetArr.get(i)));
                } else {
                    result.add(sourceArr.get(i).deepCopy());
                }
            }

            return result;
        }

        // For primitives: if target exists, use target
        return target.deepCopy();
    }
}
