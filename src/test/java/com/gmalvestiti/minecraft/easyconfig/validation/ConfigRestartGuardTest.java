package com.gmalvestiti.minecraft.easyconfig.validation;

import com.gmalvestiti.minecraft.easyconfig.api.ConfigHolder;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigRestartGuardTest {

    @Test
    void testRejectsAnUpdateThatTouchesARestartOnlyField(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.EntryConfig> holder = holder(tempDir);

        UpdateResult result = holder.update(config -> config.worldPreset = "flat");

        assertFalse(result.accepted());
        assertEquals("restart.worldPreset", result.violations().getFirst().id());
        assertEquals("default", holder.data().worldPreset, "the rejected candidate must not reach the state");
    }

    @Test
    void testRejectsARestartOnlyFieldNestedInsideTheModel(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.EntryConfig> holder = holder(tempDir);

        UpdateResult result = holder.update(config -> config.section.experimental = true);

        assertFalse(result.accepted());
        assertEquals("restart.experimental", result.violations().getFirst().id());
    }

    @Test
    void testLeavesTheRestOfTheUpdateAlone(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.EntryConfig> holder = holder(tempDir);

        assertTrue(holder.update(config -> config.hudScale = 6).accepted());
        assertEquals(6, holder.data().hudScale);
    }

    @Test
    void testStillAcceptsTheValueTheFileSupplies(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("entries.json5"), "{\"worldPreset\": \"flat\"}");

        ConfigHolder<TestFixtures.EntryConfig> holder = holder(tempDir);

        assertEquals("flat", holder.data().worldPreset,
            "a restart-only field is edited in the file, so loading it is the supported path");
    }

    @Test
    void testReportsTheDedicatedErrorUnderAStrictPolicy(@TempDir Path tempDir) {
        ConfigHolder<TestFixtures.EntryConfig> holder =
            EasyConfig.holder(TestFixtures.EntryConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .updateFailurePolicy(FailurePolicy.STRICT)
                .create();

        EasyConfigException failure = org.junit.jupiter.api.Assertions.assertThrows(
            EasyConfigException.class,
            () -> holder.update(config -> config.worldPreset = "flat")
        );
        assertEquals(ConfigError.RESTART_FIELD_CHANGED, failure.error());
        assertEquals(1, failure.violations().size());
    }

    @Test
    void testResetsEverythingExceptTheFieldsThatOnlyApplyAtStartup(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("entries.json5"),
            "{\"hud_scale\": 9, \"worldPreset\": \"flat\", \"nested\": {\"experimental\": true}}");

        ConfigHolder<TestFixtures.EntryConfig> holder = holder(tempDir);

        assertTrue(holder.reset().accepted(), "a restart-only field must not block a reset");
        assertEquals(2, holder.data().hudScale, "everything else goes back to its default");
        assertEquals("flat", holder.data().worldPreset);
        assertTrue(holder.data().section.experimental, "nested restart-only fields are kept too");
    }

    private static ConfigHolder<TestFixtures.EntryConfig> holder(Path tempDir) {
        return EasyConfig.holder(TestFixtures.EntryConfig.class)
            .modId("mod")
            .baseDir(tempDir.toString())
            .create();
    }
}

