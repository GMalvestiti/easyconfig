package com.gmalvestiti.minecraft.easyconfig.engine;

import com.gmalvestiti.minecraft.easyconfig.api.spi.StateCloner;
import com.gmalvestiti.minecraft.easyconfig.layout.ConfigLayout;

import java.util.function.Supplier;

public final class ConfigEngine<T> {

    private final ConfigLayout<T> layout;
    private final ConfigLifecycleHooks<T> hooks;
    private final StateCloner<T> cloner;

    public ConfigEngine(ConfigLayout<T> layout, ConfigLifecycleHooks<T> hooks, StateCloner<T> cloner) {
        this.layout = layout;
        this.hooks = hooks;
        this.cloner = cloner;
    }

    public T initialize() {
        return layout.createDefaults();
    }

    public T load(Supplier<T> currentState) {
        T loaded = layout.load(currentState);
        hooks.afterLoad(loaded);
        return loaded;
    }

    public void save(T data) {
        if (!hooks.hasBeforeSave()) {
            layout.save(data);
            return;
        }
        T candidate = cloner.copy(data);
        hooks.beforeSave(candidate);
        layout.save(candidate);
    }
}
