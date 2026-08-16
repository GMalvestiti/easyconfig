package com.gmalvestiti.minecraft.easyconfig.exception;

/**
 * Defines the error codes carried by every {@link EasyConfigException}.
 *
 * <p>Callers branch on these enum values rather than on exception subtypes:
 *
 * <pre>{@code
 * catch (EasyConfigException failure) {
 *     switch (failure.error()) {
 *         case MALFORMED_CONFIG_DATA -> LOGGER.warn("config file was reset");
 *         default -> throw failure;
 *     }
 * }
 * }</pre>
 *
 * <p>This enum is pure reference data: each constant owns a
 * {@link String#format(String, Object...)} {@link #template()} and a {@link #defect()} flag. The
 * template is formatted internally before the finished message reaches an exception.
 *
 * <p>{@link #defect()} separates recoverable runtime failures from mod or library bugs. Defects,
 * including {@link #UNEXPECTED_FAILURE}, are always logged and always propagated; no failure
 * policy may hide them.
 *
 * <p>New constants may be added in any minor release, so always include a {@code default} branch
 * when switching over this enum.
 */
public enum ConfigError {
    /** Signals that a root class has neither {@code @Config} nor {@code @ConfigGroup}. */
    MISSING_CONFIG_MARKER("Class %s must be annotated with @Config or @ConfigGroup"),
    /** Reserved for backward compatibility; no longer thrown for field access level since non-public @Config fields are now supported. May be used in future for other invalid field conditions. */
    INVALID_CONFIG_GROUP_FIELD("Field %s in %s is not a valid @Config group member"),
    /** Signals that a config group member cannot be assigned during load. */
    FINAL_CONFIG_GROUP_MEMBER("Field %s in %s must not be final because group loads replace members"),
    /** Signals that a {@code @Config} class directly references another {@code @Config} type. */
    CONFIG_REFERENCE_FORBIDDEN("Field %s in %s cannot reference another @Config type"),
    /** Signals that a config class cannot provide defaults through a no-arg constructor. */
    MISSING_DEFAULT_CONSTRUCTOR("Class %s must expose a no-arg constructor"),
    /** Signals that a group declares two fields with the same {@code @Config} type. */
    DUPLICATE_CONFIG_GROUP_MEMBER("Group %s declares more than one field of type %s"),
    /** Signals that a {@code @Config} name cannot be used as a file name. */
    INVALID_CONFIG_NAME("Config %s declares invalid name '%s'"),
    /** Signals that a config path annotation cannot be resolved safely. */
    INVALID_CONFIG_PATH("Config %s declares invalid path '%s'"),
    /** Signals that two configs resolve to the same file. */
    CONFLICTING_CONFIG_PATH("Config %s resolves to %s, which is already owned by %s"),
    /** Signals that reflection cannot read or write a config field. */
    REFLECTION_ACCESS("Unable to access field %s in class %s"),
    /** Signals that storage cannot read a config file. */
    IO_LOAD_FAILURE("Failed to load config from %s"),
    /** Signals that existing config data cannot be parsed. */
    MALFORMED_CONFIG_DATA("Malformed config data in %s"),
    /** Signals that storage cannot persist a config file. */
    IO_SAVE_FAILURE("Failed to save config to %s"),
    /** Signals that the async worker rejects work during JVM shutdown. */
    CONFIG_WORKER_STOPPED("Config worker is no longer accepting work; the JVM is shutting down"),
    /** Signals that a loaded or updated value violates the root's {@code validate} rules. */
    VALIDATION_FAILED("Validation failed for %s: %s"),
    /** Signals that an update tries to change a field declared {@code @ConfigEntry(restart = true)}. */
    RESTART_FIELD_CHANGED("Restart-only fields changed in %s: %s"),
    /** Signals that {@code validate} reports a violation with a blank id. */
    VALIDATOR_PRODUCED_BLANK_ID("Validation for %s produced a violation with a blank id", true),
    /** Signals that {@code validate} reports a null violation entry. */
    VALIDATOR_PRODUCED_NULL_VIOLATION("Validation for %s produced a null violation", true),
    /** Signals that {@code validate} throws instead of reporting violations. */
    VALIDATOR_FAILED("Validation for %s threw an exception: %s", true),
    /** Signals that a {@code ConfigExtension} hook throws. */
    EXTENSION_HOOK_FAILED("Config extension hook '%s' threw an exception: %s", true),
    /** Signals that a config lifecycle listener throws after an event is dispatched. */
    CHANGE_LISTENER_FAILED("Config change listener threw an exception: %s"),
    /** Signals that holder construction cannot complete. */
    INITIALIZATION_FAILED("Configuration initialization failed: %s"),
    /** Signals that a required builder argument is null or blank. */
    BUILD_REQUIRED_ARGUMENT("Required builder argument '%s' is missing"),
    /** Signals that a holder implementation refuses an operation it does not support. */
    HOLDER_OPERATION_UNSUPPORTED("The %s config holder does not support '%s'"),
    /** Signals that blocking APIs are called from the config worker thread. */
    BLOCKING_CALL_ON_CONFIG_THREAD(
        "Blocking config operation called from the config worker thread; use the *Async variant instead", true),
    /** Signals that a config task tried to queue more work onto the worker running it. */
    NESTED_CONFIG_OPERATION(
        "Config operation scheduled from inside a config task; the single worker would have to wait on itself",
        true),
    /** Signals that a raw {@link RuntimeException} escapes internal code. */
    UNEXPECTED_FAILURE("Unexpected failure during %s: %s", true);

    private final String template;
    private final boolean defect;

    ConfigError(String template) {
        this(template, false);
    }

    ConfigError(String template, boolean defect) {
        this.template = template;
        this.defect = defect;
    }

    /**
     * Returns the raw format template for this code.
     *
     * <p>The template is unformatted and carries no {@code [modId]} prefix;
     * both are applied internally when the library builds a failure.
     *
     * @return the {@link String#format(String, Object...)} template; never {@code null}
     */
    public String template() {
        return template;
    }

    /**
     * Returns whether the code represents a programming defect.
     *
     * <p>Defects cannot be repaired by falling back to defaults or skipping work, so they
     * are always logged and propagated regardless of the holder's configured failure policy.
     *
     * @return {@code true} when no policy may degrade this error
     */
    public boolean defect() {
        return defect;
    }
}
