package com.saloeater.translations_extractor.module;

import java.nio.file.Path;

public interface Module {
    void execute(Path resourcePackPath);
}
