package com.gmalvestiti.minecraft.easyconfig.reflection;

import com.gmalvestiti.minecraft.easyconfig.api.ConfigExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ConfigExtensionLookup {

    public record Entry(Field field, Class<?> type, boolean overridesBeforeSave) {

        public boolean isRoot() {
            return field == null;
        }

        public String label() {
            return type.getSimpleName();
        }

        public Object resolveIn(Object root, ConfigFieldAccess fieldAccess) {
            return isRoot() ? root : fieldAccess.read(field, root);
        }
    }

    private final List<Entry> ascending;

    private ConfigExtensionLookup(List<Entry> ascending) {
        this.ascending = ascending;
    }

    public static ConfigExtensionLookup resolve(Class<?> rootType, ConfigFieldAccess fieldAccess) {
        List<Entry> entries = new ArrayList<>();
        for (Field member : fieldAccess.configFieldsOf(rootType)) {
            if (ConfigExtension.class.isAssignableFrom(member.getType())) {
                entries.add(new Entry(member, member.getType(), overridesBeforeSave(member.getType())));
            }
        }

        if (ConfigExtension.class.isAssignableFrom(rootType)) {
            entries.add(new Entry(null, rootType, overridesBeforeSave(rootType)));
        }

        return new ConfigExtensionLookup(List.copyOf(entries));
    }

    public List<Entry> ascending() {
        return ascending;
    }

    public List<Entry> descending() {
        return ascending.reversed();
    }

    public boolean isEmpty() {
        return ascending.isEmpty();
    }

    public boolean hasBeforeSave() {
        return ascending.stream().anyMatch(Entry::overridesBeforeSave);
    }

    private static boolean overridesBeforeSave(Class<?> type) {
        try {
            Method method = type.getMethod("beforeSave");
            return method.getDeclaringClass() != ConfigExtension.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
