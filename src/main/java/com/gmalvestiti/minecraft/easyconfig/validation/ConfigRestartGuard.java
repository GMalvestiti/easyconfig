package com.gmalvestiti.minecraft.easyconfig.validation;

import com.gmalvestiti.minecraft.easyconfig.api.annotations.ConfigEntry;
import com.gmalvestiti.minecraft.easyconfig.api.annotations.ConfigIgnore;
import com.gmalvestiti.minecraft.easyconfig.api.spi.Violation;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.reflection.ConfigFieldAccess;
import com.gmalvestiti.minecraft.easyconfig.storage.ConfigBinder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ConfigRestartGuard<T> {

    private static final ClassValue<List<Field>> FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> owner = type; owner != null && owner != Object.class; owner = owner.getSuperclass()) {
                for (Field field : owner.getDeclaredFields()) {
                    if (isCandidate(field)) {
                        field.trySetAccessible();
                        fields.add(field);
                    }
                }
            }
            return List.copyOf(fields);
        }
    };

    private final ConfigScope scope;
    private final String typeName;
    private final ConfigFieldAccess fieldAccess;

    public ConfigRestartGuard(Class<T> type, ConfigScope scope, ConfigFieldAccess fieldAccess) {
        this.scope = scope;
        this.typeName = type.getName();
        this.fieldAccess = fieldAccess;
    }

    public void enforce(T current, T candidate) {
        List<Violation> violations = restartFieldsOf(current, candidate).stream()
            .filter(this::changed)
            .map(ConfigRestartGuard::violationFor)
            .toList();

        if (violations.isEmpty()) {
            return;
        }

        String summary = violations.stream().map(Violation::message).collect(Collectors.joining("; "));
        throw scope.exception(ConfigError.RESTART_FIELD_CHANGED, violations, typeName, summary);
    }

    public void carryOver(T current, T candidate) {
        for (RestartField found : restartFieldsOf(current, candidate)) {
            fieldAccess.write(found.field, found.candidate, fieldAccess.read(found.field, found.current));
        }
    }

    private List<RestartField> restartFieldsOf(Object current, Object candidate) {
        List<RestartField> found = new ArrayList<>();
        collect(current, candidate, found);
        return found;
    }

    private void collect(Object current, Object candidate, List<RestartField> sink) {
        if (current == null || candidate == null || current.getClass() != candidate.getClass()) {
            return;
        }

        for (Field field : FIELDS.get(current.getClass())) {
            if (isRestartOnly(field)) {
                sink.add(new RestartField(field, current, candidate));
            } else if (descendable(field.getType())) {
                collect(fieldAccess.read(field, current), fieldAccess.read(field, candidate), sink);
            }
        }
    }

    private boolean changed(RestartField found) {
        return !ConfigBinder.toTree(fieldAccess.read(found.field, found.current))
            .equals(ConfigBinder.toTree(fieldAccess.read(found.field, found.candidate)));
    }

    private static Violation violationFor(RestartField found) {
        Field field = found.field;
        return Violation.of(
            "restart." + field.getName(),
            "%s in %s only applies at startup; edit the config file and restart instead"
                .formatted(field.getName(), field.getDeclaringClass().getSimpleName()));
    }

    private static boolean descendable(Class<?> type) {
        return !type.isPrimitive()
            && !type.isArray()
            && !type.isEnum()
            && !type.getName().startsWith("java.")
            && !Collection.class.isAssignableFrom(type)
            && !Map.class.isAssignableFrom(type);
    }

    private static boolean isRestartOnly(Field field) {
        ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
        return entry != null && entry.restart();
    }

    private static boolean isCandidate(Field field) {
        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers)
            && !Modifier.isTransient(modifiers)
            && !field.isSynthetic()
            && field.getAnnotation(ConfigIgnore.class) == null;
    }

    private record RestartField(Field field, Object current, Object candidate) {}
}
