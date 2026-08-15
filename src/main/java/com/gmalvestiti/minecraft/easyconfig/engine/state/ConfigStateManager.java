package com.gmalvestiti.minecraft.easyconfig.engine.state;

import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;

/**
 * Stores and isolates the canonical and published config state for one holder.
 *
 * <p>The canonical state is the authoritative value used by load, save, and update
 * operations. The published value is what {@code ConfigHolder#data()} returns; callers must
 * treat it as read-only. Implementations choose the storage model: volatile slots for async
 * use, plain fields for single-threaded use, or final fields for immutable holders.
 *
 * <p><strong>The two slots are always equal but never aliased.</strong> Because
 * {@link #published()} returns a reference rather than a copy, and config objects are
 * mutable, a caller can write through the reference it receives. Keeping a separate
 * canonical slot ensures such writes can never reach disk or seed the next update.
 * Implementations must never assign both slots to the same object.
 *
 * <p>{@link #published()} is the read hot path and must not allocate, lock, or deep-copy.
 * Mutation paths call {@link #copyOfCanonical()} once per attempt, validate the result,
 * then install it with {@link #replaceState(Object)}:
 *
 * <pre>{@code
 * T candidate = stateManager.copyOfCanonical();
 * consumer.accept(candidate);   // caller mutates the private copy
 * validate(candidate);
 * stateManager.replaceState(candidate);
 * }</pre>
 *
 * @param <T> config root type stored by this manager
 */
public interface ConfigStateManager<T> {

    /**
     * Returns the state currently visible to {@code ConfigHolder#data()} callers.
     *
     * @return the shared published reference; never {@code null}; callers must not mutate it
     */
    T published();

    /**
     * Returns the canonical state used by load, save, and validation.
     *
     * <p>This is the value {@code save} serializes; it must not be exposed to callers
     * outside the holder.
     *
     * @return the trusted state; never {@code null}
     */
    T canonical();

    /**
     * Returns a deep copy of the canonical state that the caller may mutate freely.
     *
     * <p>Intentionally expensive: runs the configured cloner every time. Update paths call
     * this once per attempt to obtain a private candidate.
     *
     * @return an independent copy of canonical state; never {@code null}
     */
    T copyOfCanonical();

    /**
     * Installs a validated candidate as the new canonical and published state.
     *
     * <p>Canonical must become {@code next} exactly; published must be an independent copy so
     * that callers holding the old published reference cannot reach or corrupt canonical.
     * Async implementations must ensure the published write is visible to other threads.
     * The immutable implementation always throws.
     *
     * @param next validated state to install; must not be {@code null}
     * @throws EasyConfigException with {@link ConfigError#HOLDER_OPERATION_UNSUPPORTED}
     *     when the holder implementation does not allow replacement
     */
    void replaceState(T next);
}
