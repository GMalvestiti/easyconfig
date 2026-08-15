package com.gmalvestiti.minecraft.easyconfig.async;

import com.gmalvestiti.minecraft.easyconfig.EasyConfigCommon;

import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Provides the shared single-threaded config worker.
 *
 * <p>Async holders submit lifecycle tasks here. One worker serializes loads, saves, hooks,
 * validators, and mutators across all async holders, so their read-modify-write operations
 * need no extra locking.
 *
 * <p>{@link ConfigWorkerScope} marks tasks running on this thread so blocking holder calls
 * from hooks, validators, or mutators can be rejected instead of deadlocking.
 */
public final class ConfigExecutors {

    private static final String WORKER_NAME = EasyConfigCommon.MOD_ID + "-io";
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    private static final ExecutorService DEFAULT_EXECUTOR =
        Executors.newSingleThreadExecutor(ConfigExecutors::newWorker);

    private static final Executor SHARED_VIEW = DEFAULT_EXECUTOR::execute;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ConfigExecutors::drainAndStop, WORKER_NAME + "-shutdown"));
    }

    private ConfigExecutors() {}

    public static Executor defaultExecutor() {
        return SHARED_VIEW;
    }

    private static Thread newWorker(Runnable runnable) {
        Thread thread = new Thread(() -> {
            ConfigWorkerScope.registerWorker(Thread.currentThread());
            runnable.run();
        }, WORKER_NAME);
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((worker, error) ->
            LoggerFactory.getLogger(EasyConfigCommon.MOD_ID).error("Uncaught error on {}", worker.getName(), error));
        return thread;
    }

    private static void drainAndStop() {
        DEFAULT_EXECUTOR.shutdown();
        try {
            if (!DEFAULT_EXECUTOR.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                DEFAULT_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException ex) {
            DEFAULT_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
