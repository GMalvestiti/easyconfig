package com.gmalvestiti.minecraft.easyconfig.engine;

import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ConfigEventNotifier<T> {

    private final ConfigScope scope;
    private final List<Consumer<T>> updateListeners;
    private final List<Consumer<T>> loadListeners;
    private final List<Consumer<T>> saveListeners;
    private final List<Consumer<T>> resetListeners;

    public ConfigEventNotifier(
        ConfigScope scope,
        List<Consumer<T>> updateListeners,
        List<Consumer<T>> loadListeners,
        List<Consumer<T>> saveListeners,
        List<Consumer<T>> resetListeners
    ) {
        this.scope = scope;
        this.updateListeners = new CopyOnWriteArrayList<>(updateListeners);
        this.loadListeners = new CopyOnWriteArrayList<>(loadListeners);
        this.saveListeners = new CopyOnWriteArrayList<>(saveListeners);
        this.resetListeners = new CopyOnWriteArrayList<>(resetListeners);
    }

    public void addUpdateListener(Consumer<T> listener) { updateListeners.add(listener); }
    public void addLoadListener(Consumer<T> listener) { loadListeners.add(listener); }
    public void addSaveListener(Consumer<T> listener) { saveListeners.add(listener); }
    public void addResetListener(Consumer<T> listener) { resetListeners.add(listener); }

    public void notifyUpdated(T state) { dispatch(updateListeners, state); }
    public void notifyLoaded(T state) { dispatch(loadListeners, state); }
    public void notifySaved(T state) { dispatch(saveListeners, state); }
    public void notifyReset(T state) { dispatch(resetListeners, state); }

    private void dispatch(List<Consumer<T>> listeners, T state) {
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
