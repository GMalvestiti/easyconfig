package com.gmalvestiti.minecraft.easyconfig.api;

import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.api.FailurePolicy;
import com.gmalvestiti.minecraft.easyconfig.api.AsyncConfigHolder;
import com.gmalvestiti.minecraft.easyconfig.api.ConfigHolder;
import com.gmalvestiti.minecraft.easyconfig.api.spi.Violation;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasyConfigTest {

    @Test
    void testSupportsLifecycleOperations(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .updateFailurePolicy(FailurePolicy.STRICT)
            .create();

        assertEquals(1, holder.data().value);

        holder.update(cfg -> cfg.value = 4);
        assertEquals(4, holder.data().value);

        holder.updateAndSave(cfg -> cfg.value = 6);
        assertEquals(6, holder.data().value);

        holder.update(cfg -> cfg.value = 2);
        holder.save();

        holder.update(cfg -> cfg.value = 8);
        holder.load();
        assertEquals(2, holder.data().value);
        assertTrue(holder.data().afterLoadCalled);
    }

    @Test
    void testSupportsAsyncLifecycleOperations(@TempDir Path tempDir) {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .updateFailurePolicy(FailurePolicy.STRICT)
            .createAsync();

        holder.updateAsync(cfg -> cfg.value = 3).join();
        holder.saveAsync().join();
        holder.updateAsync(cfg -> cfg.value = 9).join();
        holder.loadAsync().join();

        assertEquals(3, holder.data().value);
    }

    @Test
    void testRejectsInvalidDefaultsDuringConstruction(@TempDir Path tempDir) {
        ConfigBuilder<TestFixtures.InvalidDefaultsConfig> builder =
            EasyConfig.holder(TestFixtures.InvalidDefaultsConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString());

        EasyConfigException failure = assertThrows(EasyConfigException.class, builder::create);
        assertEquals(ConfigError.VALIDATION_FAILED, failure.error());
    }

    @Test
    void testRejectsInvalidMutations(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .updateFailurePolicy(FailurePolicy.STRICT)
            .create();

        EasyConfigException failure = assertThrows(
            EasyConfigException.class,
            () -> holder.update(cfg -> cfg.value = -1)
        );
        assertEquals(ConfigError.VALIDATION_FAILED, failure.error());
    }

    @Test
    void testReportsAMissingMutatorAsADefect(@TempDir Path tempDir) {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir);

        for (Executable blocking : List.<Executable>of(
            () -> holder.updateAndSave(null))) {
            assertEquals(ConfigError.UNEXPECTED_FAILURE, assertThrows(EasyConfigException.class, blocking).error());
        }
        for (Executable async : List.<Executable>of(
            () -> holder.updateAsync(null).join(),
            () -> holder.updateAndSaveAsync(null).join())) {
            assertThrows(CompletionException.class, async);
        }
    }

    @Test
    void testRejectsBlockingCallsFromTheConfigThread(@TempDir Path tempDir) {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir);

        CompletionException wrapper = assertThrows(
            CompletionException.class,
            () -> holder.updateAsync(cfg -> holder.save()).join()
        );
        assertTrue(wrapper.getCause() instanceof EasyConfigException failure
            && failure.error() == ConfigError.BLOCKING_CALL_ON_CONFIG_THREAD);
    }

    @Test
    void testRejectsNestedSchedulingSoAJoiningHookCannotDeadlockTheWorker(@TempDir Path tempDir) {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir);

        CompletionException wrapper = assertThrows(
            CompletionException.class,
            () -> holder.updateAsync(cfg -> holder.saveAsync().join()).join()
        );

        assertTrue(wrapper.getCause() instanceof EasyConfigException failure
            && failure.error() == ConfigError.UNEXPECTED_FAILURE);
        assertTrue(causeChainOf(wrapper).stream().anyMatch(cause ->
            cause instanceof EasyConfigException nested
                && nested.error() == ConfigError.NESTED_CONFIG_OPERATION));
    }

    private static List<Throwable> causeChainOf(Throwable failure) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            chain.add(current);
            if (current.getCause() == current) {
                break;
            }
        }
        return chain;
    }

    @Test
    void testRestoresDefaultsWhenStoredValuesFailValidation(@TempDir Path tempDir) throws Exception {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .create();
        holder.save();
        Files.writeString(tempDir.resolve("with-extension.json"), "{\"value\":-5}");

        holder.load();

        assertEquals(1, holder.data().value);
    }

    @Test
    void testPropagatesStoredValidationFailuresUnderStrictReads(@TempDir Path tempDir) throws Exception {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .readFailurePolicy(FailurePolicy.STRICT)
            .create();
        holder.save();
        Files.writeString(tempDir.resolve("with-extension.json"), "{\"value\":-5}");

        EasyConfigException failure = assertThrows(EasyConfigException.class, holder::load);
        assertEquals(ConfigError.VALIDATION_FAILED, failure.error());
    }

    @Test
    void testCancelsInvalidUpdatesUnderFallbackPolicy(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .create();

        UpdateResult rejected = holder.updateAndSave(cfg -> cfg.value = -1);

        assertEquals(1, holder.data().value);
        assertFalse(rejected.accepted());
        assertEquals(List.of("nonNegative"), rejected.violations().stream().map(Violation::id).toList());
    }

    @Test
    void testReturnsTheViolationsBehindEveryFallbackRejection(@TempDir Path tempDir) {
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .createAsync();

        assertTrue(holder.update(cfg -> cfg.value = 5).accepted());
        assertTrue(holder.updateAndSave(cfg -> cfg.value = 6).accepted());

        for (Supplier<UpdateResult> rejecting : List.<Supplier<UpdateResult>>of(
            () -> holder.update(cfg -> cfg.value = -1),
            () -> holder.updateAndSave(cfg -> cfg.value = -1),
            () -> holder.updateAsync(cfg -> cfg.value = -1).join(),
            () -> holder.updateAndSaveAsync(cfg -> cfg.value = -1).join())) {
            UpdateResult result = rejecting.get();
            assertInstanceOf(UpdateResult.Rejected.class, result);
            List<Violation> rejected = result.violations();
            assertEquals(1, rejected.size());
            assertEquals("nonNegative", rejected.getFirst().id());
            assertEquals("value must be >= 0", rejected.getFirst().message());
        }
        assertEquals(6, holder.data().value);
    }

    @Test
    void testCarriesTheViolationsOnStrictRejections(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .updateFailurePolicy(FailurePolicy.STRICT)
            .create();

        EasyConfigException failure = assertThrows(
            EasyConfigException.class,
            () -> holder.update(cfg -> cfg.value = -1)
        );

        assertEquals(ConfigError.VALIDATION_FAILED, failure.error());
        assertEquals(List.of("nonNegative"), failure.violations().stream().map(Violation::id).toList());
        assertThrows(UnsupportedOperationException.class, () -> failure.violations().clear());
    }

    @Test
    void testNeverDegradesABuggyValidatorEvenUnderFallbackPolicy(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ThrowingValidatorConfig> holder =
            EasyConfig.holder(TestFixtures.ThrowingValidatorConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .updateFailurePolicy(FailurePolicy.FALLBACK)
                    .create();

        EasyConfigException failure = assertThrows(EasyConfigException.class,
            () -> holder.update(cfg -> cfg.value = -1));

        assertEquals(ConfigError.VALIDATOR_FAILED, failure.error());
    }

    @Test
    void testCoversBuilderCustomizers(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .readFailurePolicy(FailurePolicy.FALLBACK)
            .writeFailurePolicy(FailurePolicy.FALLBACK)
            .updateFailurePolicy(FailurePolicy.FALLBACK)
            .stateCloner(source -> {
                TestFixtures.ConfigWithExtension copy = new TestFixtures.ConfigWithExtension();
                copy.value = source.value;
                return copy;
            })
            .create();
        assertEquals(1, holder.data().value);
    }

    @Test
    void testRejectsGroupsThatDeclareOneMemberTypeTwice(@TempDir Path tempDir) {
        EasyConfigException failure = assertThrows(
            EasyConfigException.class,
            () -> EasyConfig.holder(TestFixtures.DuplicateMemberGroupConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .create()
        );
        assertEquals(ConfigError.DUPLICATE_CONFIG_GROUP_MEMBER, failure.error());
    }

    private static AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder(Path tempDir) {
        return EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .createAsync();
    }
}

