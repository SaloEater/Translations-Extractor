package com.saloeater.translations_extractor.module;

import net.minecraft.network.chat.Component;

public class ModuleResult {
    private final boolean filesCreated;
    private final Component summary;

    public ModuleResult(boolean filesCreated, Component summary) {
        this.filesCreated = filesCreated;
        this.summary = summary;
    }

    public boolean isFilesCreated() {
        return filesCreated;
    }

    public Component getSummary() {
        return summary;
    }

    public static ModuleResult empty() {
        return new ModuleResult(false, Component.empty());
    }

    public static ModuleResult of(boolean filesCreated, Component summary) {
        return new ModuleResult(filesCreated, summary);
    }
}
