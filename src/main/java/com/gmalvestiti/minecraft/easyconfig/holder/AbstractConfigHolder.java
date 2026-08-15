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
                notifyChanged();
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
        return attempt(() -> {
            Objects.requireNonNull(mutator, "mutator");
            T candidate = stateManager.copyOfCanonical();
            mutator.accept(candidate);
            return candidate;
        });
    }

    protected final UpdateResult performReset() {
        return attempt(() -> {
            T defaults = context.engine().initialize();
            context.restartGuard().carryOver(stateManager.canonical(), defaults);
            return defaults;
        });
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

        notifyChanged();
        return UpdateResult.published();
    }

    private void notifyChanged() {
        context.changeNotifier().published(stateManager.published());
    }

    private void logCompleted(ConfigOperation operation) {
        scope().logInfo("Config %s operation completed successfully".formatted(operation.displayName()));
    }
}
