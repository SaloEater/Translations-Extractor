package com.saloeater.translations_extractor.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

public class Config {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ClientConfig CLIENT;

    static {
        ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();
        CLIENT = new ClientConfig(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();
    }

    public static class ClientConfig {
        public final ForgeConfigSpec.ConfigValue<String> sourceLanguage;
        public final ForgeConfigSpec.ConfigValue<String> targetLanguage;
        public final ForgeConfigSpec.BooleanValue hideExistingTranslations;
        public final ForgeConfigSpec.BooleanValue includeSourceLanguageFiles;
        public final ForgeConfigSpec.ConfigValue<String> resourcePackName;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> only;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> skip;

        public ClientConfig(ForgeConfigSpec.Builder builder) {
            builder.push("translations");

            sourceLanguage = builder
                    .comment("The source language code to extract translations from (e.g., en_us)")
                    .define("sourceLanguage", "en_us");

            targetLanguage = builder
                    .comment("The target language code to extract translations to (e.g., ru_ru)")
                    .define("targetLanguage", "ru_ru");

            hideExistingTranslations = builder
                    .comment("If true, only include keys that are missing in the target language (hide already translated keys)")
                    .define("hideExistingTranslations", false);

            includeSourceLanguageFiles = builder
                    .comment("If true, also include source language files in the resource pack")
                    .define("includeSourceLanguageFiles", false);

            resourcePackName = builder
                    .comment("The name of the generated resource pack folder")
                    .define("resourcePackName", "ExtractedTranslations");

            only = builder
                    .comment("If not empty, only extract translations from these namespaces (e.g., [\"mymod\", \"othermod\"])")
                    .defineList("only", new ArrayList<>(), obj -> obj instanceof String);

            skip = builder
                    .comment("Namespaces to skip when extracting translations (e.g., [\"minecraft\", \"forge\"])")
                    .defineList("skip", List.of("minecraft"), obj -> obj instanceof String);

            builder.pop();
        }
    }
}
