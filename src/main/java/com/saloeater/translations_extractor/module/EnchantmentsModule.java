package com.saloeater.translations_extractor.module;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EnchantmentsModule implements Module {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String getName() {
        return "enchantments";
    }

    @Override
    public ModuleResult execute(Path resourcePackPath, boolean onlyMissing) {
        final String lang = Minecraft.getInstance().options.languageCode;

        final List<ResourceLocation> sortedKeys = new ArrayList<>(BuiltInRegistries.ENCHANTMENT.keySet());
        sortedKeys.sort(Comparator.comparing(ResourceLocation::getNamespace)
                                  .thenComparing(ResourceLocation::getPath));

        final Path outputPath = resourcePackPath.resolve("assets/enchdesc/lang/" + lang + ".json");
        try {
            Files.createDirectories(outputPath.getParent());
        } catch (IOException e) {
            LOGGER.error("Failed to create directories for enchantments export", e);
            return ModuleResult.empty();
        }

        final List<String> entryLines = new ArrayList<>();
        final List<String> entryNamespaces = new ArrayList<>();

        for (ResourceLocation key : sortedKeys) {
            final Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(key);
            if (enchantment == null) {
                continue;
            }
            final String descKey = getDescriptionKey(enchantment);
            final boolean translationExists = I18n.exists(descKey);
            if (onlyMissing && translationExists) {
                continue;
            }
            final String value = translationExists ? escapeJson(I18n.get(descKey)) : "";
            entryLines.add("    \"" + escapeJson(descKey) + "\": \"" + value + "\"");
            entryNamespaces.add(key.getNamespace());
        }

        try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(outputPath), StandardCharsets.UTF_8)) {

            writer.write("{\n");
            for (int i = 0; i < entryLines.size(); i++) {
                if (i > 0 && !entryNamespaces.get(i).equals(entryNamespaces.get(i - 1))) {
                    writer.write("\n");
                }
                writer.write(entryLines.get(i));
                writer.write(i < entryLines.size() - 1 ? ",\n" : "\n");
            }
            writer.write("}\n");
        } catch (IOException e) {
            LOGGER.error("Failed to export enchantment descriptions", e);
            return ModuleResult.empty();
        }

        final int count = entryLines.size();
        return ModuleResult.of(true, Component.literal("Exported " + count + " enchantment descriptions in " + lang));
    }

    private static String getDescriptionKey(Enchantment ench) {
        final String descKey = ench.getDescriptionId() + ".desc";
        if (!I18n.exists(descKey) && I18n.exists(ench.getDescriptionId() + ".description")) {
            return ench.getDescriptionId() + ".description";
        }
        return descKey;
    }

    private static String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
