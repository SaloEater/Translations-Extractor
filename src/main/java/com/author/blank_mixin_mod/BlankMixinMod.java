package com.author.blank_mixin_mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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

@Mod(BlankMixinMod.MODID)
public class BlankMixinMod
{
    public static final String MODID = "blank_mixin_mod";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BlankMixinMod()
    {
        var forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.register(this);
    }

    @SubscribeEvent
    public void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("extract").then(Commands.literal("translations")).executes(context -> {
                ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
                var langFrom = "en_us";
                var langTo = "ru_ru";
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

                Path resourcePackPath = Minecraft.getInstance().getResourcePackDirectory().resolve("ExtractedTranslations");
                squashedFrom.forEach((namespace, fromMap) -> {
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

                    String fileName = langTo + ".json";
                    Path outputPath = resourcePackPath.resolve("assets").resolve(namespace).resolve("lang").resolve(fileName);

                    try {
                        Files.createDirectories(outputPath.getParent());
                        try (var writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                            HashMap<String, String> finalMap = new HashMap<>();
                            finalMap.putAll(missingMap);
                            finalMap.putAll(existingMap);
                            GSON.newBuilder().setPrettyPrinting().create().toJson(finalMap, writer);
                        }
                        LOGGER.info("Wrote merged translations to: {}", outputPath);
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
}
