package com.gmalvestiti.minecraft.easyconfig.api;

import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Operates on one config root after {@link ConfigBuilder#create()},
 * {@link ConfigBuilder#createImmutable()} completes.
 *
 * <p>Read the published state through {@link #data()} and treat it as read-only. Take a
 * {@link #copy()} when you need an object you may mutate, or a reading that cannot shift
 * underneath you. Change values through {@link #update(Consumer)} or
 * {@link #updateAndSave(Consumer)}, which mutate a private candidate, validate it, and publish
 * only accepted results:
 *
 * <pre>{@code
 * MyModConfig shared = holder.data();   // cheap, shared, read-only
 * MyModConfig mine = holder.copy();     // deep copy the caller owns
 *
 * holder.update(config -> config.showHints = false);   // publish in memory
 * holder.updateAndSave(config -> config.hudScale = 3); // publish and write
 * }</pre>
 *
 * <p>Both update methods return an {@link UpdateResult}. A strict update policy throws on
 * rejection, so the result is always {@link UpdateResult.Published}; a fallback policy returns
 * quietly, making the result the only way to learn which rules failed:
 *
 * <pre>{@code
 * UpdateResult result = holder.updateAndSave(config -> config.hudScale = 99);
 * if (!result.accepted()) {
 *     result.violations().forEach(v -> LOGGER.warn("{}: {}", v.id(), v.message()));
 * }
 * }</pre>
 *
 * <p>All methods wait and throw {@link EasyConfigException} directly on failure. For a
 * non-blocking API, obtain an {@link AsyncConfigHolder} through {@link ConfigBuilder#createAsync()}
 * — it adds {@code *Async} variants that return {@link CompletableFuture}.
 *
 * <p>The selected {@link HolderImplementation} decides where lifecycle work runs:
 * <ul>
 *   <li>{@code ASYNC} (via {@link ConfigBuilder#createAsync()}): queues work on the shared config
 *       worker; safe across threads.</li>
 *   <li>{@code SIMPLE}: runs inline on the caller's thread; not thread-safe.</li>
 *   <li>{@code IMMUTABLE}: loads once at build time and refuses load/update through the
 *       relevant failure policy.</li>
 * </ul>
 *
 * <p>Not intended for implementation outside EasyConfig: methods may be added in any minor
 * release. Build holders through {@code EasyConfig.holder(...)}, and wrap this type behind an
 * interface you own if you need a seam for testing.
 *
 * @param <T> the root config type
 */
public interface ConfigHolder<T> {

    /**
     * Returns the currently published state.
     *
     * @return the shared state instance; never {@code null}; treat it as read-only
     */
    T data();

    /**
     * Returns a private deep copy of the current state.
     *
     * <p>Unlike {@link #data()}, the returned object belongs to the caller: mutating it affects
     * nothing the holder tracks, and it cannot change underneath you while another thread loads
     * or updates.
     *
     * <pre>{@code
     * MyModConfig staged = holder.copy();
     * staged.hudScale = 4;                                  // nothing published yet
     * holder.updateAndSave(config -> config.hudScale = staged.hudScale);
     * }</pre>
     *
     * <p>Each call runs the configured cloner, so read through {@link #data()} on hot paths and
     * copy only when isolation is actually needed.
     *
     * @return a fresh deep copy of the current state; never {@code null}
     */
    T copy();

    /**
     * Re-reads persisted state and publishes it when accepted.
     *
     * <p>{@link ConfigBuilder#create()} already performed the initial load, so this is for
     * picking up a file edited after startup. A failed read is handled by the read policy:
     * {@code FALLBACK} moves a malformed or invalid file aside and restores defaults in memory,
     * leaving the file to be rewritten by the next save; {@code STRICT} throws.
     *
     * @throws EasyConfigException when the read policy is strict and loading, parsing, or load-time validation fails;
     *     also when a defect error occurs
     */
    void load();

    /**
     * Mutates a private candidate and publishes it if validation accepts the result.
     *
     * <p>Never writes to disk — use {@link #updateAndSave(Consumer)} for that. Under a fallback
     * update policy a rejected candidate is logged and discarded, the previous state stays
     * published, and the failed rules come back on the result.
     *
     * <pre>{@code
     * holder.update(config -> config.showHints = false);
     * }</pre>
     *
     * @param mutator edits a private candidate copy; must not be {@code null}
     * @return {@link UpdateResult.Published} when the candidate became the new state,
     *         {@link UpdateResult.Rejected} only when a fallback update policy discarded it;
     *         never {@code null}
     * @throws EasyConfigException when the update policy is strict and validation rejects the candidate;
     *     also when the mutator is {@code null} or another defect error occurs
     */
    UpdateResult update(Consumer<T> mutator);

    /**
     * Mutates, validates, publishes, and saves one accepted candidate.
     *
     * <p>The state written to disk is the state published to readers. A candidate rejected
     * under a fallback update policy is neither published nor saved, and the failed rules come
     * back on the result.
     *
     * <pre>{@code
     * holder.updateAndSave(config -> config.hudScale = 3);   // memory and config/mymod.json5
     * }</pre>
     *
     * @param mutator edits a private candidate copy; must not be {@code null}
     * @return {@link UpdateResult.Published} when the candidate was published and saved,
     *         {@link UpdateResult.Rejected} only when a fallback update policy discarded it;
     *         never {@code null}
     * @throws EasyConfigException when the update or write policy is strict and the operation fails;
     *     also when the mutator is {@code null} or another defect error occurs
     */
    UpdateResult updateAndSave(Consumer<T> mutator);

    /**
     * Publishes the config class's declared defaults, discarding every current value.
     *
     * <p>The defaults are the ones a fresh {@code new MyModConfig()} carries.
     * Nothing is written to disk — pair it with {@link #save()}, or call
     * {@link #resetAndSave()} to do both.
     *
     * <p>Fields marked {@code @ConfigEntry(restart = true)} keep the value the game started
     * with, since they cannot change until the next launch. Everything else goes back to its
     * default.
     *
     * <pre>{@code
     * holder.resetAndSave();   // back to the shipped defaults, on disk
     * }</pre>
     *
     * <p>Governed by the update policy, exactly like {@link #update(Consumer)}. A rejection here
     * means the config class's own defaults fail its own validators, which is a mod defect worth
     * fixing rather than a user error.
     *
     * @return {@link UpdateResult.Published} when the defaults were published,
     *         {@link UpdateResult.Rejected} only when a fallback update policy discarded them;
     *         never {@code null}
     * @throws EasyConfigException when the update policy is strict and the defaults are rejected,
     *     when the config class cannot be instantiated, or when another defect error occurs
     */
    UpdateResult reset();

    /**
     * Publishes the declared defaults and saves them.
     *
     * <p>The state written to disk is the state published to readers. Defaults rejected under a
     * fallback update policy are neither published nor saved.
     *
     * @return {@link UpdateResult.Published} when the defaults were published and saved,
     *         {@link UpdateResult.Rejected} only when a fallback update policy discarded them;
     *         never {@code null}
     * @throws EasyConfigException when the update or write policy is strict and the operation
     *     fails, or when a defect error occurs
     */
    UpdateResult resetAndSave();

    /**
     * Saves the current state to storage.
     *
     * @throws EasyConfigException when the write policy is strict and persistence fails; also when a defect error occurs
     */
    void save();
}
