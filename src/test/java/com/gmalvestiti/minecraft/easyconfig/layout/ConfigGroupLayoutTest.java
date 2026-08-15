package com.gmalvestiti.minecraft.easyconfig.layout;

import com.gmalvestiti.minecraft.easyconfig.storage.ConfigPathResolver;
import com.gmalvestiti.minecraft.easyconfig.storage.ConfigStorage;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigGroupLayoutTest {

    @Test
    void testCreatesDefaultsLoadsAndSavesMembers(@TempDir Path tempDir) {
        ConfigGroupLayout<TestFixtures.GroupConfig> layout = createLayout(tempDir);

        TestFixtures.GroupConfig defaults = layout.createDefaults();
        assertNotNull(defaults.memberA);
        assertNotNull(defaults.memberB);

        defaults.memberA.value = 77;
        defaults.memberB.value = 88;
        layout.save(defaults);

        TestFixtures.GroupConfig loaded = layout.load(TestFixtures.GroupConfig::new);
        assertEquals(77, loaded.memberA.value);
        assertEquals(88, loaded.memberB.value);
    }

    @Test
    void testHandlesNullStateAndNullMembers(@TempDir Path tempDir) {
        ConfigGroupLayout<TestFixtures.GroupConfig> layout = createLayout(tempDir);

        TestFixtures.GroupConfig group = new TestFixtures.GroupConfig();
        group.memberA = null;
        group.memberB = null;
        layout.save(group);

        assertNotNull(group.memberA);
        assertNotNull(group.memberB);
        TestFixtures.GroupConfig loaded = layout.load(() -> null);
        assertNotNull(loaded.memberA);
        assertNotNull(loaded.memberB);
    }

    @Test
    void testLeavesPreviousMemberFilesUntouchedWhenStagingFails(@TempDir Path tempDir) throws IOException {
        ConfigPathResolver resolver = new ConfigPathResolver(tempDir, TestFixtures.SCOPE);
        Path memberAPath = resolver.resolveForConfig(TestFixtures.MemberAConfig.class);
        Files.createDirectories(memberAPath.getParent());
        Files.writeString(memberAPath, "{\"value\":10}");

        ConfigStorage storage = TestFixtures.storage(tempDir);
        ConfigGroupLayout<TestFixtures.GroupWithUnserializableMemberConfig> layout =
            new ConfigGroupLayout<>(
                TestFixtures.GroupWithUnserializableMemberConfig.class, TestFixtures.layoutContext(storage));
        TestFixtures.GroupWithUnserializableMemberConfig group =
            new TestFixtures.GroupWithUnserializableMemberConfig();
        group.memberA = new TestFixtures.MemberAConfig();
        group.memberA.value = 77;
        group.broken = new TestFixtures.UnserializableConfig();

        EasyConfigException failure = assertThrows(EasyConfigException.class, () -> layout.save(group));

        assertEquals(ConfigError.IO_SAVE_FAILURE, failure.error());
        assertEquals("{\"value\":10}", Files.readString(memberAPath));
    }

    private static ConfigGroupLayout<TestFixtures.GroupConfig> createLayout(Path tempDir) {
        ConfigStorage storage = TestFixtures.storage(tempDir);
        return new ConfigGroupLayout<>(TestFixtures.GroupConfig.class, TestFixtures.layoutContext(storage));
    }
}

