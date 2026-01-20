package com.saloeater.translations_extractor.module;

import java.nio.file.Path;

public interface Module {
    boolean execute(Path resourcePackPath);
}
