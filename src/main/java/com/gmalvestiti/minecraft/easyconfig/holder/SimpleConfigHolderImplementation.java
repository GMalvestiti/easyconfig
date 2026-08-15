package com.gmalvestiti.minecraft.easyconfig.holder;

import com.gmalvestiti.minecraft.easyconfig.api.UpdateResult;
import com.gmalvestiti.minecraft.easyconfig.engine.state.ConfigStateManager;
import com.gmalvestiti.minecraft.easyconfig.context.ConfigContext;

import java.util.function.Consumer;

final class SimpleConfigHolderImplementation<T> extends AbstractConfigHolder<T> {

    SimpleConfigHolderImplementation(ConfigContext<T> context, ConfigStateManager<T> stateManager) {
        super(context, stateManager);
    }

    @Override
    public void load() {
        performLoad();
    }

    @Override
    public UpdateResult update(Consumer<T> mutator) {
        return performUpdate(mutator);
    }

    @Override
    public UpdateResult updateAndSave(Consumer<T> mutator) {
        return performUpdateAndSave(mutator);
    }

    @Override
    public UpdateResult resetAndSave() {
        return performResetAndSave();
    }

    @Override
    public UpdateResult reset() {
        return performReset();
    }

    @Override
    public void save() {
        performSave();
    }
}
