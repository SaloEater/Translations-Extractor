package com.saloeater.translations_extractor.module;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ModuleManager {
    private final Map<String, Module> modules = new HashMap<>();

    public ModuleManager() {
        registerModules();
    }

    private void registerModules() {
        registerModule(new LangModule());
        registerModule(new PatchouliModule());
    }

    private void registerModule(Module module) {
        modules.put(module.getName(), module);
    }

    public Set<String> getModuleNames() {
        return modules.keySet();
    }

    public ModuleResult execute(String moduleName, Path resourcePackPath) {
        Module module = modules.get(moduleName);
        if (module == null) {
            return ModuleResult.empty();
        }
        return module.execute(resourcePackPath);
    }
}
