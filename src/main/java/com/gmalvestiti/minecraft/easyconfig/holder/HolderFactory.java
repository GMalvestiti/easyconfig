package com.gmalvestiti.minecraft.easyconfig.holder;

import com.gmalvestiti.minecraft.easyconfig.api.ConfigHolder;

import com.gmalvestiti.minecraft.easyconfig.context.ConfigContext;
import com.gmalvestiti.minecraft.easyconfig.context.ConfigContextAssembler;
import com.gmalvestiti.minecraft.easyconfig.context.ConfigSettings;
import com.gmalvestiti.minecraft.easyconfig.engine.state.AsyncConfigStateManager;
import com.gmalvestiti.minecraft.easyconfig.engine.state.ImmutableConfigStateManager;
import com.gmalvestiti.minecraft.easyconfig.engine.state.SimpleConfigStateManager;

public final class HolderFactory {

    private HolderFactory() {}

    public static <T> ConfigHolder<T> create(ConfigSettings<T> settings) {

        ConfigContext<T> context = ConfigContextAssembler.assemble(settings);
        T initial = HolderState.materialized(context, HolderState.validatedDefaults(context));

        return switch (settings.implementation()) {
            case ASYNC -> new AsyncConfigHolderImplementation<>(
                context, new AsyncConfigStateManager<>(context.stateCloner(), initial));
            case SIMPLE -> new SimpleConfigHolderImplementation<>(
                context, new SimpleConfigStateManager<>(context.stateCloner(), initial));
            case IMMUTABLE -> new ImmutableConfigHolderImplementation<>(
                context,
                new ImmutableConfigStateManager<>(context.stateCloner(), context.scope(), initial));
        };
    }
}
