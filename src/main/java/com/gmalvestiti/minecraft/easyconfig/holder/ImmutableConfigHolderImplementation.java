package com.gmalvestiti.minecraft.easyconfig.holder;

import com.gmalvestiti.minecraft.easyconfig.shared.ConfigOperation;
import com.gmalvestiti.minecraft.easyconfig.api.UpdateResult;
import com.gmalvestiti.minecraft.easyconfig.api.spi.Violation;
import com.gmalvestiti.minecraft.easyconfig.engine.state.ConfigStateManager;
import com.gmalvestiti.minecraft.easyconfig.context.ConfigContext;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;

import java.util.List;
import java.util.function.Consumer;

final class ImmutableConfigHolderImplementation<T> extends AbstractConfigHolder<T> {

    private static final String NAME = "immutable";
    private static final String REFUSED_ID = "holder.immutable";

    ImmutableConfigHolderImplementation(ConfigContext<T> context, ConfigStateManager<T> stateManager) {
        super(context, stateManager);
    }

    @Override
    public void load() {
        exceptionHandler().reject(ConfigOperation.LOAD, unsupportedError(ConfigOperation.LOAD));
    }

    @Override
    public UpdateResult update(Consumer<T> mutator) {
        return refuseUpdate();
    }

    @Override
    public UpdateResult updateAndSave(Consumer<T> mutator) {
        return refuseUpdate();
    }

    @Override
    public UpdateResult reset() {
        return refuseUpdate();
    }

    @Override
    public UpdateResult resetAndSave() {
        return refuseUpdate();
    }

    @Override
    public void save() {
        performSave();
    }

    private UpdateResult refuseUpdate() {
        exceptionHandler().reject(ConfigOperation.UPDATE, unsupportedError(ConfigOperation.UPDATE));
        // Only reached under FALLBACK — STRICT threw above.
        return UpdateResult.rejected(List.of(Violation.of(
            REFUSED_ID,
            "An %s holder cannot %s; rebuild with a mutable implementation to change values"
                .formatted(NAME, ConfigOperation.UPDATE.displayName()))));
    }

    private EasyConfigException unsupportedError(ConfigOperation operation) {
        return scope().exception(ConfigError.HOLDER_OPERATION_UNSUPPORTED, NAME, operation.displayName());
    }
}
