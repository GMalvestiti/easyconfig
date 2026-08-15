package com.gmalvestiti.minecraft.easyconfig.context;

import com.gmalvestiti.minecraft.easyconfig.exception.ConfigExceptionHandler;
import com.gmalvestiti.minecraft.easyconfig.layout.ConfigLayout;
import com.gmalvestiti.minecraft.easyconfig.reflection.ConfigFieldAccess;
import com.gmalvestiti.minecraft.easyconfig.storage.ConfigStorage;
import com.gmalvestiti.minecraft.easyconfig.async.ConfigExecutors;
import com.gmalvestiti.minecraft.easyconfig.engine.ConfigChangeNotifier;
import com.gmalvestiti.minecraft.easyconfig.engine.ConfigEngine;
import com.gmalvestiti.minecraft.easyconfig.engine.ConfigLifecycleHooks;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.layout.ConfigLayoutContext;
import com.gmalvestiti.minecraft.easyconfig.layout.ConfigModelValidator;
import com.gmalvestiti.minecraft.easyconfig.storage.ConfigPathResolver;
import com.gmalvestiti.minecraft.easyconfig.validation.ConfigValidationRunner;
import com.gmalvestiti.minecraft.easyconfig.validation.ConfigRestartGuard;

import java.util.Objects;

public final class ConfigContextAssembler {

    private ConfigContextAssembler() {}

    public static <T> ConfigContext<T> assemble(ConfigSettings<T> settings) {
        Objects.requireNonNull(settings, "settings");
        ConfigScope scope = settings.scope();

        ConfigFieldAccess fieldAccess = new ConfigFieldAccess(scope);
        ConfigStorage storage = new ConfigStorage(
            new ConfigPathResolver(settings.baseDirectory(), scope),
            scope,
            fieldAccess
        );

        ConfigExceptionHandler exceptionHandler = new ConfigExceptionHandler(
            scope,
            settings.readFailurePolicy(),
            settings.writeFailurePolicy(),
            settings.updateFailurePolicy(),
            storage::backupCorrupted
        );

        ConfigLayout<T> layout = ConfigModelValidator.resolveLayout(
            settings.type(),
            new ConfigLayoutContext(scope, storage, exceptionHandler, fieldAccess)
        );

        ConfigEngine<T> engine = new ConfigEngine<>(
            layout,
            new ConfigLifecycleHooks<>(settings.type(), scope, fieldAccess),
            settings.stateCloner()
        );

        ConfigValidationRunner<T> validationRunner =
            new ConfigValidationRunner<>(settings.type(), scope, fieldAccess);

        ConfigRestartGuard<T> restartGuard =
            new ConfigRestartGuard<>(settings.type(), scope, fieldAccess);

        ConfigChangeNotifier<T> changeNotifier =
            new ConfigChangeNotifier<>(scope, settings.changeListeners());

        return new ConfigContext<>(
            settings.type(),
            scope,
            ConfigExecutors.defaultExecutor(),
            engine,
            validationRunner,
            restartGuard,
            changeNotifier,
            settings.stateCloner(),
            exceptionHandler
        );
    }
}
