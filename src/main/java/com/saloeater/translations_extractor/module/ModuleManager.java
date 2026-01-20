package com.saloeater.translations_extractor.module;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        registerModules();
    }

    private void registerModules() {
        modules.add(new LangModule());
    }

    public void executeAll(Path resourcePackPath) {
        for (Module module : modules) {
            module.execute(resourcePackPath);
        }
    }
}
