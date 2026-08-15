package com.gmalvestiti.minecraft.easyconfig.engine.state;

import com.gmalvestiti.minecraft.easyconfig.api.spi.StateCloner;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;

import java.util.Objects;

public final class ImmutableConfigStateManager<T> implements ConfigStateManager<T> {

    private final StateCloner<T> cloner;
    private final ConfigScope scope;
    private final T state;
    private final T published;

    public ImmutableConfigStateManager(StateCloner<T> cloner, ConfigScope scope, T state) {
        this.cloner = cloner;
        this.scope = scope;
        this.state = Objects.requireNonNull(state, "state");
        this.published = cloner.copy(this.state);
    }

    @Override
    public T published() {
        return published;
    }

    @Override
    public T canonical() {
        return state;
    }

    @Override
    public T copyOfCanonical() {
        return cloner.copy(state);
    }

    @Override
    public void replaceState(T next) {
        throw scope.exception(ConfigError.HOLDER_OPERATION_UNSUPPORTED, "immutable", "update");
    }
}
