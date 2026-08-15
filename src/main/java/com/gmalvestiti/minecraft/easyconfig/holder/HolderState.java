package com.gmalvestiti.minecraft.easyconfig.holder;

import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.context.ConfigContext;

final class HolderState {

    private HolderState() {}

    static <T> T validatedDefaults(ConfigContext<T> context) {
        T defaults = context.engine().initialize();
        context.validationRunner().runOrThrow(defaults);
        return defaults;
    }

    static <T> T loaded(ConfigContext<T> context, T defaults) {
        try {
            return context.exceptionHandler().onRead(context.type(), () -> {
                T candidate = context.engine().load(context.engine()::initialize);
                context.validationRunner().runOrThrow(candidate);
                return candidate;
            }).valueOr(() -> defaults);
        } catch (EasyConfigException failure) {
            if (!failure.defect()) {
                context.scope().logError(failure.rawMessage(), failure);
            }
            throw failure;
        }
    }

    static <T> T materialized(ConfigContext<T> context, T defaults) {
        T initial = loaded(context, defaults);
        context.exceptionHandler().onWrite(() -> context.engine().save(initial));
        return initial;
    }
}
