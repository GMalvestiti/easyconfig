package com.gmalvestiti.minecraft.easyconfig.api;

import com.gmalvestiti.minecraft.easyconfig.api.annotations.Config;
import com.gmalvestiti.minecraft.easyconfig.api.annotations.ConfigEntry;

/**
 * Selects the file format a config type uses on disk.
 *
 * <p>JSON5 and TOML have both first class support: the same config class, the same
 * {@link ConfigEntry} names, and the same comment text produce equivalent files. Switching
 * between them is a one-word change on {@link Config#format()}:
 *
 * <pre>{@code
 * @Config(name = "mymod")                                // config/mymod.json
 * public final class MyModConfig {
 *
 *     @ConfigEntry(comment = "Scale of the on-screen HUD.")
 *     public int hudScale = 2;
 * }
 *
 * @Config(name = "mymod", format = ConfigFormat.TOML)    // config/mymod.toml
 * public final class MyModConfig { }
 * }</pre>
 *
 * <p>{@link #JSON}:
 *
 * <pre>{@code
 * {
 *   // Scale of the on-screen HUD.
 *   "hudScale": 2
 * }
 * }</pre>
 *
 * <p>{@link #TOML}:
 *
 * <pre>{@code
 * #Scale of the on-screen HUD.
 * hudScale = 2
 * }</pre>
 *
 * <p>The format also decides the file extension, so two config types that differ only by format
 * never collide on one file. TOML has no null literal: a {@code null} field is left out and
 * falls back to its default on the next load. JSON writes it as {@code null}.
 *
 * <p>Pick the format once, before players have config files on disk. Changing it later changes
 * the file name, so the old file is left behind untouched and the new one starts from defaults.
 */
public enum ConfigFormat {

    /** JSON5, written to {@code .json} files. */
    JSON(".json"),

    /** TOML, written to {@code .toml} files. */
    TOML(".toml");

    private final String extension;

    ConfigFormat(String extension) {
        this.extension = extension;
    }

    /**
     * Returns the format a config class is stored in.
     *
     * @param configType class to read {@link Config#format()} from; never {@code null}
     * @return the declared format, or {@link #JSON} by default.
     */
    public static ConfigFormat of(Class<?> configType) {
        Config config = configType.getAnnotation(Config.class);
        return config == null ? JSON : config.format();
    }

    /**
     * Returns the file extension this format writes, leading dot included.
     *
     * <p>Appended to {@link Config#name()} when the declared name does not already end with it,
     * so {@code name = "mymod"} and {@code name = "mymod.json"} resolve to the same JSON file.
     *
     * @return the extension, such as {@code ".json"}; never {@code null} or blank
     */
    public String extension() {
        return extension;
    }
}
