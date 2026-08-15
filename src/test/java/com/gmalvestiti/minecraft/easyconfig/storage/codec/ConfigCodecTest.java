package com.gmalvestiti.minecraft.easyconfig.storage.codec;

import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.storage.ConfigStorage;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigCodecTest {

    @Test
    void testWritesJsonUnderTheNamesAndCommentsTheEntryAnnotationDeclares(@TempDir Path tempDir) throws IOException {
        ConfigStorage storage = TestFixtures.storage(tempDir);

        storage.write(TestFixtures.EntryConfig.class, new TestFixtures.EntryConfig());
        String written = Files.readString(tempDir.resolve("entries.json"));
        assertTrue(written.contains("hud_scale"), "@ConfigEntry(name) renames the property on disk");
        assertTrue(written.contains("Scale of the HUD overlay."));
        assertTrue(written.contains("Settings for the entry fixture."));
        assertTrue(written.contains("max_depth"), "nested objects are renamed too");
    }

    @Test
    void testRoundTripsJsonThroughTheRenamedProperties(@TempDir Path tempDir) {
        ConfigStorage storage = TestFixtures.storage(tempDir);
        TestFixtures.EntryConfig written = new TestFixtures.EntryConfig();
        written.hudScale = 9;
        written.section.maxDepth = 11;

        storage.write(TestFixtures.EntryConfig.class, written);
        TestFixtures.EntryConfig read = storage.read(TestFixtures.EntryConfig.class);

        assertEquals(9, read.hudScale);
        assertEquals(11, read.section.maxDepth);
    }

    @Test
    void testWritesTomlForTypesThatDeclareIt(@TempDir Path tempDir) throws IOException {
        ConfigStorage storage = TestFixtures.storage(tempDir);

        storage.write(TestFixtures.TomlConfig.class, new TestFixtures.TomlConfig());
        String written = Files.readString(tempDir.resolve("toml-fixture.toml"));
        assertTrue(written.contains("#A TOML-backed fixture."), "the class comment heads the file");
        assertTrue(written.contains("How loud, from 0 to 10."));
        assertTrue(written.contains("volume = 5"), "an integer field must not be written as a float");
        assertFalse(written.contains("volume = 5.0"));
        assertTrue(written.contains("display_name = \"player\""));
        assertTrue(written.contains("[section]"), "nested objects become tables");
    }

    @Test
    void testRoundTripsToml(@TempDir Path tempDir) {
        ConfigStorage storage = TestFixtures.storage(tempDir);
        TestFixtures.TomlConfig written = new TestFixtures.TomlConfig();
        written.volume = 8;
        written.displayName = "steve";
        written.tags.add("c");
        written.section.ratio = 0.25;
        written.section.enabled = false;

        storage.write(TestFixtures.TomlConfig.class, written);
        TestFixtures.TomlConfig read = storage.read(TestFixtures.TomlConfig.class);

        assertEquals(8, read.volume);
        assertEquals("steve", read.displayName);
        assertEquals(java.util.List.of("a", "b", "c"), read.tags);
        assertEquals(0.25, read.section.ratio);
        assertEquals(false, read.section.enabled);
    }

    @Test
    void testReportsMalformedTomlLikeAnyOtherUnreadableFile(@TempDir Path tempDir) throws IOException {
        ConfigStorage storage = TestFixtures.storage(tempDir);
        Files.writeString(tempDir.resolve("toml-fixture.toml"), "volume = = 3");

        EasyConfigException failure = assertThrows(
            EasyConfigException.class,
            () -> storage.read(TestFixtures.TomlConfig.class)
        );
        assertEquals(ConfigError.MALFORMED_CONFIG_DATA, failure.error());
    }

    @Test
    void testRendersMultiLineCommentsAsOneJsonBlock(@TempDir Path tempDir) throws IOException {
        ConfigStorage storage = TestFixtures.storage(tempDir);

        storage.write(TestFixtures.AwkwardCommentConfig.class, new TestFixtures.AwkwardCommentConfig());
        String written = Files.readString(tempDir.resolve("awkward-comments.json"));

        assertTrue(written.contains("/*"), "several lines share one block comment");
        assertTrue(written.contains(" * Two lines,"));
        assertTrue(written.contains(" * so this renders as a block."));
    }

    @Test
    void testKeepsFilesReadableWhenACommentContainsABlockTerminator(@TempDir Path tempDir) {
        ConfigStorage storage = TestFixtures.storage(tempDir);
        TestFixtures.AwkwardCommentConfig written = new TestFixtures.AwkwardCommentConfig();
        written.value = 42;

        storage.write(TestFixtures.AwkwardCommentConfig.class, written);

        assertEquals(
            42,
            storage.read(TestFixtures.AwkwardCommentConfig.class).value,
            "a comment must never be able to truncate the file it documents");
    }

    @Test
    void testCommentsOneTomlLinePerEntry(@TempDir Path tempDir) throws IOException {
        ConfigStorage storage = TestFixtures.storage(tempDir);
        TestFixtures.AwkwardCommentTomlConfig written = new TestFixtures.AwkwardCommentTomlConfig();
        written.value = 42;

        storage.write(TestFixtures.AwkwardCommentTomlConfig.class, written);
        String text = Files.readString(tempDir.resolve("awkward-comments-toml.toml"));

        assertTrue(text.contains("#Two lines,"));
        assertTrue(text.contains("#so this renders as a block."));
        assertFalse(text.contains("/*"), "TOML has no block comment to escape into");
        assertEquals(42, storage.read(TestFixtures.AwkwardCommentTomlConfig.class).value);
    }
}

