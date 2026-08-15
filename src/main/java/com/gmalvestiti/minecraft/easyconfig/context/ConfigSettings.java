package com.gmalvestiti.minecraft.easyconfig.context;

import com.gmalvestiti.minecraft.easyconfig.api.FailurePolicy;
import com.gmalvestiti.minecraft.easyconfig.api.spi.StateCloner;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.api.HolderImplementation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public record ConfigSettings<T>(
    Class<T> type,
    ConfigScope scope,
    Path baseDirectory,
    FailurePolicy readFailurePolicy,
    FailurePolicy writeFailurePolicy,
    FailurePolicy updateFailurePolicy,
    StateCloner<T> stateCloner,
    HolderImplementation implementation,
    List<Consumer<T>> changeListeners
) {
    public ConfigSettings {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(baseDirectory, "baseDirectory");
        Objects.requireNonNull(readFailurePolicy, "readFailurePolicy");
        Objects.requireNonNull(writeFailurePolicy, "writeFailurePolicy");
        Objects.requireNonNull(updateFailurePolicy, "updateFailurePolicy");
        Objects.requireNonNull(stateCloner, "stateCloner");
        Objects.requireNonNull(implementation, "implementation");
        changeListeners = List.copyOf(changeListeners);
    }
}
