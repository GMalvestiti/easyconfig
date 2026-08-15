package com.gmalvestiti.minecraft.easyconfig.exception;

import com.gmalvestiti.minecraft.easyconfig.api.FailurePolicy;
import com.gmalvestiti.minecraft.easyconfig.api.spi.Violation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Result of an operation wrapped by {@link ConfigExceptionHandler}.
 *
 * <p>It is either {@link Completed} or {@link Degraded}. Under
 * {@link FailurePolicy#STRICT}, failures throw and you usually never see a degraded value.
 *
 * <p>Typical use:
 *
 * <pre>{@code
 * ConfigOutcome<MyConfig> read = handler.onRead(MyConfig.class, () -> storage.read(MyConfig.class));
 * MyConfig config = read.valueOr(MyConfig::new);
 *
 * ConfigOutcome<Void> write = handler.onWrite(MyConfig.class, () -> {
 *     storage.write(MyConfig.class, config);
 *     return null;
 * });
 *
 * if (write.degraded()) {
 *     logger.warn("Write failed: {}", write.failure().orElseThrow().getMessage());
 * }
 * }</pre>
 *
 * <p>Use {@link #completed()} / {@link #degraded()} to test status. Do not infer status from
 * {@link #value()} being empty, because a successful operation may still produce no value.
 *
 * @param <V> produced value type (or {@link Void} for no value)
 */
public sealed interface ConfigOutcome<V> {

    /** Creates a completed outcome. */
    static <V> ConfigOutcome<V> completed(V value) {
        return new Completed<>(value);
    }

    /** Creates a degraded outcome from a swallowed fallback failure. */
    static <V> ConfigOutcome<V> degraded(EasyConfigException failure) {
        return new Degraded<>(failure);
    }

    /** {@code true} when the guarded operation finished successfully. */
    boolean completed();

    /** {@code true} when fallback swallowed an expected failure. */
    default boolean degraded() {
        return !completed();
    }

    /** Produced value, if any. */
    Optional<V> value();

    /** Swallowed failure for degraded outcomes. */
    Optional<EasyConfigException> failure();

    /** Produced value, or {@code fallback.get()} when absent. */
    default V valueOr(Supplier<V> fallback) {
        return value().orElseGet(fallback);
    }

    /** Validation violations carried by the degraded failure, when present. */
    default List<Violation> violations() {
        return failure().map(EasyConfigException::violations).orElseGet(List::of);
    }

    /** Successful outcome. */
    record Completed<V>(V produced) implements ConfigOutcome<V> {

        @Override
        public boolean completed() {
            return true;
        }

        @Override
        public Optional<V> value() {
            return Optional.ofNullable(produced);
        }

        @Override
        public Optional<EasyConfigException> failure() {
            return Optional.empty();
        }
    }

    /** Fallback-swallowed failure outcome. */
    record Degraded<V>(EasyConfigException cause) implements ConfigOutcome<V> {

        public Degraded {
            Objects.requireNonNull(cause, "cause");
        }

        @Override
        public boolean completed() {
            return false;
        }

        @Override
        public Optional<V> value() {
            return Optional.empty();
        }

        @Override
        public Optional<EasyConfigException> failure() {
            return Optional.of(cause);
        }
    }
}
