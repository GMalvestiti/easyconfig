package com.gmalvestiti.minecraft.easyconfig.layout;

import com.gmalvestiti.minecraft.easyconfig.api.annotations.Config;
import com.gmalvestiti.minecraft.easyconfig.api.annotations.ConfigGroup;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

public final class ConfigModelValidator {

    private ConfigModelValidator() {}

    public static <T> ConfigLayout<T> resolveLayout(Class<T> rootType, ConfigLayoutContext layoutContext) {
        ConfigScope scope = layoutContext.scope();

        if (rootType.isAnnotationPresent(Config.class)) {
            validateConfigClass(rootType, scope);
            return new ConfigSingleLayout<>(rootType, layoutContext);
        }

        if (rootType.isAnnotationPresent(ConfigGroup.class)) {
            validateGroupClass(rootType, scope);
            return new ConfigGroupLayout<>(rootType, layoutContext);
        }

        throw scope.exception(ConfigError.MISSING_CONFIG_MARKER, rootType.getName());
    }

    private static void validateConfigClass(Class<?> configType, ConfigScope scope) {
        for (Field field : configType.getDeclaredFields()) {
            if (field.getType().isAnnotationPresent(Config.class)) {
                throw scope.exception(
                    ConfigError.CONFIG_REFERENCE_FORBIDDEN, field.getName(), configType.getName());
            }
        }
    }

    private static void validateGroupClass(Class<?> groupType, ConfigScope scope) {
        Set<Class<?>> memberTypes = new HashSet<>();
        for (Field field : groupType.getDeclaredFields()) {
            int modifiers = field.getModifiers();

            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }

            if (!field.getType().isAnnotationPresent(Config.class)) {
                continue;
            }

            if (Modifier.isFinal(modifiers)) {
                throw scope.exception(
                    ConfigError.FINAL_CONFIG_GROUP_MEMBER, field.getName(), groupType.getName());
            }

            // Two fields of one type would share a file, so each load would give them the
            // same content and each save would overwrite the other.
            if (!memberTypes.add(field.getType())) {
                throw scope.exception(
                    ConfigError.DUPLICATE_CONFIG_GROUP_MEMBER, groupType.getName(), field.getType().getName());
            }
        }
    }
}
