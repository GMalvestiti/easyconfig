package com.gmalvestiti.minecraft.easyconfig.validation;

import com.gmalvestiti.minecraft.easyconfig.api.ConfigExtension;
import com.gmalvestiti.minecraft.easyconfig.reflection.ConfigFieldAccess;
import com.gmalvestiti.minecraft.easyconfig.api.spi.Violation;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.reflection.ConfigExtensionLookup;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ConfigValidationRunner<T> {

    private final ConfigScope scope;
    private final String typeName;
    private final ConfigFieldAccess fieldAccess;
    private final ConfigExtensionLookup extensions;

    public ConfigValidationRunner(Class<T> type, ConfigScope scope, ConfigFieldAccess fieldAccess) {
        this.scope = scope;
        this.typeName = type.getName();
        this.fieldAccess = fieldAccess;
        this.extensions = ConfigExtensionLookup.resolve(type, fieldAccess);
    }

    public List<Violation> run(T candidate) {
        if (extensions.isEmpty()) {
            return List.of();
        }

        List<Violation> violations = new ArrayList<>();
        for (ConfigExtensionLookup.Entry entry : extensions.ascending()) {
            if (entry.resolveIn(candidate, fieldAccess) instanceof ConfigExtension extension) {
                append(entry, invoke(entry, extension), violations);
            }
        }

        return violations.isEmpty() ? List.of() : List.copyOf(violations);
    }

    public void runOrThrow(T candidate) {
        List<Violation> violations = run(candidate);
        if (!violations.isEmpty()) {
            String summary = violations.stream().map(Violation::message).collect(Collectors.joining("; "));
            throw scope.exception(ConfigError.VALIDATION_FAILED, violations, typeName, summary);
        }
    }

    private List<Violation> invoke(ConfigExtensionLookup.Entry entry, ConfigExtension extension) {
        List<Violation> produced = new ArrayList<>();
        try {
            extension.validate(produced);
        } catch (EasyConfigException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw scope.exception(ConfigError.VALIDATOR_FAILED, ex, entry.label(), ex.getMessage());
        }
        return produced;
    }
    private void append(ConfigExtensionLookup.Entry entry, List<Violation> produced, List<Violation> sink) {
        if (produced.isEmpty()) {
            return;
        }

        for (Violation violation : produced) {
            if (violation == null) {
                throw scope.exception(ConfigError.VALIDATOR_PRODUCED_NULL_VIOLATION, entry.label());
            }
            if (violation.id() == null || violation.id().isBlank()) {
                throw scope.exception(ConfigError.VALIDATOR_PRODUCED_BLANK_ID, entry.label());
            }
            sink.add(violation);
        }
    }
}
