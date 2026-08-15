package com.gmalvestiti.minecraft.easyconfig.holder;

import com.gmalvestiti.minecraft.easyconfig.api.EasyConfig;
import com.gmalvestiti.minecraft.easyconfig.api.FailurePolicy;
import com.gmalvestiti.minecraft.easyconfig.api.ConfigHolder;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleConfigHolderTest {

    @Test
    void testSupportsTheFullLifecycleInline(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir);

        assertEquals(1, holder.data().value);

        holder.update(cfg -> cfg.value = 4);
        assertEquals(4, holder.data().value);

        holder.updateAndSave(cfg -> cfg.value = 6);
        holder.update(cfg -> cfg.value = 8);
        holder.load();

        assertEquals(6, holder.data().value);
        assertTrue(holder.data().afterLoadCalled);
    }

    @Test
    void testHandsOutAnIsolatedCopyThatCannotReachPublishedState(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir);

        TestFixtures.ConfigWithExtension copy = holder.copy();
        copy.value = 99;

        assertNotSame(holder.data(), copy);
        assertEquals(1, holder.data().value, "mutating a copy must not publish");

        holder.update(cfg -> cfg.value = 5);
        assertEquals(99, copy.value, "an existing copy must not track later updates");
    }

    @Test
    void testAllowsBlockingCallsFromInsideAMutator(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir);

        holder.update(cfg -> {
            cfg.value = 2;
            holder.save();
        });

        assertEquals(2, holder.data().value);
    }

    @Test
    void testStillEnforcesValidationOnUpdates(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir);

        EasyConfigException failure = assertThrows(
            EasyConfigException.class,
            () -> holder.update(cfg -> cfg.value = -1)
        );

        assertEquals(ConfigError.VALIDATION_FAILED, failure.error());
        assertEquals(1, holder.data().value);
    }

    @Test
    void testFallsBackAndBacksUpMalformedDataOnLoad(@TempDir Path tempDir) throws Exception {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir);
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
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.STRICT);
        Path file = tempDir.resolve("with-extension.json5");
        Files.writeString(file, "{");

        EasyConfigException failure = assertThrows(EasyConfigException.class, holder::load);

        assertEquals(ConfigError.MALFORMED_CONFIG_DATA, failure.error());
        assertTrue(Files.exists(file));
        try (var entries = Files.list(tempDir)) {
            assertEquals(0, entries.filter(p -> p.getFileName().toString().contains(".corrupt-")).count());
        }
    }

    private static ConfigHolder<TestFixtures.ConfigWithExtension> holder(Path tempDir) {
        return holder(tempDir, FailurePolicy.FALLBACK);
    }

    private static ConfigHolder<TestFixtures.ConfigWithExtension> holder(Path tempDir, FailurePolicy readPolicy) {
        return EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .readFailurePolicy(readPolicy)
            .updateFailurePolicy(FailurePolicy.STRICT)
            .create();
    }
}

