package com.gmalvestiti.minecraft.easyconfig.api.annotations;

import com.gmalvestiti.minecraft.easyconfig.api.ConfigFormat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks one class as a config file root.
 *
 * <p>The class must expose a public no-argument constructor, whose field values are the
 * defaults. It may hold any field the JSON provider supports, but never a field typed with
 * another {@code @Config} class — use {@link ConfigGroup} for that. Each config type owns
 * exactly one file:
 *
 * <pre>{@code
 * @Config(name = "mymod")
 * public final class MyModConfig {
 *     public boolean showHints = true;
 *     public int hudScale = 2;
 * }
 * }</pre>
 *
 * <p>The file is {@code <baseDir>/<path>/<name><extension>}: {@link #path()} contributes only
 * directories, {@link #name()} contributes only the file, {@link #format()} contributes the
 * extension, and {@code baseDir} is the platform config directory unless
 * {@code ConfigBuilder.baseDir(...)} overrides it. The example above resolves to
 * {@code config/mymod.json5}.
 *
 * <p>Name the file after your mod. The owning mod id is <em>not</em> part of the path, so a
 * generic {@code name} such as {@code "config"} or {@code "client"} in the shared config root
 * collides with whichever other mod picked it first, and the second holder to be created fails
 * with {@code ConfigError.CONFLICTING_CONFIG_PATH}:
 *
 * <pre>{@code
 * @Config(name = "mymod")                     // config/mymod.json5        — one file
 * @Config(name = "client", path = "mymod")    // config/mymod/client.json5 — several files
 * @Config(name = "client")                    // config/client.json5       — avoid: not yours alone
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Config {

    /**
     * Names the config file, and nothing else.
     *
     * <p>Exactly one file-name component — never a directory, and the only place the file name
     * comes from. Name it after your mod, since the config root is shared with every other mod.
     * The extension of the declared {@link #format()} is appended when missing, ignoring case:
     *
     * <pre>{@code
     * name = "mymod"         -> mymod.json5        // recommended for a single file
     * name = "mymod.json5"   -> mymod.json5
     * name = "client"        -> client.json5       // only inside your own path, see path()
     * }</pre>
     *
     * <p>Blank values, the bare extension {@code .json5}, and values containing {@code /} or
     * {@code \} are rejected with {@code ConfigError.INVALID_CONFIG_NAME}; put directories in
     * {@link #path()} instead.
     *
     * @return the config file name, with or without the format's extension
     */
    String name();

    /**
     * Names the directories that contain the file, and nothing else.
     *
     * <p>A relative, {@code /}-separated directory path resolved under the config directory —
     * never a file name and never a file extension. Empty by default, which puts the file
     * straight in the config root; give it your mod id once you own more than one file. Missing
     * directories are created on the first save:
     *
     * <pre>{@code
     * path = "",       name = "mymod"   -> config/mymod.json5            // one file
     * path = "mymod",  name = "client"  -> config/mymod/client.json5    // several files
     * path = "mymod/gui", name = "hud" -> config/mymod/gui/hud.json5
     *
     * // Wrong: the file name belongs in name(), so this creates a directory called client.json5
     * path = "mymod/client.json5" -> config/mymod/client.json5/client.json5
     * }</pre>
     *
     * <p>Absolute paths, and relative ones that escape the config root once normalized, are
     * rejected with {@code ConfigError.INVALID_CONFIG_PATH}.
     *
     * @return a relative directory path, or {@code ""} for the mod config root
     */
    String path() default "";

    /**
     * Chooses the file format, and with it the file extension.
     *
     * <p>{@link ConfigFormat#JSON} by default, which writes JSON5 so comments stay in the file.
     * {@link ConfigFormat#TOML} writes TOML instead. The config class itself does not change:
     *
     * <pre>{@code
     * @Config(name = "mymod")                              // config/mymod.json5
     * @Config(name = "mymod", format = ConfigFormat.TOML)  // config/mymod.toml
     * }</pre>
     *
     * <p>Decide before shipping. Changing the format later changes the file name, so the old
     * file stays on disk untouched and the new one starts from defaults.
     *
     * @return the format this config is stored in; never {@code null}
     */
    ConfigFormat format() default ConfigFormat.JSON;

    /**
     * Heads the config file with an explanation of what it is for.
     *
     * <p>The class-level counterpart of {@link ConfigEntry#comment()}: that one documents a
     * single field, this one documents the file as a whole. Use it for what the file is, how to
     * get the defaults back, or where the real documentation lives — the things a player wants
     * to read before the first setting:
     *
     * <pre>{@code
     * @Config(name = "mymod", comment = "MyMod settings. Delete this file to restore the defaults.")
     * public final class MyModConfig { }
     *
     * @Config(name = "mymod", comment = {
     *     "MyMod settings.",
     *     "",
     *     "Delete this file to restore the defaults."
     * })
     * public final class MyModConfig { }
     * }</pre>
     *
     * <p>One entry per line of the file; a blank entry is a blank comment line. An entry
     * containing newlines is split, so a text block works as well as an array. Write the text,
     * not the comment markers — each format renders them its own way.
     *
     * <p>Purely cosmetic: a comment never affects parsing, validation, or defaults.
     *
     * @return the header lines, or an empty array for no header
     */
    String[] comment() default {};
}
