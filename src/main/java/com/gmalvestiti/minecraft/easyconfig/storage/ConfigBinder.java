package com.gmalvestiti.minecraft.easyconfig.storage;

import com.gmalvestiti.minecraft.easyconfig.api.annotations.ConfigEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import java.lang.reflect.Field;

/**
 * Converts config objects to and from a format-neutral JSON tree.
 *
 * <p>Every format goes through this one binder, so {@code @ConfigEntry(name = ...)} and the
 * supported field types are the same whether the file ends up as JSON or TOML. The codecs only
 * turn the tree into text.
 */
public final class ConfigBinder {

    private static final Gson GSON = new GsonBuilder()
        .setFieldNamingStrategy(ConfigBinder::propertyNameOf)
        .disableHtmlEscaping()
        .serializeNulls()
        .create();

    private ConfigBinder() {}

    /**
     * Returns the key {@code field} is stored under, honouring {@link ConfigEntry#name()}.
     */
    public static String propertyNameOf(Field field) {
        ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
        return entry == null || entry.name().isBlank() ? field.getName() : entry.name();
    }

    public static JsonElement toTree(Object data) {
        return GSON.toJsonTree(data);
    }

    public static <V> V fromTree(JsonElement tree, Class<V> type) {
        return GSON.fromJson(tree, type);
    }

    public static <V> V copy(V source, Class<V> type) {
        return GSON.fromJson(GSON.toJsonTree(source), type);
    }
}
