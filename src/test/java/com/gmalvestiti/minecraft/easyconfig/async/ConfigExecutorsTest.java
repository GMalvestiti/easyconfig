package com.gmalvestiti.minecraft.easyconfig.async;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigExecutorsTest {

    @Test
    void testReturnsSingletonExecutorAndExecutesTasks() throws Exception {
        Executor first = ConfigExecutors.defaultExecutor();
        Executor second = ConfigExecutors.defaultExecutor();
        assertSame(first, second);

        CompletableFuture<String> future = CompletableFuture.supplyAsync(
            () -> Thread.currentThread().getName(),
            first
        );
        String threadName = future.get(5, TimeUnit.SECONDS);
        assertTrue(threadName.contains("easyconfig-io"));
    }

    @Test
    void testHidesTheExecutorServiceSoCallersCannotStopTheSharedWorker() {
        assertFalse(ConfigExecutors.defaultExecutor() instanceof ExecutorService);
    }

    @Test
    void testCreatesWorkerWithUncaughtExceptionHandler() throws Exception {
        Thread worker = (Thread) newWorker(() -> {
        });

        assertTrue(worker.isDaemon());
        assertTrue(worker.getName().contains("easyconfig-io"));
        worker.getUncaughtExceptionHandler().uncaughtException(worker, new RuntimeException("boom"));
    }

    @Test
    void testMarksTheWorkerOnlyOnceItStartsRunning() throws Exception {
        Thread neverStarted = (Thread) newWorker(() -> {
        });

        assertFalse(ConfigWorkerScope.isInsideTask());

        CompletableFuture<Boolean> insideTask =
            CompletableFuture.supplyAsync(ConfigWorkerScope::isInsideTask, ConfigExecutors.defaultExecutor());

        assertTrue(insideTask.get(5, TimeUnit.SECONDS),
            "a task on the shared worker must see itself as inside a config task");
        assertFalse(neverStarted.isAlive(),
            "constructing a worker must not claim the marker for a thread that never runs");
        assertFalse(ConfigWorkerScope.isInsideTask(),
            "the calling thread must never be seen as the config worker");
    }

    private static Object newWorker(Runnable task) throws Exception {
        Method factory = ConfigExecutors.class.getDeclaredMethod("newWorker", Runnable.class);
        factory.setAccessible(true);
        return factory.invoke(null, task);
    }
}

