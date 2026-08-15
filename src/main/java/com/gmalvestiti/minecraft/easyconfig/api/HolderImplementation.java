package com.gmalvestiti.minecraft.easyconfig.api;

import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;

import java.util.concurrent.CompletionException;

/**
 * Chooses the thread model and publication strategy for a holder.
 *
 * <p>Not set directly — the {@link ConfigBuilder} method you finish with picks the constant for
 * you. This enum documents what each one actually does.
 *
 * <pre>{@code
 * ConfigHolder<MyModConfig> simple = EasyConfig.holder(MyModConfig.class)
 *     .modId("mymod")
 *     .create();            // SIMPLE
 *
 * AsyncConfigHolder<MyModConfig> async = EasyConfig.holder(MyModConfig.class)
 *     .modId("mymod")
 *     .createAsync();       // ASYNC, richer return type
 *
 * ConfigHolder<MyModConfig> frozen = EasyConfig.holder(MyModConfig.class)
 *     .modId("mymod")
 *     .createImmutable();   // IMMUTABLE
 * }</pre>
 *
 * <p>All three apply the same failure policies; they differ in where lifecycle work runs and
 * what memory-visibility guarantee readers get.
 *
 * <ul>
 *   <li>{@link #ASYNC}: shared worker, volatile publication, thread-safe.</li>
 *   <li>{@link #SIMPLE}: caller thread, plain field, server-thread confined.</li>
 *   <li>{@link #IMMUTABLE}: build-time load, final field, read-only.</li>
 * </ul>
 */
public enum HolderImplementation {

    /**
     * Runs lifecycle work on the shared config worker and publishes through volatile fields.
     *
     * <p>Selected by {@link ConfigBuilder#createAsync()}, which returns an
     * {@link AsyncConfigHolder} so the {@code *Async} methods are reachable. Blocking
     * methods submit to the worker and wait, unwrapping {@link CompletionException} so
     * callers still receive {@link EasyConfigException}. Calls made from inside a config task
     * are rejected with {@code BLOCKING_CALL_ON_CONFIG_THREAD} — use the {@code *Async}
     * variants there. A stopped worker fails with {@code CONFIG_WORKER_STOPPED}. Published
     * state is a cloned volatile snapshot, separate from the volatile canonical state.
     */
    ASYNC,

    /**
     * Runs lifecycle work inline on the calling thread and publishes through a plain field.
     *
     * <p>Selected by {@link ConfigBuilder#create()}. Safe for Minecraft server data as long as
     * every holder call comes from the server thread. Publishing performs no clone, because the
     * installed state is already a private copy. Has no {@code *Async} methods — use
     * {@link ConfigBuilder#createAsync()} when non-blocking submission is needed.
     *
     * <p><strong>Not thread-safe.</strong>
     */
    SIMPLE,

    /**
     * Freezes the state loaded during {@link ConfigBuilder#createImmutable()} in a final field.
     *
     * <p>Final-field publication makes reads safe from any thread. {@code update} and
     * {@code reset} are refused through the exception handler: {@code STRICT} throws
     * {@code HOLDER_OPERATION_UNSUPPORTED}, {@code FALLBACK} logs and returns a rejection.
     * {@code load} and {@code save} still run. Has no {@code *Async} methods.
     */
    IMMUTABLE;
}
