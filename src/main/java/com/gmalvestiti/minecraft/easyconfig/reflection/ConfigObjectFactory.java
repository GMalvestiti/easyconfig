package com.gmalvestiti.minecraft.easyconfig.reflection;

import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public final class ConfigObjectFactory {

    private static final ClassValue<Constructor<?>> CONSTRUCTORS = new ClassValue<>() {
        @Override
        protected Constructor<?> computeValue(Class<?> type) {
            try {
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.trySetAccessible();
                return constructor;
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
    };

    private ConfigObjectFactory() {}

    public static <T> T newInstance(Class<T> type, ConfigScope scope) {
        Constructor<?> constructor = CONSTRUCTORS.get(type);
        if (constructor == null) {
            throw scope.exception(ConfigError.MISSING_DEFAULT_CONSTRUCTOR, type.getName());
        }

        try {
            return type.cast(constructor.newInstance());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw scope.exception(ConfigError.INITIALIZATION_FAILED, ex, ex.getMessage());
        }
    }
}
