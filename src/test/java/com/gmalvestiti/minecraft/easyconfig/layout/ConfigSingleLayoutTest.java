package com.gmalvestiti.minecraft.easyconfig.layout;

import com.gmalvestiti.minecraft.easyconfig.storage.ConfigStorage;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigSingleLayoutTest {

    @Test
    void testCreatesDefaultsLoadsAndSaves(@TempDir Path tempDir) {
        ConfigStorage storage = TestFixtures.storage(tempDir);
        ConfigSingleLayout<TestFixtures.SimpleConfig> layout =
            new ConfigSingleLayout<>(TestFixtures.SimpleConfig.class, TestFixtures.layoutContext(storage));

        TestFixtures.SimpleConfig defaults = layout.createDefaults();
        assertEquals(1, defaults.value);

        TestFixtures.SimpleConfig value = new TestFixtures.SimpleConfig();
        value.value = 33;
        layout.save(value);
        TestFixtures.SimpleConfig loaded = layout.load(TestFixtures.SimpleConfig::new);
        assertEquals(33, loaded.value);
    }
}

