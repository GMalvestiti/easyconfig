package com.gmalvestiti.minecraft.easyconfig.holder;

import com.gmalvestiti.minecraft.easyconfig.api.EasyConfig;
import com.gmalvestiti.minecraft.easyconfig.api.FailurePolicy;
import com.gmalvestiti.minecraft.easyconfig.api.ConfigHolder;
import com.gmalvestiti.minecraft.easyconfig.api.UpdateResult;
import com.gmalvestiti.minecraft.easyconfig.api.spi.Violation;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmutableConfigHolderTest {

    @Test
    void testLoadsOnceWhileBuilding(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("with-extension.json5"), "{\"value\":4}");

        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.FALLBACK);

        assertEquals(4, holder.data().value);
        assertTrue(holder.data().afterLoadCalled);
    }

    @Test
    void testFallsBackToDefaultsWhenNoFileExists(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.FALLBACK);

        assertEquals(1, holder.data().value);
    }

    @Test
    void testFallsBackAndBacksUpMalformedDataDuringInitialLoad(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("with-extension.json5");
        Files.writeString(file, "{");

        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.FALLBACK);

        assertEquals(1, holder.data().value);
        assertTrue(Files.readString(file).contains("\"value\": 1"),
            "the restored defaults must be persisted, not just held in memory");
        try (var entries = Files.list(tempDir)) {
            assertEquals(1, entries.filter(p -> p.getFileName().toString().contains(".corrupt-")).count());
        }
    }

    @Test
    void testThrowsWithoutBackingUpMalformedDataDuringInitialStrictLoad(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("with-extension.json5");
        Files.writeString(file, "{");

        EasyConfigException failure = assertThrows(EasyConfigException.class, () -> strictHolder(tempDir));

        assertEquals(ConfigError.MALFORMED_CONFIG_DATA, failure.error());
        assertTrue(Files.exists(file));
        try (var entries = Files.list(tempDir)) {
            assertEquals(0, entries.filter(p -> p.getFileName().toString().contains(".corrupt-")).count());
        }
    }

    @Test
    void testRestoresDefaultsWhenTheLoadedFileFailsValidation(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("with-extension.json5");
        Files.writeString(file, "{\"value\":-5}");

        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.FALLBACK);

        assertEquals(1, holder.data().value, "an invalid file must not become the frozen state");
        assertTrue(Files.readString(file).contains("\"value\": 1"),
            "the restored defaults must be persisted, not just held in memory");
        try (var entries = Files.list(tempDir)) {
            assertEquals(1, entries.filter(p -> p.getFileName().toString().contains(".corrupt-")).count(),
                "the rejected file must be kept aside so the user can recover their values");
        }
    }

    @Test
    void testPropagatesDefectsFromTheInitialLoadEvenUnderFallback(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("throwing-validator.json5"), "{\"value\":-1}");

        EasyConfigException failure = assertThrows(EasyConfigException.class, () -> EasyConfig
            .holder(TestFixtures.ThrowingValidatorConfig.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .readFailurePolicy(FailurePolicy.FALLBACK)
            .createImmutable());

        assertEquals(ConfigError.VALIDATOR_FAILED, failure.error(),
            "a defect must abort construction regardless of the read policy");
    }

    @Test
    void testHandsOutTheSameInstanceOnEveryRead(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.FALLBACK);

        assertSame(holder.data(), holder.data());
    }

    @Test
    void testStillCopiesEvenThoughItRefusesWrites(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = strictHolder(tempDir);

        TestFixtures.ConfigWithExtension copy = assertDoesNotThrow(holder::copy);
        copy.value = 42;

        assertNotSame(holder.data(), copy);
        assertEquals(1, holder.data().value);
    }

    @Test
    void testThrowsOnMutatingOperationsUnderStrictPolicies(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = strictHolder(tempDir);

        for (Executable refused : List.<Executable>of(
            holder::load,
            () -> holder.update(cfg -> cfg.value = 2),
            () -> holder.updateAndSave(cfg -> cfg.value = 2),
            holder::reset,
            holder::resetAndSave)) {
            assertEquals(
                ConfigError.HOLDER_OPERATION_UNSUPPORTED,
                assertThrows(EasyConfigException.class, refused).error()
            );
        }
        assertEquals(1, holder.data().value);
    }

    @Test
    void testSkipsMutatingOperationsUnderFallbackPolicies(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = EasyConfig
            .holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .createImmutable();

        holder.load();
        UpdateResult update = holder.update(cfg -> cfg.value = 2);
        UpdateResult updateAndSave = holder.updateAndSave(cfg -> cfg.value = 2);
        UpdateResult reset = holder.reset();
        UpdateResult resetAndSave = holder.resetAndSave();

        assertEquals(1, holder.data().value);
        for (UpdateResult result : List.of(update, updateAndSave, reset, resetAndSave)) {
            assertInstanceOf(UpdateResult.Rejected.class, result);
            assertEquals(
                List.of("holder.immutable"),
                result.violations().stream().map(Violation::id).toList(),
                "a refusal must explain itself instead of returning an empty rejection");
        }
    }

    @Test
    void testStillSupportsSaving(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, FailurePolicy.FALLBACK);

        holder.save();

        assertTrue(Files.exists(tempDir.resolve("with-extension.json5")));
    }

    private static ConfigHolder<TestFixtures.ConfigWithExtension> strictHolder(Path tempDir) {
        return EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .readFailurePolicy(FailurePolicy.STRICT)
            .updateFailurePolicy(FailurePolicy.STRICT)
            .createImmutable();
    }

    private static ConfigHolder<TestFixtures.ConfigWithExtension> holder(Path tempDir, FailurePolicy policy) {
        return EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .readFailurePolicy(policy)
            .createImmutable();
    }
}

