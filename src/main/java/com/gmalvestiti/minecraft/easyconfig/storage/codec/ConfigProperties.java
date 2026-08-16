package com.gmalvestiti.minecraft.easyconfig.storage.codec;

import com.gmalvestiti.minecraft.easyconfig.api.annotations.Config;
import com.gmalvestiti.minecraft.easyconfig.api.annotations.ConfigEntry;
import com.gmalvestiti.minecraft.easyconfig.api.annotations.ConfigIgnore;
import com.gmalvestiti.minecraft.easyconfig.storage.ConfigBinder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers what the codecs need to know about a config class.
 *
 * <p>Lookups are keyed by the name in the file rather than the Java field name, so
 * {@link ConfigEntry#name()} renames resolve here.
 */
final class ConfigProperties {

    private static final ClassValue<Map<String, Field>> BY_PROPERTY_NAME = new ClassValue<>() {
        @Override
        protected Map<String, Field> computeValue(Class<?> type) {
            Map<String, Field> fields = new LinkedHashMap<>();
            for (Class<?> owner = type; owner != null && owner != Object.class; owner = owner.getSuperclass()) {
                for (Field field : owner.getDeclaredFields()) {
                    if (isPersisted(field)) {
                        fields.putIfAbsent(ConfigBinder.propertyNameOf(field), field);
                    }
                }
            }
            return Map.copyOf(fields);
        }
    };

    private ConfigProperties() {}

    static Class<?> nestedTypeOf(Class<?> owner, String property) {
        Field field = fieldFor(owner, property);
        return field == null ? null : field.getType();
    }

    static String commentOfClass(Class<?> type) {
        Config config = type == null ? null : type.getAnnotation(Config.class);
        return config == null ? null : joinLines(config.comment());
    }

    static String commentOfEntry(Class<?> owner, String property, Class<?> nested) {
        Field field = fieldFor(owner, property);
        ConfigEntry entry = field == null ? null : field.getAnnotation(ConfigEntry.class);
        String comment = entry == null ? null : joinLines(entry.comment());
        return comment != null ? comment : commentOfClass(nested);
    }

    private static Field fieldFor(Class<?> owner, String property) {
        return owner == null ? null : BY_PROPERTY_NAME.get(owner).get(property);
    }

    /**
     * Flattens the declared lines into one newline-separated comment, or {@code null} when
     * nothing was declared. Embedded newlines break lines too, so a text block reads the same as
     * an array.
     */
    private static String joinLines(String[] declared) {
        List<String> lines = new ArrayList<>();
        for (String block : declared) {
            for (String line : block.split("\\R", -1)) {
                lines.add(line);
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private static boolean isPersisted(Field field) {
        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers)
            && !Modifier.isTransient(modifiers)
            && !field.isSynthetic()
            && field.getAnnotation(ConfigIgnore.class) == null;
    }
}
