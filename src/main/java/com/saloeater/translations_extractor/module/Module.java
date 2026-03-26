package com.saloeater.translations_extractor.module;

import java.nio.file.Path;

public interface Module {
    String getName();
    ModuleResult execute(Path resourcePackPath, boolean onlyMissing);
}
