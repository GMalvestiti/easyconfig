package com.gmalvestiti.minecraft.easyconfig.engine;

import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;

import java.util.List;
import java.util.function.Consumer;

public final class ConfigChangeNotifier<T> {

    private final ConfigScope scope;
    private final List<Consumer<T>> listeners;

    public ConfigChangeNotifier(ConfigScope scope, List<Consumer<T>> listeners) {
        this.scope = scope;
        this.listeners = List.copyOf(listeners);
    }

    public void published(T state) {
        for (Consumer<T> listener : listeners) {
            try {
                listener.accept(state);
            } catch (RuntimeException ex) {
                EasyConfigException failure = scope.exception(
                    ConfigError.CHANGE_LISTENER_FAILED, ex, String.valueOf(ex));
                scope.logError(failure.rawMessage(), ex);
            }
        }
    }
}
