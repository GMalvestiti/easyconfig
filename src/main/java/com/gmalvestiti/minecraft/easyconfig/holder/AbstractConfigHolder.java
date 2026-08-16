package com.gmalvestiti.minecraft.easyconfig.holder;

import com.gmalvestiti.minecraft.easyconfig.shared.ConfigOperation;
import com.gmalvestiti.minecraft.easyconfig.api.ConfigHolder;
import com.gmalvestiti.minecraft.easyconfig.api.UpdateResult;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.engine.state.ConfigStateManager;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigExceptionHandler;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigOutcome;
import com.gmalvestiti.minecraft.easyconfig.context.ConfigContext;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

abstract class AbstractConfigHolder<T> implements ConfigHolder<T> {

    protected final ConfigContext<T> context;
    protected final ConfigStateManager<T> stateManager;

    protected AbstractConfigHolder(ConfigContext<T> context, ConfigStateManager<T> stateManager) {
        this.context = context;
        this.stateManager = stateManager;
    }

    protected final ConfigScope scope() {
        return context.scope();
    }

    protected final ConfigExceptionHandler exceptionHandler() {
        return context.exceptionHandler();
    }

    @Override
    public final T data() {
        return stateManager.published();
    }

    @Override
    public final T copy() {
        return stateManager.copyOfCanonical();
    }

    protected final void performLoad() {
        try {
            ConfigOutcome<T> outcome = exceptionHandler().onRead(context.type(), () -> {
                T candidate = context.engine().load(stateManager::copyOfCanonical);
                context.validationRunner().runOrThrow(candidate);
                return candidate;
            });

            stateManager.replaceState(outcome.valueOr(context.engine()::initialize));

            if (outcome.completed()) {
                context.eventNotifier().notifyLoaded(stateManager.published());
                logCompleted(ConfigOperation.LOAD);
            }
        } catch (EasyConfigException failure) {
            if (!failure.defect()) {
                scope().logError(failure.rawMessage(), failure);
            }
            throw failure;
        }
    }

    protected final UpdateResult performUpdate(Consumer<T> mutator) {
        UpdateResult result = attempt(() -> {
            Objects.requireNonNull(mutator, "mutator");
            T candidate = stateManager.copyOfCanonical();
            mutator.accept(candidate);
            return candidate;
        });
        if (result.accepted()) {
            context.eventNotifier().notifyUpdated(stateManager.published());
        }
        return result;
    }

    protected final UpdateResult performReset() {
        UpdateResult result = attempt(() -> {
            T defaults = context.engine().initialize();
            context.restartGuard().carryOver(stateManager.canonical(), defaults);
            return defaults;
        });
        if (result.accepted()) {
            context.eventNotifier().notifyReset(stateManager.published());
        }
        return result;
    }

    protected final UpdateResult performResetAndSave() {
        UpdateResult result = performReset();
        if (result.accepted()) {
            performSave();
        }
        return result;
    }

    protected final UpdateResult performUpdateAndSave(Consumer<T> mutator) {
        UpdateResult result = performUpdate(mutator);
        if (result.accepted()) {
            performSave();
        }
        return result;
    }

    protected final void performSave() {
        if (exceptionHandler().onWrite(() -> context.engine().save(stateManager.canonical())).completed()) {
            logCompleted(ConfigOperation.SAVE);
            context.eventNotifier().notifySaved(stateManager.published());
        }
    }

    private UpdateResult attempt(Supplier<T> candidate) {
        ConfigOutcome<T> outcome = exceptionHandler().onUpdate(() -> {
            T next = candidate.get();
            context.restartGuard().enforce(stateManager.canonical(), next);
            context.validationRunner().runOrThrow(next);
            stateManager.replaceState(next);
            return next;
        });

        if (outcome.degraded()) {
            return UpdateResult.rejected(outcome.violations());
        }

        return UpdateResult.published();
    }

    @Override
    public final ConfigHolder<T> onUpdate(Consumer<T> listener) {
        if (listener != null) {
            context.eventNotifier().addUpdateListener(listener);
        }
        return this;
    }

    @Override
    public final ConfigHolder<T> onLoad(Consumer<T> listener) {
        if (listener != null) {
            context.eventNotifier().addLoadListener(listener);
        }
        return this;
    }

    @Override
    public final ConfigHolder<T> onSave(Consumer<T> listener) {
        if (listener != null) {
            context.eventNotifier().addSaveListener(listener);
        }
        return this;
    }

    @Override
    public final ConfigHolder<T> onReset(Consumer<T> listener) {
        if (listener != null) {
            context.eventNotifier().addResetListener(listener);
        }
        return this;
    }

    private void logCompleted(ConfigOperation operation) {
        scope().logInfo("Config %s operation completed successfully".formatted(operation.displayName()));
    }
}
