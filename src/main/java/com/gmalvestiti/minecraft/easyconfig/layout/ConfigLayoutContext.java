package com.gmalvestiti.minecraft.easyconfig.layout;

import com.gmalvestiti.minecraft.easyconfig.exception.ConfigExceptionHandler;
import com.gmalvestiti.minecraft.easyconfig.storage.ConfigStorage;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.reflection.ConfigFieldAccess;

import java.util.Objects;

public record ConfigLayoutContext(
    ConfigScope scope,
    ConfigStorage storage,
    ConfigExceptionHandler exceptionHandler,
    ConfigFieldAccess fieldAccess
) {
    public ConfigLayoutContext {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(exceptionHandler, "exceptionHandler");
        Objects.requireNonNull(fieldAccess, "fieldAccess");
    }
}
