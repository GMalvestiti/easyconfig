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
        assertTrue(settings.updateListeners().isEmpty());
        assertTrue(settings.loadListeners().isEmpty());
        assertTrue(settings.saveListeners().isEmpty());
        assertTrue(settings.resetListeners().isEmpty());
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
                .onUpdate(state -> {
                })
                .settings();

        assertEquals(FailurePolicy.STRICT, settings.readFailurePolicy());
        assertEquals(FailurePolicy.STRICT, settings.writeFailurePolicy());
        assertEquals(FailurePolicy.STRICT, settings.updateFailurePolicy());
        assertEquals(1, settings.updateListeners().size());
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
    void testOnUpdateFiresOnAcceptedUpdateButNotOnBuildLoadOrReset(@TempDir Path tempDir) {
        List<Integer> seen = new ArrayList<>();

        ConfigHolder<TestFixtures.SimpleConfig> holder =
            EasyConfig.holder(TestFixtures.SimpleConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .onUpdate(state -> {
                    throw new IllegalStateException("a broken listener must not stop the others");
                })
                .onUpdate(state -> seen.add(state.value))
                .create();

        assertTrue(seen.isEmpty(), "building a holder is not a change");

        holder.updateAndSave(config -> config.value = 5);
        holder.load();
        holder.reset();

        assertEquals(List.of(5), seen);
    }

    @Test
    void testOnLoadFiresAfterSuccessfulLoadOnly(@TempDir Path tempDir) {
        List<Integer> seen = new ArrayList<>();

        ConfigHolder<TestFixtures.SimpleConfig> holder =
            EasyConfig.holder(TestFixtures.SimpleConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .onLoad(state -> seen.add(state.value))
                .create();

        assertTrue(seen.isEmpty(), "build-time load must not fire onLoad");

        holder.update(config -> config.value = 7);
        holder.load();
        holder.reset();

        assertEquals(List.of(1), seen, "onLoad fires once, with the value read from disk");
    }

    @Test
    void testOnSaveFiresAfterEveryPersistOperation(@TempDir Path tempDir) {
        List<Integer> seen = new ArrayList<>();

        ConfigHolder<TestFixtures.SimpleConfig> holder =
            EasyConfig.holder(TestFixtures.SimpleConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .onSave(state -> seen.add(state.value))
                .create();

        holder.update(config -> config.value = 3);
        holder.save();
        holder.updateAndSave(config -> config.value = 9);
        holder.resetAndSave();

        assertEquals(List.of(3, 9, 1), seen);
    }

    @Test
    void testOnResetFiresAfterAcceptedReset(@TempDir Path tempDir) {
        List<Integer> seen = new ArrayList<>();

        ConfigHolder<TestFixtures.SimpleConfig> holder =
            EasyConfig.holder(TestFixtures.SimpleConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .onReset(state -> seen.add(state.value))
                .create();

        holder.update(config -> config.value = 4);
        holder.load();
        holder.reset();
        holder.resetAndSave();

        assertEquals(List.of(1, 1), seen);
    }

    @Test
    void testRegistersListenersAfterHolderCreation(@TempDir Path tempDir) {
        List<Integer> updates = new ArrayList<>();
        List<Integer> loads   = new ArrayList<>();
        List<Integer> saves   = new ArrayList<>();
        List<Integer> resets  = new ArrayList<>();

        ConfigHolder<TestFixtures.SimpleConfig> holder =
            EasyConfig.holder(TestFixtures.SimpleConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .create();

        holder.onUpdate(state -> updates.add(state.value));
        holder.onLoad(state -> loads.add(state.value));
        holder.onSave(state -> saves.add(state.value));
        holder.onReset(state -> resets.add(state.value));

        holder.updateAndSave(config -> config.value = 6);
        holder.load();
        holder.reset();

        assertEquals(List.of(6), updates);
        assertEquals(List.of(6), loads, "load re-reads the file written by updateAndSave");
        assertEquals(List.of(6), saves);
        assertEquals(List.of(1), resets);
    }

    @Test
    void testDoesNotNotifyListenersWhenALoadFallsBackToDefaults(@TempDir Path tempDir) throws IOException {
        List<Integer> seen = new ArrayList<>();

        ConfigHolder<TestFixtures.SimpleConfig> holder =
            EasyConfig.holder(TestFixtures.SimpleConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .onLoad(state -> seen.add(state.value))
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
                .onUpdate(state -> seen.add(state.value))
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

