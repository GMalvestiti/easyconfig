package com.gmalvestiti.minecraft.easyconfig.api;

import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Extends {@link ConfigHolder} with non-blocking counterparts for every mutating operation.
 *
 * <p>Obtain one through {@link ConfigBuilder#createAsync()} instead of {@link ConfigBuilder#create()}.
 * The returned holder is backed by {@link HolderImplementation#ASYNC} and runs all lifecycle
 * work on the shared config worker, so the {@code *Async} methods submit work and return
 * immediately; the futures complete (or fail) when the worker finishes.
 *
 * <p>Blocking methods still exist via the parent interface, and they block until the worker
 * task finishes — unwrapping {@code CompletionException} so you get {@link EasyConfigException}
 * directly. Calling a blocking method from inside a config task throws
 * {@code BLOCKING_CALL_ON_CONFIG_THREAD}; use the {@code *Async} variant there instead.
 *
 * <pre>{@code
 * AsyncConfigHolder<MyModConfig> config = EasyConfig.holder(MyModConfig.class)
 *     .modId("mymod")
 *     .createAsync();
 *
 * config.loadAsync().thenCompose(v -> config.saveAsync());
 * config.updateAndSaveAsync(cfg -> cfg.hudScale = 3).thenAccept(result -> {
 *     if (!result.accepted()) result.violations().forEach(v -> LOGGER.warn(v.message()));
 * });
 * }</pre>
 *
 * @param <T> the root config type
 */
public interface AsyncConfigHolder<T> extends ConfigHolder<T> {

    /**
     * Loads persisted state without blocking the caller.
     *
     * @return a future that completes after the loaded state is published, or
     *         completes exceptionally with {@link EasyConfigException}
     */
    CompletableFuture<Void> loadAsync();

    /**
     * Mutates and publishes a private candidate without blocking the caller.
     *
     * @param mutator edits a private candidate copy; must not be {@code null}
     * @return a future completing with the same result {@link #update(Consumer)} returns, or
     *         completing exceptionally with {@link EasyConfigException}
     */
    CompletableFuture<UpdateResult> updateAsync(Consumer<T> mutator);

    /**
     * Mutates, publishes, and saves one accepted candidate without blocking the caller.
     *
     * @param mutator edits a private candidate copy; must not be {@code null}
     * @return a future completing with the same result {@link #updateAndSave(Consumer)} returns,
     *         or completing exceptionally with {@link EasyConfigException}
     */
    CompletableFuture<UpdateResult> updateAndSaveAsync(Consumer<T> mutator);

    /**
     * Publishes the declared defaults without blocking the caller.
     *
     * @return a future completing with the same result {@link #reset()} returns, or completing
     *         exceptionally with {@link EasyConfigException}
     */
    CompletableFuture<UpdateResult> resetAsync();

    /**
     * Publishes the declared defaults and saves them without blocking the caller.
     *
     * @return a future completing with the same result {@link #resetAndSave()} returns, or
     *         completing exceptionally with {@link EasyConfigException}
     */
    CompletableFuture<UpdateResult> resetAndSaveAsync();

    /**
     * Saves the current state without blocking the caller.
     *
     * @return a future that completes after persistence, or completes exceptionally with
     *         {@link EasyConfigException}
     */
    CompletableFuture<Void> saveAsync();
}
