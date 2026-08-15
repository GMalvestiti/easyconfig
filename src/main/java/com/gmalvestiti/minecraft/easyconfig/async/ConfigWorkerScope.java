package com.gmalvestiti.minecraft.easyconfig.async;

import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;

/**
 * Tracks whether the current thread is the shared config worker.
 *
 * <p>A blocking holder call submitted from a hook, validator, or mutator would enqueue more
 * work on the same single-threaded worker and wait forever. The marker lets blocking methods
 * detect and reject that call with {@link ConfigError#BLOCKING_CALL_ON_CONFIG_THREAD} instead
 * of deadlocking.
 */
public final class ConfigWorkerScope {

    private static volatile Thread workerThread;

    private ConfigWorkerScope() {}

    static void registerWorker(Thread worker) {
        workerThread = worker;
    }

    public static boolean isInsideTask() {
        return Thread.currentThread() == workerThread;
    }
}
