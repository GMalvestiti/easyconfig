package com.gmalvestiti.minecraft.easyconfig.holder;

import com.gmalvestiti.minecraft.easyconfig.api.UpdateResult;
import com.gmalvestiti.minecraft.easyconfig.api.AsyncConfigHolder;
import com.gmalvestiti.minecraft.easyconfig.engine.state.ConfigStateManager;
import com.gmalvestiti.minecraft.easyconfig.async.ConfigWorkerScope;
import com.gmalvestiti.minecraft.easyconfig.context.ConfigContext;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class AsyncConfigHolderImplementation<T> extends AbstractConfigHolder<T> implements AsyncConfigHolder<T> {

    private final Executor executor;

    AsyncConfigHolderImplementation(ConfigContext<T> context, ConfigStateManager<T> stateManager) {
        super(context, stateManager);
        this.executor = context.executor();
    }

    @Override
    public void load() {
        await(this::loadAsync);
    }

    @Override
    public CompletableFuture<Void> loadAsync() {
        return schedule(this::performLoad);
    }

    @Override
    public UpdateResult update(Consumer<T> mutator) {
        return await(() -> updateAsync(mutator));
    }

    @Override
    public CompletableFuture<UpdateResult> updateAsync(Consumer<T> mutator) {
        return submit(() -> performUpdate(mutator));
    }

    @Override
    public UpdateResult updateAndSave(Consumer<T> mutator) {
        return await(() -> updateAndSaveAsync(mutator));
    }

    @Override
    public CompletableFuture<UpdateResult> updateAndSaveAsync(Consumer<T> mutator) {
        return submit(() -> performUpdateAndSave(mutator));
    }

    @Override
    public UpdateResult reset() {
        return await(this::resetAsync);
    }

    @Override
    public CompletableFuture<UpdateResult> resetAsync() {
        return submit(this::performReset);
    }

    @Override
    public UpdateResult resetAndSave() {
        return await(this::resetAndSaveAsync);
    }

    @Override
    public CompletableFuture<UpdateResult> resetAndSaveAsync() {
        return submit(this::performResetAndSave);
    }

    @Override
    public void save() {
        await(this::saveAsync);
    }

    @Override
    public CompletableFuture<Void> saveAsync() {
        return schedule(this::performSave);
    }

    private CompletableFuture<Void> schedule(Runnable task) {
        return submit(() -> {
            task.run();
            return null;
        });
    }

    private <V> CompletableFuture<V> submit(Supplier<V> task) {
        if (ConfigWorkerScope.isInsideTask()) {
            return CompletableFuture.failedFuture(scope().exception(ConfigError.NESTED_CONFIG_OPERATION));
        }
        try {
            return CompletableFuture.supplyAsync(task, executor);
        } catch (RejectedExecutionException ex) {
            return CompletableFuture.failedFuture(scope().exception(ConfigError.CONFIG_WORKER_STOPPED, ex));
        }
    }

    private <V> V await(Supplier<CompletableFuture<V>> operation) {
        if (ConfigWorkerScope.isInsideTask()) {
            throw scope().exception(ConfigError.BLOCKING_CALL_ON_CONFIG_THREAD);
        }
        try {
            return operation.get().join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            throw cause instanceof RuntimeException failure ? failure : ex;
        }
    }
}
