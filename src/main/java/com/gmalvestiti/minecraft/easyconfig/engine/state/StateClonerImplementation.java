package com.gmalvestiti.minecraft.easyconfig.engine.state;

import com.gmalvestiti.minecraft.easyconfig.api.spi.StateCloner;
import com.gmalvestiti.minecraft.easyconfig.storage.ConfigBinder;

import java.util.Objects;

public final class StateClonerImplementation<T> implements StateCloner<T> {

    private final Class<T> type;

    public StateClonerImplementation(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override
    public T copy(T source) {
        return source == null ? null : ConfigBinder.copy(source, type);
    }
}
