package com.gmalvestiti.minecraft.easyconfig.api;

import com.gmalvestiti.minecraft.easyconfig.api.annotations.Config;
import com.gmalvestiti.minecraft.easyconfig.api.spi.StateCloner;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.context.ConfigSettings;
import com.gmalvestiti.minecraft.easyconfig.engine.state.StateClonerImplementation;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.holder.HolderFactory;
import com.gmalvestiti.minecraft.easyconfig.platform.Platform;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Collects the caller's choices and creates the holder.
 *
 * <p>Obtain one from {@link EasyConfig#holder(Class)}, set {@link #modId(String)}, then finish
 * with one of the three terminal methods. The one you pick <em>is</em> the choice of holder
 * implementation — there is no separate setter for it:
 *
 * <ul>
 *   <li>{@link #create()} — inline on the calling thread. Not thread-safe.</li>
 *   <li>{@link #createAsync()} — dedicated worker, thread-safe, adds {@code *Async} methods.</li>
 *   <li>{@link #createImmutable()} — loads once at build time, refuses later mutation.</li>
 * </ul>
 *
 * <p>Everything in between is optional, and passing {@code null} to any setter means
 * "keep the default":
 *
 * <pre>{@code
 * // Defaults: FALLBACK policies, platform config directory.
 * ConfigHolder<MyModConfig> config = EasyConfig.holder(MyModConfig.class)
 *     .modId("mymod")
 *     .create();
 *
 * // Thread-safe, strict about everything, custom directory, reacting to changes.
 * AsyncConfigHolder<MyModConfig> strict = EasyConfig.holder(MyModConfig.class)
 *     .modId("mymod")
 *     .baseDir(Platform.getConfigDir().resolve("mymod"))
 *     .failurePolicy(FailurePolicy.STRICT)
 *     .onChange(state -> hudRenderer.setScale(state.hudScale))
 *     .createAsync();
 * }</pre>
 *
 * <p>Defaults resolve lazily when you call a create method: {@link FailurePolicy#FALLBACK} for
 * read, write, and update, {@link Platform#getConfigDir()}, and a tree round-trip cloner. The
 * file format is not set here — it belongs to the config class, on {@link Config#format()}.
 *
 * <p>Creating touches disk: it loads the persisted state and writes it back, so the config files
 * always exist afterwards and the holder starts in sync with them.
 *
 * @param <T> the root config type
 */
public final class ConfigBuilder<T> {

    private final Class<T> type;
    private final List<Consumer<T>> changeListeners = new ArrayList<>();
    private String modId;
    private Path baseDirectory;
    private FailurePolicy readFailurePolicy;
    private FailurePolicy writeFailurePolicy;
    private FailurePolicy updateFailurePolicy;
    private StateCloner<T> stateCloner;
    private HolderImplementation implementation;

    ConfigBuilder(Class<T> type) {
        if (type == null) {
            throw ConfigScope.unknown().exception(ConfigError.BUILD_REQUIRED_ARGUMENT, "type");
        }
        this.type = type;
    }

    /**
     * Sets the owning mod id used in failure messages and logging.
     *
     * <p>Required. The id becomes the config scope, prefixes every failure message as
     * {@code [modId]}, and names the logger.
     *
     * <p>It is <em>not</em> part of the resolved file path.
     *
     * @param modId the owning mod id; must not be {@code null} or blank
     * @return this builder
     * @throws EasyConfigException with {@link ConfigError#BUILD_REQUIRED_ARGUMENT} if {@code modId} is {@code null} or blank
     */
    public ConfigBuilder<T> modId(String modId) {
        this.modId = required(modId, "modId");
        return this;
    }

    /**
     * Overrides the directory that contains this mod's config files.
     *
     * <p>Parsed with {@link Path#of(String, String...)}, then made absolute and normalized.
     * Directories only — the file name always comes from {@link Config#name()}. Prefer
     * {@link #baseDir(Path)} when a {@link Path} is already in hand.
     *
     * <pre>{@code
     * .baseDir("config/mymod")   // config/mymod/<@Config.path() dirs>/<@Config.name()>.json5
     * }</pre>
     *
     * @param baseDir the directory string; must not be {@code null} or blank
     * @return this builder
     * @throws EasyConfigException with {@link ConfigError#BUILD_REQUIRED_ARGUMENT} if {@code baseDir} is {@code null} or blank
     * @throws EasyConfigException with {@link ConfigError#INVALID_CONFIG_PATH} if {@code baseDir} cannot be parsed as a path
     * @see #baseDir(Path)
     */
    public ConfigBuilder<T> baseDir(String baseDir) {
        String value = required(baseDir, "baseDir");
        try {
            return baseDir(Path.of(value));
        } catch (InvalidPathException ex) {
            throw scope().exception(ConfigError.INVALID_CONFIG_PATH, ex, type.getName(), value);
        }
    }

    /**
     * Overrides the directory that contains this mod's config files.
     *
     * <p>Made absolute and normalized when set, so a later change to the process working
     * directory cannot move the config root. Omit this to use {@link Platform#getConfigDir()},
     * which is {@code config/} in a normal game install. The {@link Config#path()} directories
     * are then appended to it, and the {@link Config#name()} file inside those:
     *
     * <pre>{@code
     * // with @Config(name = "mymod")
     * .baseDir(Platform.getConfigDir().resolve("mymod"))   // config/mymod/mymod.json5
     * .baseDir(tempDir)                                    // isolated root, useful in tests
     * }</pre>
     *
     * <p>Unlike {@link #baseDir(String)}, this overload accepts a {@link Path} that is already
     * parsed, so it cannot throw {@link ConfigError#INVALID_CONFIG_PATH}.
     *
     * @param baseDir the directory; must not be {@code null}
     * @return this builder
     * @throws EasyConfigException with {@link ConfigError#BUILD_REQUIRED_ARGUMENT} if {@code baseDir} is {@code null}
     * @see #baseDir(String)
     */
    public ConfigBuilder<T> baseDir(Path baseDir) {
        if (baseDir == null) {
            throw scope().exception(ConfigError.BUILD_REQUIRED_ARGUMENT, "baseDir");
        }
        this.baseDirectory = baseDir.toAbsolutePath().normalize();
        return this;
    }

    /**
     * Registers a listener notified whenever the published state changes.
     *
     * <pre>{@code
     * EasyConfig.holder(MyModConfig.class)
     *     .modId("mymod")
     *     .onChange(config -> hudRenderer.setScale(config.hudScale))
     *     .onChange(config -> LOGGER.info("config changed"))
     *     .create();
     * }</pre>
     *
     * <p>Listeners fire after an accepted update, an accepted reset, and a completed load — in
     * registration order, and only once the change is visible through
     * {@link ConfigHolder#data()}. A rejected update fires nothing, and neither does a load that
     * fell back to defaults because the file could not be read. Neither does the load this
     * builder performs while creating the holder: nothing has changed yet at that point, and no
     * listener could be attached to a holder that does not exist.
     *
     * <p>The listener receives the published state, the same shared instance {@code data()}
     * returns. Read it; do not mutate it, and do not keep the reference, because the next change
     * publishes a different object. Call {@link ConfigHolder#copy()} if you need to hold on to
     * values.
     *
     * <p>Listeners run on whichever thread performed the change, which for
     * {@link #createAsync()} is the config worker. Never call a blocking
     * holder method from inside one — that is reported as
     * {@link ConfigError#BLOCKING_CALL_ON_CONFIG_THREAD}. A listener that throws is reported as
     * {@link ConfigError#CHANGE_LISTENER_FAILED} and skipped, so it cannot fail the operation or
     * stop the listeners after it.
     *
     * @param listener callback invoked with the new published state, or {@code null} to register
     *                 nothing
     * @return this builder
     */
    public ConfigBuilder<T> onChange(Consumer<T> listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
        return this;
    }

    /**
     * Chooses how load, parse, and load-time validation failures behave.
     *
     * <p>{@link FailurePolicy#STRICT} throws. {@link FailurePolicy#FALLBACK} logs and restores
     * defaults, backing up malformed files first. Defect errors always propagate.
     *
     * @param readFailurePolicy the read policy, or {@code null} for {@link FailurePolicy#FALLBACK}
     * @return this builder
     */
    public ConfigBuilder<T> readFailurePolicy(FailurePolicy readFailurePolicy) {
        this.readFailurePolicy = readFailurePolicy;
        return this;
    }

    /**
     * Chooses how save failures behave.
     *
     * <p>{@link FailurePolicy#STRICT} throws. {@link FailurePolicy#FALLBACK} logs and leaves
     * memory unchanged; the last successfully written file stays on disk. Defect errors always
     * propagate.
     *
     * @param writeFailurePolicy the write policy, or {@code null} for {@link FailurePolicy#FALLBACK}
     * @return this builder
     */
    public ConfigBuilder<T> writeFailurePolicy(FailurePolicy writeFailurePolicy) {
        this.writeFailurePolicy = writeFailurePolicy;
        return this;
    }

    /**
     * Chooses how validation failures during updates behave.
     *
     * <p>{@link FailurePolicy#STRICT} throws. {@link FailurePolicy#FALLBACK} logs and discards
     * the candidate, leaving published state unchanged. Defect errors always propagate.
     *
     * @param updateFailurePolicy the update policy, or {@code null} for {@link FailurePolicy#FALLBACK}
     * @return this builder
     */
    public ConfigBuilder<T> updateFailurePolicy(FailurePolicy updateFailurePolicy) {
        this.updateFailurePolicy = updateFailurePolicy;
        return this;
    }

    /**
     * Sets the same failure policy for read, write, and update at once.
     *
     * <p>Shorthand for calling {@link #readFailurePolicy}, {@link #writeFailurePolicy}, and
     * {@link #updateFailurePolicy} with the same value.
     *
     * @param policy the policy to apply to all three operations, or {@code null} to reset all
     *               three to {@link FailurePolicy#FALLBACK}
     * @return this builder
     */
    public ConfigBuilder<T> failurePolicy(FailurePolicy policy) {
        this.readFailurePolicy = policy;
        this.writeFailurePolicy = policy;
        this.updateFailurePolicy = policy;
        return this;
    }

    /**
     * Replaces the deep-copy strategy used for candidates and published state.
     *
     * <p>A custom cloner must return an independent object whose later mutations cannot reach
     * the source instance; a hand-written copy is the fastest way to satisfy that:
     *
     * <pre>{@code
     * .stateCloner(new MyModConfigCloner())
     * }</pre>
     *
     * @param stateCloner the cloner for {@code T}, or {@code null} to keep the default
     *                    JSON round-trip cloner
     * @return this builder
     */
    public ConfigBuilder<T> stateCloner(StateCloner<T> stateCloner) {
        this.stateCloner = stateCloner;
        return this;
    }

    /**
     * Creates a holder that runs every operation inline on the calling thread.
     *
     * <p>The right choice for single-threaded setups, tests, and mod initialization: no worker
     * thread is started, so nothing outlives the holder and failures surface on the exact stack
     * that caused them. If your config is read or written from more than one thread, use
     * {@link #createAsync()} or {@link #createImmutable()} instead.
     *
     * <p>Building touches disk. It resolves file paths, rejects invalid config models, registers
     * extension validators, and validates defaults; then it reads what is on disk and writes the
     * accepted state back. Every file the root owns exists once this returns, and the holder
     * starts in sync with disk. A first run gets its defaults written out; an existing valid file
     * seeds the holder; an invalid one is moved aside under a fallback read policy and replaced
     * with defaults.
     *
     * @return a ready-to-use holder for {@code T}; never {@code null}
     * @throws EasyConfigException if the required
     *         mod id is missing, the model violates EasyConfig rules, two config types resolve to
     *         one file, defaults fail validation, or a strict read or write policy refuses the
     *         build-time load or write
     */
    public ConfigHolder<T> create() {
        this.implementation = HolderImplementation.SIMPLE;
        return HolderFactory.create(settings());
    }

    /**
     * Creates a thread-safe holder backed by a dedicated config worker.
     *
     * <p>The only mutable option that is safe to share across threads. Loads, saves, validators,
     * hooks, and mutators all run on the worker; blocking methods submit and join, while the
     * {@code *Async} methods on the returned {@link AsyncConfigHolder} hand back the future
     * directly. Reading {@link ConfigHolder#data()} never schedules work.
     *
     * <p>All build-time effects described on {@link #create()} apply here too.
     *
     * @return a ready-to-use async holder for {@code T}; never {@code null}
     * @throws EasyConfigException under the same conditions as {@link #create()}
     */
    public AsyncConfigHolder<T> createAsync() {
        this.implementation = HolderImplementation.ASYNC;
        return (AsyncConfigHolder<T>) HolderFactory.create(settings());
    }

    /**
     * Creates a holder that loads once here and refuses every later mutation.
     *
     * <p>For config a mod reads but never writes at runtime. {@code data()} keeps returning the
     * state captured during this call, which makes it safe to share freely without copying.
     * {@code update} and {@code reset} are refused through the update failure policy: they throw
     * under {@link FailurePolicy#STRICT} and return a rejection under
     * {@link FailurePolicy#FALLBACK}. {@code load} and {@code save} still work — a reload replaces
     * the published state wholesale rather than mutating it.
     *
     * <p>All build-time effects described on {@link #create()} apply here too.
     *
     * @return a ready-to-use immutable holder for {@code T}; never {@code null}
     * @throws EasyConfigException under the same conditions as {@link #create()}
     */
    public ConfigHolder<T> createImmutable() {
        this.implementation = HolderImplementation.IMMUTABLE;
        return HolderFactory.create(settings());
    }

    /**
     * Resolves defaults into the immutable settings this builder describes.
     *
     * @return resolved settings; never {@code null}
     * @throws EasyConfigException with {@link ConfigError#BUILD_REQUIRED_ARGUMENT} if {@link #modId(String)} was never given a non-blank value
     */
    ConfigSettings<T> settings() {
        this.modId = required(modId, "modId");
        return new ConfigSettings<>(
            type,
            scope(),
            orElseGet(baseDirectory, Platform::getConfigDir),
            orElseGet(readFailurePolicy, () -> FailurePolicy.FALLBACK),
            orElseGet(writeFailurePolicy, () -> FailurePolicy.FALLBACK),
            orElseGet(updateFailurePolicy, () -> FailurePolicy.FALLBACK),
            orElseGet(stateCloner, () -> new StateClonerImplementation<>(type)),
            orElseGet(implementation, () -> HolderImplementation.SIMPLE),
            List.copyOf(changeListeners)
        );
    }

    private ConfigScope scope() {
        return modId == null ? ConfigScope.unknown() : new ConfigScope(modId);
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw scope().exception(ConfigError.BUILD_REQUIRED_ARGUMENT, name);
        }
        return value;
    }

    private static <V> V orElseGet(V value, Supplier<? extends V> fallback) {
        return value != null ? value : Objects.requireNonNull(fallback.get(), "fallback");
    }
}
