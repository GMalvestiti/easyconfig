package com.gmalvestiti.minecraft.easyconfig.context;

import com.gmalvestiti.minecraft.easyconfig.exception.ConfigExceptionHandler;
import com.gmalvestiti.minecraft.easyconfig.engine.ConfigChangeNotifier;
import com.gmalvestiti.minecraft.easyconfig.engine.ConfigEngine;
import com.gmalvestiti.minecraft.easyconfig.api.spi.StateCloner;
import com.gmalvestiti.minecraft.easyconfig.validation.ConfigValidationRunner;
import com.gmalvestiti.minecraft.easyconfig.validation.ConfigRestartGuard;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;

import java.util.concurrent.Executor;

public record ConfigContext<T>(
    Class<T> type,
    ConfigScope scope,
    Executor executor,
    ConfigEngine<T> engine,
    ConfigValidationRunner<T> validationRunner,
    ConfigRestartGuard<T> restartGuard,
    ConfigChangeNotifier<T> changeNotifier,
    StateCloner<T> stateCloner,
    ConfigExceptionHandler exceptionHandler
) {
}
