package com.gmalvestiti.minecraft.easyconfig.api;

import com.gmalvestiti.minecraft.easyconfig.context.ConfigSettings;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigBuilderTest {

    @Test
    void testResolvesDefaultsForEveryUnsetOption(@TempDir Path tempDir) {
        ConfigSettings<TestFixtures.SimpleConfig> settings =
            EasyConfig.holder(TestFixtures.SimpleConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .settings();

        assertEquals(new ConfigScope("mod"), settings.scope());
        assertEquals(tempDir.toAbsolutePath().normalize(), settings.baseDirectory());
        assertNotNull(settings.stateCloner());
        assertEquals(FailurePolicy.FALLBACK, settings.readFailurePolicy());
        assertEquals(FailurePolicy.FALLBACK, settings.writeFailurePolicy());
        assertEquals(FailurePolicy.FALLBACK, settings.updateFailurePolicy());
        assertEquals(HolderImplementation.SIMPLE, settings.implementation());
        assertTrue(settings.changeListeners().isEmpty());
    }

    @Test
    void testKeepsEveryExplicitChoice(@TempDir Path tempDir) {
        ConfigSettings<TestFixtures.SimpleConfig> settings =
            EasyConfig.holder(TestFixtures.SimpleConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .readFailurePolicy(FailurePolicy.STRICT)
                .writeFailurePolicy(FailurePolicy.STRICT)
                .updateFailurePolicy(FailurePolicy.STRICT)
                .stateCloner(source -> source)
                .onChange(state -> {
                })
                .settings();

        assertEquals(FailurePolicy.STRICT, settings.readFailurePolicy());
        assertEquals(FailurePolicy.STRICT, settings.writeFailurePolicy());
        assertEquals(FailurePolicy.STRICT, settings.updateFailurePolicy());
        assertEquals(1, settings.changeListeners().size());
    }

    @Test
    void testLetsTheChosenCreateMethodPickTheImplementation(@TempDir Path tempDir) {
        assertInstanceOf(AsyncConfigHolder.class, EasyConfig.holder(TestFixtures.SimpleConfig.class)
            .modId("mod").baseDir(tempDir.toString()).createAsync());

        ConfigHolder<TestFixtures.SimpleConfig> simple = EasyConfig.holder(TestFixtures.SimpleConfig.class)
            .modId("mod").baseDir(tempDir.toString()).create();
        assertFalse(simple instanceof AsyncConfigHolder, "create() must not hand back an async holder");

        ConfigHolder<TestFixtures.SimpleConfig> frozen = EasyConfig.holder(TestFixtures.SimpleConfig.class)
            .modId("mod").baseDir(tempDir.toString()).updateFailurePolicy(FailurePolicy.STRICT)
            .createImmutable();
        assertThrows(EasyConfigException.class, () -> frozen.update(config -> config.value = 5));
    }

    @Test
    void testNotifiesListenersOnAcceptedChangesButNotOnTheBuildTimeLoad(@TempDir Path tempDir) {
        List<Integer> seen = new ArrayList<>();

        ConfigHolder<TestFixtures.SimpleConfig> holder =
            EasyConfig.holder(TestFixtures.SimpleConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .onChange(state -> {
                    throw new IllegalStateException("a broken listener must not stop the others");
                })
                .onChange(state -> seen.add(state.value))
                .create();

        assertTrue(seen.isEmpty(), "building a holder is not a change");

        holder.updateAndSave(config -> config.value = 5);
        holder.load();
        holder.reset();

        assertEquals(List.of(5, 5, 1), seen);
    }

    @Test
    void testDoesNotNotifyListenersWhenALoadFallsBackToDefaults(@TempDir Path tempDir) throws IOException {
        List<Integer> seen = new ArrayList<>();

        ConfigHolder<TestFixtures.SimpleConfig> holder =
            EasyConfig.holder(TestFixtures.SimpleConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .onChange(state -> seen.add(state.value))
                .create();

        Files.writeString(tempDir.resolve("simple.json5"), "{ not a config");
        holder.load();

        assertTrue(seen.isEmpty(), "a swallowed failure is not a change worth announcing");
    }

    @Test
    void testDoesNotNotifyListenersWhenAnUpdateIsRejected(@TempDir Path tempDir) {        List<Integer> seen = new ArrayList<>();

        ConfigHolder<TestFixtures.ConfigWithExtension> holder =
            EasyConfig.holder(TestFixtures.ConfigWithExtension.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .onChange(state -> seen.add(state.value))
                .create();

        assertFalse(holder.update(config -> config.value = -1).accepted());
        assertTrue(seen.isEmpty());
    }

    @Test
    void testFailurePolicySetsAllThreePoliciesAtOnce(@TempDir Path tempDir) {
        ConfigSettings<TestFixtures.SimpleConfig> settings =
            EasyConfig.holder(TestFixtures.SimpleConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .failurePolicy(FailurePolicy.STRICT)
                .settings();

        assertEquals(FailurePolicy.STRICT, settings.readFailurePolicy());
        assertEquals(FailurePolicy.STRICT, settings.writeFailurePolicy());
        assertEquals(FailurePolicy.STRICT, settings.updateFailurePolicy());
    }

    @Test
    void testRejectsMissingArgumentsAndInvalidPath() {
        assertThrows(EasyConfigException.class, () -> EasyConfig.holder(null));
        assertThrows(
            EasyConfigException.class,
            () -> EasyConfig.holder(TestFixtures.SimpleConfig.class).baseDir("bad\u0000path")
        );
        assertThrows(
            EasyConfigException.class,
            () -> EasyConfig.holder(TestFixtures.SimpleConfig.class).modId("mod").baseDir("bad\u0000path")
        );
        assertThrows(
            EasyConfigException.class,
            () -> EasyConfig.holder(TestFixtures.SimpleConfig.class).modId(" ")
        );
        assertThrows(
            EasyConfigException.class,
            () -> EasyConfig.holder(TestFixtures.SimpleConfig.class).settings()
        );
    }
}

