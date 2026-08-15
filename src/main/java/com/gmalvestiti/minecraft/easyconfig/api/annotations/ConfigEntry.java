package com.gmalvestiti.minecraft.easyconfig.api.annotations;

import com.gmalvestiti.minecraft.easyconfig.api.ConfigHolder;
import com.gmalvestiti.minecraft.easyconfig.api.UpdateResult;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes one config field: how it is named in the file, what it does, and when it may change.
 *
 * <p>Everything is optional, so annotate only the fields that need it:
 *
 * <pre>{@code
 * @Config(name = "mymod")
 * public final class MyModConfig {
 *
 *     @ConfigEntry(comment = "Scale of the on-screen HUD.")
 *     public int hudScale = 2;
 *
 *     @ConfigEntry(name = "hide-hints")
 *     public boolean hideHints = false;
 *
 *     @ConfigEntry(comment = "Takes effect after a restart.", restart = true)
 *     public boolean useNativeRenderer = false;
 * }
 * }</pre>
 *
 * <p>writes
 *
 * <pre>{@code
 * {
 *   // Scale of the on-screen HUD.
 *   "hudScale": 2,
 *   "hide-hints": false,
 *   // Takes effect after a restart.
 *   "useNativeRenderer": false
 * }
 * }</pre>
 *
 * <p>The same annotation drives every format, so a config class annotated once reads and writes
 * identically as JSON and as TOML. To comment the class itself rather than a field, use
 * {@link Config#comment()}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigEntry {

    /**
     * Names the field in the config file, decoupling it from the Java field name.
     *
     * <p>Empty by default, which uses the Java field name as-is. Set it when the file should
     * read differently from the code — a kebab-case key, a name kept stable across a Java-side
     * rename, or a key that is not a legal Java identifier:
     *
     * <pre>{@code
     * public boolean hideHints = false; // results in "hideHints"
     *
     * @ConfigEntry(name = "hide-hints")
     * public boolean hideHints = false; // results in "hide-hints"
     * }</pre>
     *
     * <p>The name applies to reading and writing alike, so an existing file keyed by the Java
     * name is not migrated: it is read as a missing value and the field keeps its default until
     * the next save writes the new key.
     *
     * <p>Two fields of one class must not resolve to the same name, whether through this
     * attribute or by colliding with another field's Java name.
     *
     * @return the key to use in the file, or {@code ""} to use the Java field name
     */
    String name() default "";

    /**
     * Explains the field in the config file.
     *
     * <pre>{@code
     * @ConfigEntry(comment = "Scale of the on-screen HUD.")
     * public int hudScale = 2;
     *
     * @ConfigEntry(comment = {"Scale of the on-screen HUD.", "Between 1 and 4."})
     * public int hudScale = 2;
     * }</pre>
     *
     * <p>One array entry becomes one line in the file. A blank entry leaves a blank line, which is
     * how you separate paragraphs. An entry containing newlines is split too, so a text block
     * works as well as an array.
     *
     * <p>Write the text, not the markers. Each format renders them its own way, and a marker that
     * leaks into your text is defused rather than written as-is.
     *
     * <p>Fields inside collections and maps are not commented, because their entries have no
     * single declaring field.
     *
     * <p>Purely cosmetic: comments never affect parsing, validation, or defaults.
     *
     * @return the comment lines, or an empty array for no comment
     */
    String[] comment() default {};

    /**
     * Refuses runtime changes to a field that only takes effect while the game is starting.
     *
     * <p>Some settings are read once at startup. Writing them at runtime leaves the file disagreeing with the running game, so the attempt fails immediately instead:
     *
     * <pre>{@code
     * UpdateResult result = holder.updateAndSave(config -> config.useNativeRenderer = true);
     * if (!result.accepted()) {
     *     result.violations().forEach(v -> LOGGER.warn("{}: {}", v.id(), v.message()));
     * }
     * }</pre>
     *
     * <p>The whole update is rejected, not just the field, so a mutator that touches several
     * fields never applies half of them. Rejection follows the update failure policy: it throws
     * under {@code STRICT} and returns a rejected {@link UpdateResult}
     * under {@code FALLBACK}. {@link ConfigHolder#reset()} is the exception: it restores every
     * other field to its default and leaves this one alone.
     *
     * <p>Loading is not an update: {@link ConfigHolder#load()} replaces the state wholesale, so
     * a player who edits the file and restarts still gets the new value.
     *
     * @return {@code true} to reject every update that changes this field
     */
    boolean restart() default false;
}
