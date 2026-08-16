package com.gmalvestiti.minecraft.easyconfig.holder;

import com.gmalvestiti.minecraft.easyconfig.api.AsyncConfigHolder;
import com.gmalvestiti.minecraft.easyconfig.api.EasyConfig;
import com.gmalvestiti.minecraft.easyconfig.api.FailurePolicy;
import com.gmalvestiti.minecraft.easyconfig.api.UpdateResult;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncConfigHolderTest {

    @Test
    void testLoadsSavesAndUpdatesThroughTheWorker(@TempDir Path tempDir) {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.STRICT);

        assertTrue(holder.update(config -> config.value = 7).accepted());
        holder.save();

        assertTrue(Files.exists(tempDir.resolve("with-extension.json5")));

        holder.update(config -> config.value = 42);
        holder.load();

        assertEquals(7, holder.data().value, "load must restore what save persisted");
    }

    @Test
    void testCompletesAsyncOperationsWithoutBlockingTheCaller(@TempDir Path tempDir) {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.STRICT);

        CompletableFuture<UpdateResult> pending = holder.updateAndSaveAsync(config -> config.value = 12);

        assertTrue(pending.join().accepted());
        assertEquals(12, holder.data().value);

        holder.saveAsync().join();
        holder.loadAsync().join();
        assertEquals(12, holder.data().value);
    }

    @Test
    void testKeepsPublishedStateIsolatedFromCallerMutation(@TempDir Path tempDir) {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.STRICT);

        TestFixtures.ConfigWithExtension copy = holder.copy();
        copy.value = 99;

        assertNotSame(holder.data(), copy);
        assertEquals(1, holder.data().value, "mutating a copy must not publish");
    }

    @Test
    void testRejectsUpdatesThatFailValidation(@TempDir Path tempDir) {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.STRICT);

        CompletableFuture<UpdateResult> failed = holder.updateAsync(config -> config.value = -1);

        assertThrows(CompletionException.class, failed::join);
        assertTrue(failed.isCompletedExceptionally());

        EasyConfigException failure = assertThrows(
            EasyConfigException.class,
            () -> holder.update(config -> config.value = -1)
        );

        assertEquals(ConfigError.VALIDATION_FAILED, failure.error());
        assertEquals(1, holder.data().value, "a rejected candidate must not reach published state");
    }

    @Test
    void testReportsRejectionsAsAValueUnderFallbackPolicy(@TempDir Path tempDir) {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.FALLBACK);

        UpdateResult result = holder.updateAndSave(config -> config.value = -1);

        assertInstanceOf(UpdateResult.Rejected.class, result);
        assertFalse(result.accepted());
        assertEquals(List.of("nonNegative"), result.violations().stream().map(v -> v.id()).toList());
        assertEquals(1, holder.data().value);
    }

    @Test
    void testRefusesNestedSchedulingFromInsideAWorkerTask(@TempDir Path tempDir) {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.STRICT);
        AtomicReference<CompletableFuture<Void>> nested = new AtomicReference<>();

        holder.update(config -> {
            config.value = 3;
            nested.set(holder.saveAsync());
        });

        EasyConfigException failure = assertThrows(
            EasyConfigException.class,
            () -> unwrap(nested.get())
        );
        assertEquals(ConfigError.NESTED_CONFIG_OPERATION, failure.error());
        assertEquals(3, holder.data().value, "the outer update must still publish");
    }

    @Test
    void testRefusesBlockingCallsFromInsideAWorkerTask(@TempDir Path tempDir) {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.STRICT);
        AtomicReference<EasyConfigException> caught = new AtomicReference<>();

        holder.update(config -> {
            config.value = 4;
            caught.set(assertThrows(EasyConfigException.class, holder::save));
        });

        assertEquals(ConfigError.BLOCKING_CALL_ON_CONFIG_THREAD, caught.get().error());
        assertEquals(4, holder.data().value);
    }

    @Test
    void testFallsBackAndBacksUpMalformedDataOnLoad(@TempDir Path tempDir) throws Exception {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder =
            holder(tempDir, FailurePolicy.STRICT, FailurePolicy.FALLBACK);
        Path file = tempDir.resolve("with-extension.json5");
        Files.writeString(file, "{");

        holder.load();

        assertEquals(1, holder.data().value);
        assertFalse(Files.exists(file), "a degraded load moves the file aside and keeps defaults in memory");
        try (var entries = Files.list(tempDir)) {
            assertEquals(1, entries.filter(p -> p.getFileName().toString().contains(".corrupt-")).count());
        }
    }

    @Test
    void testThrowsOnMalformedDataUnderStrictReadPolicy(@TempDir Path tempDir) throws Exception {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder =
            holder(tempDir, FailurePolicy.STRICT, FailurePolicy.STRICT);
        Path file = tempDir.resolve("with-extension.json5");
        Files.writeString(file, "{");

        EasyConfigException failure = assertThrows(EasyConfigException.class, holder::load);

        assertEquals(ConfigError.MALFORMED_CONFIG_DATA, failure.error());
        assertTrue(Files.exists(file));
        try (var entries = Files.list(tempDir)) {
            assertEquals(0, entries.filter(p -> p.getFileName().toString().contains(".corrupt-")).count());
        }
    }

    @Test
    void testRethrowsAnErrorRaisedOnTheWorkerUnchanged(@TempDir Path tempDir) {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.STRICT);
        Error raised = new Error("worker exploded");

        Error thrown = assertThrows(Error.class, () -> holder.update(config -> {
            throw raised;
        }));

        assertSame(raised, thrown, "an Error must reach the caller untouched, not wrapped");
    }

    @Test
    void testKeepsTheCompletionWrapperForNonRuntimeWorkerFailures(@TempDir Path tempDir) {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.STRICT);
        IOException raised = new IOException("worker io");

        CompletionException thrown = assertThrows(CompletionException.class, () -> holder.update(config -> {
            sneakyThrow(raised);
        }));

        assertSame(raised, thrown.getCause(),
            "a checked failure cannot be rethrown directly, so the wrapper must carry it");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable failure) throws T {
        throw (T) failure;
    }

    private static void unwrap(CompletableFuture<?> future) {
        try {
            future.join();
        } catch (CompletionException ex) {
            throw (RuntimeException) ex.getCause();
        }
    }

    private static AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder(Path tempDir, FailurePolicy updatePolicy) {
        return holder(tempDir, updatePolicy, FailurePolicy.FALLBACK);
    }

    private static AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder(
        Path tempDir,
        FailurePolicy updatePolicy,
        FailurePolicy readPolicy
    ) {
        return EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir)
            .readFailurePolicy(readPolicy)
            .updateFailurePolicy(updatePolicy)
            .createAsync();
    }
}

