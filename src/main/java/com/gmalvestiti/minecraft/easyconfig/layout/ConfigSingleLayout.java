package com.gmalvestiti.minecraft.easyconfig.layout;

import com.gmalvestiti.minecraft.easyconfig.reflection.ConfigObjectFactory;

import java.util.function.Supplier;

public final class ConfigSingleLayout<T> implements ConfigLayout<T> {

    private final Class<T> type;
    private final ConfigLayoutContext layoutContext;

    public ConfigSingleLayout(Class<T> type, ConfigLayoutContext layoutContext) {
        this.type = type;
        this.layoutContext = layoutContext;
    }

    @Override
    public T createDefaults() {
        return ConfigObjectFactory.newInstance(type, layoutContext.scope());
    }

    @Override
    public T load(Supplier<T> currentState) {
        T loaded = layoutContext.storage().read(type);
        return loaded != null ? loaded : createDefaults();
    }

    @Override
    public void save(T data) {
        layoutContext.storage().write(type, data);
    }
}
