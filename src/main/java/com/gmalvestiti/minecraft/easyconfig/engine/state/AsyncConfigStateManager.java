package com.gmalvestiti.minecraft.easyconfig.engine.state;

import com.gmalvestiti.minecraft.easyconfig.api.spi.StateCloner;

import java.util.Objects;

public final class AsyncConfigStateManager<T> implements ConfigStateManager<T> {

    private final StateCloner<T> cloner;
    private volatile T canonical;
    private volatile T published;

    public AsyncConfigStateManager(StateCloner<T> cloner, T initialCanonical) {
        this.cloner = cloner;
        this.canonical = Objects.requireNonNull(initialCanonical, "initialCanonical");
        this.published = cloner.copy(initialCanonical);
    }

    @Override
    public T published() {
        return published;
    }

    @Override
    public T canonical() {
        return canonical;
    }

    @Override
    public T copyOfCanonical() {
        return cloner.copy(canonical);
    }

    @Override
    public void replaceState(T next) {
        this.canonical = next;
        this.published = cloner.copy(next);
    }
}
