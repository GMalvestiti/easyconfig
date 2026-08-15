package com.gmalvestiti.minecraft.easyconfig.exception;

import com.gmalvestiti.minecraft.easyconfig.api.FailurePolicy;
import com.gmalvestiti.minecraft.easyconfig.shared.ConfigOperation;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Applies failure policies at the holder boundary.
 *
 * <p>Every lower-level collaborator throws {@link EasyConfigException} and stops, never catching
 * or deciding whether to continue. This layer catches anticipated failures, applies the configured
 * read, write, or update policy, and chooses: log-and-degrade, log-and-skip, or rethrow. Raw
 * {@link RuntimeException}s are translated to {@code EasyConfigException} so callers never see a
 * foreign exception type from EasyConfig.
 *
 * <p>{@link FailurePolicy#STRICT} rethrows anticipated failures for all paths.
 * {@link FailurePolicy#FALLBACK} degrades: reads restore defaults (backing up malformed files),
 * writes keep the in-memory state, and updates discard the candidate. Defects —
 * {@link ConfigError#UNEXPECTED_FAILURE} and every other {@linkplain ConfigError#defect() defect
 * code} — are always logged at error level and always propagated, regardless of policy.
 *
 * <p>Each guarded method returns a {@link ConfigOutcome}, so a degraded run is observable the
 * same way for every operation family: a strict policy throws, a fallback policy returns
 * {@link ConfigOutcome#degraded(EasyConfigException) degraded} carrying the swallowed reason.
 * Callers that only need the happy path can ignore the outcome.
 *
 * <p>The policy is applied once, here at the holder boundary. Collaborators below — engine,
 * layouts, storage — throw and stop; the single exception is the group layout, which guards each
 * member read separately so one malformed member file cannot discard the whole group.
 *
 * <p>Diagnostics route through {@link ConfigScope}, which carries both identity and the log sink.
 * Instances are immutable and thread-safe after construction.
 */
public final class ConfigExceptionHandler {

    private final ConfigScope scope;
    private final FailurePolicy readPolicy;
    private final FailurePolicy writePolicy;
    private final FailurePolicy updatePolicy;
    private final Consumer<Class<?>> backupCorrupted;

    /**
     * Binds one scope, the three failure policies, and the corrupt-file backup action.
     *
     * @param scope mod scope used to create formatted failures; must not be {@code null}
     * @param readPolicy policy for {@link ConfigOperation#LOAD} and {@link ConfigOperation#READ};
     *                   must not be {@code null}
     * @param writePolicy policy for {@link ConfigOperation#SAVE} and {@link ConfigOperation#WRITE};
     *                    must not be {@code null}
     * @param updatePolicy policy for {@link ConfigOperation#UPDATE}; must not be {@code null}
     * @param backupCorrupted moves the malformed data of the given config class aside before
     *                        defaults replace it; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public ConfigExceptionHandler(
        ConfigScope scope,
        FailurePolicy readPolicy,
        FailurePolicy writePolicy,
        FailurePolicy updatePolicy,
        Consumer<Class<?>> backupCorrupted
    ) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.readPolicy = Objects.requireNonNull(readPolicy, "readPolicy");
        this.writePolicy = Objects.requireNonNull(writePolicy, "writePolicy");
        this.updatePolicy = Objects.requireNonNull(updatePolicy, "updatePolicy");
        this.backupCorrupted = Objects.requireNonNull(backupCorrupted, "backupCorrupted");
    }

    /**
     * Runs a read-side operation under the configured read policy.
     *
     * <p>{@code FALLBACK} logs the failure, backs up malformed data when it may still hold
     * recoverable user values, and returns a degraded outcome so the caller can restore defaults.
     * {@code STRICT} rethrows the failure.
     *
     * @param configType config class being read; must not be {@code null}; used for corrupt-file
     *                   backup and diagnostics
     * @param read operation to attempt; must not be {@code null}
     * @param <V> the value being read, normally the config type itself
     * @return the completed outcome carrying the read value, which is empty when storage held
     *     nothing to read; degraded when fallback chose default restoration
     * @throws EasyConfigException when the read policy is {@code STRICT}, the failure is a defect,
     *     or a raw runtime failure is translated to {@code ConfigError.UNEXPECTED_FAILURE}
     */
    public <V> ConfigOutcome<V> onRead(Class<V> configType, Supplier<V> read) {
        try {
            return ConfigOutcome.completed(read.get());
        } catch (RuntimeException ex) {
            EasyConfigException failure = expected(ConfigOperation.READ, ex);
            if (readPolicy == FailurePolicy.STRICT) {
                throw failure;
            }
            scope.logWarning(failure.rawMessage() + "; restoring defaults");
            backupIfCorrupt(configType, failure);
            return ConfigOutcome.degraded(failure);
        }
    }

    /**
     * Runs a write-side operation under the configured write policy.
     *
     * <p>{@code FALLBACK} logs the failure and leaves the current in-memory state untouched, so
     * the returned outcome is the only way to tell a persisted save from a swallowed one.
     * {@code STRICT} rethrows the failure.
     *
     * @param write operation to attempt; must not be {@code null}
     * @return the completed outcome when the write reached storage; degraded when fallback kept
     *     the in-memory state instead
     * @throws EasyConfigException when the write policy is {@code STRICT}, the failure is a
     *     defect, or a raw runtime failure is translated to {@code ConfigError.UNEXPECTED_FAILURE}
     */
    public ConfigOutcome<Void> onWrite(Runnable write) {
        try {
            write.run();
            return ConfigOutcome.completed(null);
        } catch (RuntimeException ex) {
            EasyConfigException failure = expected(ConfigOperation.WRITE, ex);
            if (writePolicy == FailurePolicy.STRICT) {
                throw failure;
            }
            scope.logError(failure.rawMessage() + "; keeping in-memory state", failure);
            return ConfigOutcome.degraded(failure);
        }
    }

    /**
     * Runs an update candidate under the configured update policy.
     *
     * <p>{@code FALLBACK} logs the failure and returns a degraded outcome; the caller must leave
     * the published state unchanged. The degraded outcome is the only way for a caller to observe
     * an anticipated failure that fallback swallowed, so an update path can hand the rejected
     * candidate's {@link ConfigOutcome#violations() violations} back to its own caller instead of
     * losing them to the log. {@code STRICT} rethrows the failure.
     *
     * @param update mutation, validation, and publish operation to attempt; must not be
     *               {@code null}
     * @param <V> the candidate type, normally the config type itself
     * @return the completed outcome carrying the accepted candidate; degraded when fallback
     *     rejected it
     * @throws EasyConfigException when the update policy is {@code STRICT}, the failure is a
     *     defect, or a raw runtime failure is translated to {@code ConfigError.UNEXPECTED_FAILURE}
     */
    public <V> ConfigOutcome<V> onUpdate(Supplier<V> update) {
        try {
            return ConfigOutcome.completed(update.get());
        } catch (RuntimeException ex) {
            EasyConfigException failure = expected(ConfigOperation.UPDATE, ex);
            if (updatePolicy == FailurePolicy.STRICT) {
                throw failure;
            }
            scope.logWarning(failure.rawMessage());
            return ConfigOutcome.degraded(failure);
        }
    }

    /**
     * Guards a top-level holder task from leaking foreign runtime exceptions.
     *
     * <p>Consults no failure policy and never degrades. Lets an existing
     * {@link EasyConfigException} escape unchanged and wraps any other {@link RuntimeException} as
     * {@code ConfigError.UNEXPECTED_FAILURE}.
     *
     * @param operation operation name used in the attributed failure; must not be {@code null}
     * @param action holder task to run; must not be {@code null}
     * @throws EasyConfigException when {@code action} throws one directly or when another runtime
     *     failure is translated to {@code ConfigError.UNEXPECTED_FAILURE}
     */
    public void runGuarded(ConfigOperation operation, Runnable action) {
        try {
            action.run();
        } catch (EasyConfigException failure) {
            throw failure;
        } catch (RuntimeException ex) {
            throw unexpected(operation, ex);
        }
    }

    /**
     * Applies the governing policy to an operation that cannot be performed.
     *
     * <p>Use for holder variants that refuse part of the lifecycle, such as an immutable holder
     * rejecting update or load. Nothing is attempted first; {@code failure} is already the
     * attributed reason. {@code STRICT} propagates it. {@code FALLBACK} logs and skips.
     *
     * @param operation selects the policy: {@code LOAD}/{@code READ} use read,
     *                  {@code SAVE}/{@code WRITE} use write, {@code UPDATE} uses update;
     *                  must not be {@code null}
     * @param failure attributed reason for refusal; must not be {@code null}
     * @return the degraded outcome carrying the refusal reason
     * @throws EasyConfigException when the governing policy is {@code STRICT} or the failure is a
     *     defect
     */
    public ConfigOutcome<Void> reject(ConfigOperation operation, EasyConfigException failure) {
        EasyConfigException degradable = expected(operation, failure);
        if (isStrict(operation)) {
            throw degradable;
        }
        scope.logWarning(degradable.rawMessage() + "; skipping");
        return ConfigOutcome.degraded(degradable);
    }

    /**
     * Selects the strictness check for an operation.
     */
    private boolean isStrict(ConfigOperation operation) {
        return switch (operation) {
            case LOAD, READ -> readPolicy == FailurePolicy.STRICT;
            case SAVE, WRITE -> writePolicy == FailurePolicy.STRICT;
            case UPDATE -> updatePolicy == FailurePolicy.STRICT;
        };
    }

    /**
     * Moves the config file aside before defaults overwrite it, when the failure indicates
     * user-written data may be recoverable.
     *
     * <p>Backup failures are logged and suppressed; they must not hide the original read failure.
     */
    private void backupIfCorrupt(Class<?> configType, EasyConfigException failure) {
        if (!shouldBackUpBeforeDefaults(failure.error())) {
            return;
        }
        try {
            backupCorrupted.accept(configType);
        } catch (RuntimeException backupFailure) {
            scope.logError(failure.rawMessage() + "; failed to back up the recoverable file", backupFailure);
        }
    }

    private static boolean shouldBackUpBeforeDefaults(ConfigError error) {
        return error == ConfigError.MALFORMED_CONFIG_DATA || error == ConfigError.VALIDATION_FAILED;
    }

    /**
     * Returns a policy-governed failure or throws a defect.
     *
     * <p>Raw runtime exceptions become {@link ConfigError#UNEXPECTED_FAILURE}. Defect codes are
     * logged and rethrown before a policy is consulted. Only anticipated
     * {@link EasyConfigException}s reach the caller.
     */
    private EasyConfigException expected(ConfigOperation operation, RuntimeException ex) {
        if (!(ex instanceof EasyConfigException failure)) {
            throw unexpected(operation, ex);
        }
        if (failure.defect()) {
            scope.logError(failure.rawMessage(), failure);
            throw failure;
        }
        return failure;
    }

    /**
     * Wraps and logs an unexpected runtime failure.
     */
    private EasyConfigException unexpected(ConfigOperation operation, RuntimeException ex) {
        EasyConfigException failure = scope.exception(
            ConfigError.UNEXPECTED_FAILURE, ex, operation.displayName(), String.valueOf(ex));
        scope.logError(failure.rawMessage(), ex);
        return failure;
    }
}
