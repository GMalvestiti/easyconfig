package com.gmalvestiti.minecraft.easyconfig.engine;

import com.gmalvestiti.minecraft.easyconfig.api.ConfigExtension;
import com.gmalvestiti.minecraft.easyconfig.reflection.ConfigFieldAccess;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.reflection.ConfigExtensionLookup;

import java.util.List;
import java.util.function.Consumer;

public final class ConfigLifecycleHooks<T> {

    private static final String AFTER_LOAD = "afterLoad";
    private static final String BEFORE_SAVE = "beforeSave";

    private final ConfigScope scope;
    private final ConfigFieldAccess fieldAccess;
    private final ConfigExtensionLookup extensions;

    public ConfigLifecycleHooks(Class<T> type, ConfigScope scope, ConfigFieldAccess fieldAccess) {
        this.scope = scope;
        this.fieldAccess = fieldAccess;
        this.extensions = ConfigExtensionLookup.resolve(type, fieldAccess);
    }

    public boolean hasBeforeSave() {
        return extensions.hasBeforeSave();
    }

    public void afterLoad(T loaded) {
        invokeAll(AFTER_LOAD, extensions.ascending(), loaded, ConfigExtension::afterLoad);
    }

    public void beforeSave(T candidate) {
        invokeAll(BEFORE_SAVE, extensions.descending(), candidate, ConfigExtension::beforeSave);
    }

    private void invokeAll(
        String hook,
        List<ConfigExtensionLookup.Entry> entries,
        T root,
        Consumer<ConfigExtension> call
    ) {
        for (ConfigExtensionLookup.Entry entry : entries) {
            if (entry.resolveIn(root, fieldAccess) instanceof ConfigExtension extension) {
                invoke(hook, entry, extension, call);
            }
        }
    }

    private void invoke(
        String hook,
        ConfigExtensionLookup.Entry entry,
        ConfigExtension extension,
        Consumer<ConfigExtension> call
    ) {
        try {
            call.accept(extension);
        } catch (EasyConfigException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw scope.exception(ConfigError.EXTENSION_HOOK_FAILED, ex, entry.label() + "." + hook, ex);
        }
    }
}
