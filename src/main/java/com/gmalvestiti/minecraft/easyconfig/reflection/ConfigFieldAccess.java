package com.gmalvestiti.minecraft.easyconfig.reflection;

import com.gmalvestiti.minecraft.easyconfig.api.annotations.Config;
import com.gmalvestiti.minecraft.easyconfig.api.annotations.ConfigIgnore;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class ConfigFieldAccess {

    private static final ClassValue<List<Field>> CONFIG_FIELDS_CACHE = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Field field : type.getDeclaredFields()) {
                if (isPersistableConfigField(field)) {
                    field.trySetAccessible();
                    fields.add(field);
                }
            }
            return List.copyOf(fields);
        }
    };

    private final ConfigScope scope;

    public ConfigFieldAccess(ConfigScope scope) {
        this.scope = scope;
    }

    public List<Field> configFieldsOf(Class<?> ownerType) {
        return CONFIG_FIELDS_CACHE.get(ownerType);
    }

    public Object read(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException | RuntimeException ex) {
            throw accessFailure(field, ex);
        }
    }

    public void write(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException | RuntimeException ex) {
            throw accessFailure(field, ex);
        }
    }

    private EasyConfigException accessFailure(Field field, Exception cause) {
        return scope.exception(
            ConfigError.REFLECTION_ACCESS, cause, field.getName(), field.getDeclaringClass().getName());
    }

    private static boolean isPersistableConfigField(Field field) {
        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers)
            && !Modifier.isTransient(modifiers)
            && field.getAnnotation(ConfigIgnore.class) == null
            && field.getType().isAnnotationPresent(Config.class);
    }
}
