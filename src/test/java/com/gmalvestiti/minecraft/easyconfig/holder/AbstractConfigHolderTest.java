package com.gmalvestiti.minecraft.easyconfig.holder;

import com.gmalvestiti.minecraft.easyconfig.api.AsyncConfigHolder;
import com.gmalvestiti.minecraft.easyconfig.api.ConfigHolder;
import com.gmalvestiti.minecraft.easyconfig.api.EasyConfig;
import com.gmalvestiti.minecraft.easyconfig.api.FailurePolicy;
import com.gmalvestiti.minecraft.easyconfig.api.HolderImplementation;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.context.ConfigSettings;
import com.gmalvestiti.minecraft.easyconfig.engine.state.StateClonerImplementation;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AbstractConfigHolderTest {

    @Test
    void testLogsCompletedLoadAndSaveOperationsOnSimpleHolder(@TempDir Path tempDir) {
        ConfigScope scope = spy(new ConfigScope("mod"));
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, scope, HolderImplementation.SIMPLE);

        holder.load();
        holder.save();

        verify(scope).logInfo("Config load operation completed successfully");
        verify(scope).logInfo("Config save operation completed successfully");
    }

    @Test
    void testLogsCompletedLoadAndSaveOperationsOnAsyncHolder(@TempDir Path tempDir) {
        ConfigScope scope = spy(new ConfigScope("mod"));
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = asyncHolder(tempDir, scope);

        holder.loadAsync().join();
        holder.saveAsync().join();

        verify(scope).logInfo("Config load operation completed successfully");
        verify(scope).logInfo("Config save operation completed successfully");
    }

    @Test
    void testDoesNotLogSuccessWhenTheWritePolicySwallowsAFailedSave(@TempDir Path tempDir) throws Exception {
        Path unusableDir = tempDir.resolve("config");
        Files.createFile(unusableDir);
        ConfigScope scope = spy(new ConfigScope("mod"));
        ConfigHolder<TestFixtures.ConfigWithExtension> holder =
            holder(unusableDir, scope, HolderImplementation.SIMPLE, FailurePolicy.FALLBACK, FailurePolicy.FALLBACK);

        holder.save();

        verify(scope, never()).logInfo("Config save operation completed successfully");
        verify(scope, times(2)).logError(contains("keeping in-memory state"), any(EasyConfigException.class));
    }

    @Test
    void testCreatesTheConfigFileDuringInitialization(@TempDir Path tempDir) throws Exception {
        ConfigScope scope = spy(new ConfigScope("mod"));

        holder(tempDir, scope, HolderImplementation.SIMPLE);

        assertTrue(Files.readString(tempDir.resolve("with-extension.json5")).contains("\"value\": 1"));
    }

    @Test
    void testAdoptsAnExistingValidFileDuringInitialization(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("with-extension.json5"), "{\"value\":7}");
        ConfigScope scope = spy(new ConfigScope("mod"));

        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, scope, HolderImplementation.SIMPLE);

        assertEquals(7, holder.data().value, "a valid file must seed the holder without calling load()");
        assertTrue(Files.readString(tempDir.resolve("with-extension.json5")).contains("\"value\": 7"),
            "adopted values must survive the write-back");
    }

    @Test
    void testBacksUpAnInvalidFileAndPersistsDefaultsDuringInitialization(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("with-extension.json5");
        Files.writeString(file, "{\"value\":-5}");
        ConfigScope scope = spy(new ConfigScope("mod"));

        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, scope, HolderImplementation.SIMPLE);

        assertEquals(1, holder.data().value);
        assertTrue(Files.readString(file).contains("\"value\": 1"));
        try (var entries = Files.list(tempDir)) {
            assertEquals(1, entries.filter(p -> p.getFileName().toString().contains(".corrupt-")).count());
        }
    }

    @Test
    void testCreatesEveryGroupMemberFileDuringInitialization(@TempDir Path tempDir) throws Exception {
        EasyConfig.holder(TestFixtures.GroupConfig.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .create();

        assertTrue(Files.readString(tempDir.resolve("member-a.json5")).contains("\"value\": 10"));
        assertTrue(Files.readString(tempDir.resolve("member-b.json5")).contains("\"value\": 20"));
    }

    @Test
    void testBacksUpOnlyTheMalformedGroupMemberAndPersistsItsDefaults(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("member-a.json5"), "{");
        Files.writeString(tempDir.resolve("member-b.json5"), "{\"value\":42}");

        ConfigHolder<TestFixtures.GroupConfig> holder = EasyConfig.holder(TestFixtures.GroupConfig.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .create();

        assertEquals(10, holder.data().memberA.value, "the malformed member falls back to its defaults");
        assertEquals(42, holder.data().memberB.value, "a healthy sibling keeps its persisted values");
        assertTrue(Files.readString(tempDir.resolve("member-a.json5")).contains("\"value\": 10"));
        try (var entries = Files.list(tempDir)) {
            assertEquals(1, entries.filter(p -> p.getFileName().toString().contains(".corrupt-")).count());
        }
    }

    private static ConfigHolder<TestFixtures.ConfigWithExtension> holder(
        Path tempDir,
        ConfigScope scope,
        HolderImplementation implementation
    ) {
        return holder(tempDir, scope, implementation, FailurePolicy.FALLBACK, FailurePolicy.FALLBACK);
    }

    @SuppressWarnings("unchecked")
    private static AsyncConfigHolder<TestFixtures.ConfigWithExtension> asyncHolder(
        Path tempDir,
        ConfigScope scope
    ) {
        return (AsyncConfigHolder<TestFixtures.ConfigWithExtension>)
            holder(tempDir, scope, HolderImplementation.ASYNC);
    }

    @Test
    void testBacksUpTheGroupMemberFilesWhenTheAssembledGroupFailsValidation(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("extended-member.json5"), "{\"value\":3}");
        ConfigScope scope = spy(new ConfigScope("mod"));

        ConfigSettings<TestFixtures.PlainGroupWithExtendedMemberConfig> settings = new ConfigSettings<>(
            TestFixtures.PlainGroupWithExtendedMemberConfig.class,
            scope,
            tempDir.toAbsolutePath().normalize(),
            FailurePolicy.FALLBACK,
            FailurePolicy.FALLBACK,
            FailurePolicy.FALLBACK,
            new StateClonerImplementation<>(TestFixtures.PlainGroupWithExtendedMemberConfig.class),
            HolderImplementation.SIMPLE,
            List.of(), List.of(), List.of(), List.of()
        );
        ConfigHolder<TestFixtures.PlainGroupWithExtendedMemberConfig> holder = HolderFactory.create(settings);

        assertEquals(2, holder.data().member.value, "a member that breaks a rule falls back to defaults");
        assertTrue(Files.readString(tempDir.resolve("extended-member.json5")).contains("\"value\": 2"));
        try (var entries = Files.list(tempDir)) {
            assertEquals(
                1,
                entries.filter(p -> p.getFileName().toString().contains(".corrupt-")).count(),
                "the file the user wrote must be preserved before defaults overwrite it");
        }
        verify(scope, never()).logError(contains("failed to back up"), any());
    }

    @Test
    void testResetRestoresTheDeclaredDefaultsWithoutTouchingDisk(@TempDir Path tempDir) throws Exception {
        ConfigScope scope = spy(new ConfigScope("mod"));
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, scope, HolderImplementation.SIMPLE);
        holder.updateAndSave(config -> config.value = 42);

        assertTrue(holder.reset().accepted());

        assertEquals(1, holder.data().value);
        assertTrue(Files.readString(tempDir.resolve("with-extension.json5")).contains("\"value\": 42"),
            "reset alone must not write");
    }

    @Test
    void testResetAndSaveRestoresTheDeclaredDefaultsOnDisk(@TempDir Path tempDir) throws Exception {
        ConfigScope scope = spy(new ConfigScope("mod"));
        ConfigHolder<TestFixtures.ConfigWithExtension> holder = holder(tempDir, scope, HolderImplementation.SIMPLE);
        holder.updateAndSave(config -> config.value = 42);

        assertTrue(holder.resetAndSave().accepted());

        assertEquals(1, holder.data().value);
        assertTrue(Files.readString(tempDir.resolve("with-extension.json5")).contains("\"value\": 1"));
    }

    @Test
    void testResetAndSaveAsyncRestoresTheDeclaredDefaultsOnDisk(@TempDir Path tempDir) throws Exception {
        ConfigScope scope = spy(new ConfigScope("mod"));
        AsyncConfigHolder<TestFixtures.ConfigWithExtension> holder = asyncHolder(tempDir, scope);
        holder.updateAndSaveAsync(config -> config.value = 42).join();

        assertTrue(holder.resetAndSaveAsync().join().accepted());

        assertEquals(1, holder.data().value);
        assertTrue(Files.readString(tempDir.resolve("with-extension.json5")).contains("\"value\": 1"));
    }

    private static ConfigHolder<TestFixtures.ConfigWithExtension> holder(
        Path tempDir,
        ConfigScope scope,
        HolderImplementation implementation,
        FailurePolicy updatePolicy,
        FailurePolicy writePolicy
    ) {        ConfigSettings<TestFixtures.ConfigWithExtension> settings = new ConfigSettings<>(
            TestFixtures.ConfigWithExtension.class,
            scope,
            tempDir.toAbsolutePath().normalize(),
            FailurePolicy.FALLBACK,
            writePolicy,
            updatePolicy,
            new StateClonerImplementation<>(TestFixtures.ConfigWithExtension.class),
            implementation,
            List.of(), List.of(), List.of(), List.of()
        );
        return HolderFactory.create(settings);
    }
}

