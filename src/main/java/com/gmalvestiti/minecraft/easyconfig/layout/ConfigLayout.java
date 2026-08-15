package com.gmalvestiti.minecraft.easyconfig.layout;

import com.gmalvestiti.minecraft.easyconfig.api.annotations.Config;
import com.gmalvestiti.minecraft.easyconfig.api.annotations.ConfigGroup;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;

import java.util.function.Supplier;

/**
 * Maps a config root to its storage units.
 *
 * <p>A {@link Config @Config} root owns one file. A {@link ConfigGroup @ConfigGroup} root owns
 * one file per public field whose declared type carries {@code @Config}; other fields are ignored
 * for persistence and validation.
 *
 * <pre>{@code
 * // Single-file layout — @Config root, named after the mod.
 * @Config(name = "mymod")
 * public final class MyModConfig { ... }
 *
 * // Per-member layout — @ConfigGroup root, each @Config member gets its own file.
 * @ConfigGroup
 * public final class ModConfigs {
 *     public ClientConfig client = new ClientConfig();   // config/mymod/client.json5
 *     public ServerConfig server = new ServerConfig();   // config/mymod/server.json5
 * }
 * }</pre>
 *
 * <p>Implementations are assembled once per holder and may cache reflection metadata. Throw
 * {@link EasyConfigException} for model, storage, or reflection failures; leave policy decisions
 * to the exception handler.
 *
 * @param <T> config root or group type handled by this layout
 */
public interface ConfigLayout<T> {

    /**
     * Creates fresh default state for this layout.
     *
     * <p>Grouped layouts must also fill each persistable member field with a non-null default
     * instance.
     *
     * @return a new root or group instance populated with declared defaults; never {@code null}
     * @throws EasyConfigException when the root or a grouped member cannot be instantiated
     */
    T createDefaults();

    /**
     * Loads the root state from its storage units.
     *
     * <p>Single-file layouts ignore {@code currentState} and return the deserialized root or
     * defaults when no file exists. Grouped layouts may call {@code currentState} to obtain a
     * private group shell, then replace each persistable member from its own file or defaults.
     *
     * @param currentState supplies a private copy of the canonical state on demand; never
     *                     {@code null}; implementations may call it zero or one time
     * @return loaded state ready for validation and publication; never {@code null}
     * @throws EasyConfigException when reading, deserialization, reflection, or default construction fails
     */
    T load(Supplier<T> currentState);

    /**
     * Persists {@code data} to every storage unit owned by this layout.
     *
     * <p>Grouped layouts should substitute a fresh default member when a persistable member field
     * is {@code null}; the file must represent a valid member object, not a missing one.
     *
     * @param data root or group state to persist; must not be {@code null}
     * @throws EasyConfigException when writing, serialization, reflection, or default construction fails
     */
    void save(T data);
}
