package com.gmalvestiti.minecraft.easyconfig.storage;

import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStorageTest {

    @Test
    void testReadsMissingAsNull(@TempDir Path tempDir) {
        ConfigStorage storage = createStorage(tempDir);
        TestFixtures.SimpleConfig value = storage.read(TestFixtures.SimpleConfig.class);
        assertNull(value);
    }

    @Test
    void testThrowsOnIoReadFailure(@TempDir Path tempDir) throws IOException {
        ConfigStorage storage = createStorage(tempDir);
        Path configPath = new ConfigPathResolver(tempDir, TestFixtures.SCOPE).resolveForConfig(TestFixtures.SimpleConfig.class);
        Files.createDirectories(configPath);

        EasyConfigException ex = assertThrows(
            EasyConfigException.class,
            () -> storage.read(TestFixtures.SimpleConfig.class)
        );
        assertEquals(ConfigError.IO_LOAD_FAILURE, ex.error());
    }

    @Test
    void testTreatsAnEmptyFileAsMalformedRatherThanAbsent(@TempDir Path tempDir) throws IOException {
        ConfigStorage storage = createStorage(tempDir);
        Path configPath = new ConfigPathResolver(tempDir, TestFixtures.SCOPE).resolveForConfig(TestFixtures.SimpleConfig.class);
        Files.createDirectories(configPath.getParent());

        for (String content : new String[] {"", "   ", "null"}) {
            Files.writeString(configPath, content);
            EasyConfigException ex = assertThrows(
                EasyConfigException.class,
                () -> storage.read(TestFixtures.SimpleConfig.class)
            );
            assertEquals(ConfigError.MALFORMED_CONFIG_DATA, ex.error());
        }
    }

    @Test
    void testWritesAndReadsConfig(@TempDir Path tempDir) {
        ConfigStorage storage = createStorage(tempDir);
        TestFixtures.SimpleConfig config = new TestFixtures.SimpleConfig();
        config.value = 12;
        storage.write(TestFixtures.SimpleConfig.class, config);
        TestFixtures.SimpleConfig read = storage.read(TestFixtures.SimpleConfig.class);
        assertNotNull(read);
        assertEquals(12, read.value);
    }

    @Test
    void testThrowsMalformedOnParseFailureAndBacksUpOnDemand(@TempDir Path tempDir) throws IOException {
        ConfigStorage storage = createStorage(tempDir);

        Path configPath = new ConfigPathResolver(tempDir, TestFixtures.SCOPE).resolveForConfig(TestFixtures.SimpleConfig.class);
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, "{ this is not a config");

        EasyConfigException ex = assertThrows(
            EasyConfigException.class,
            () -> storage.read(TestFixtures.SimpleConfig.class)
        );
        assertEquals(ConfigError.MALFORMED_CONFIG_DATA, ex.error());

        storage.backupCorrupted(TestFixtures.SimpleConfig.class);
        assertFalse(Files.exists(configPath));
        try (var entries = Files.list(configPath.getParent())) {
            long backups = entries.filter(p -> p.getFileName().toString().contains(".corrupt-")).count();
            assertEquals(1, backups);
        }
    }

    @Test
    void testSkipsTheBackupWhenTheFileIsNotOnDisk(@TempDir Path tempDir) throws IOException {
        ConfigStorage storage = createStorage(tempDir);

        assertDoesNotThrow(() -> storage.backupCorrupted(TestFixtures.SimpleConfig.class));
        assertNoBackupsIn(tempDir);
    }

    @Test
    void testBacksUpEveryMemberFileOfAGroupRoot(@TempDir Path tempDir) throws IOException {
        ConfigStorage storage = createStorage(tempDir);
        ConfigPathResolver resolver = new ConfigPathResolver(tempDir, TestFixtures.SCOPE);
        Path memberA = resolver.resolveForConfig(TestFixtures.MemberAConfig.class);
        Path memberB = resolver.resolveForConfig(TestFixtures.MemberBConfig.class);
        Files.createDirectories(memberA.getParent());
        Files.writeString(memberA, "{\"value\":10}");

        storage.backupCorrupted(TestFixtures.GroupConfig.class);

        assertFalse(Files.exists(memberA), "a group root owns no file of its own, so its members are backed up");
        assertFalse(Files.exists(memberB));
        try (var entries = Files.list(memberA.getParent())) {
            assertEquals(
                1,
                entries.filter(p -> p.getFileName().toString().contains(".corrupt-")).count(),
                "only the member that had a file on disk is preserved");
        }
    }

    @Test
    void testWrapsWriteFailures(@TempDir Path tempDir) {
        ConfigStorage storage = createStorage(tempDir);

        EasyConfigException ex = assertThrows(
            EasyConfigException.class,
            () -> storage.write(TestFixtures.UnserializableConfig.class, new TestFixtures.UnserializableConfig())
        );
        assertEquals(ConfigError.IO_SAVE_FAILURE, ex.error());
    }

    @Test
    void testAbortsBackoffWhenInterrupted() throws Exception {
        Method backOff = ConfigStorage.class.getDeclaredMethod("backOff", int.class);
        backOff.setAccessible(true);

        Thread.currentThread().interrupt();
        try {
            InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> backOff.invoke(null, 0));
            assertTrue(ex.getCause() instanceof IOException);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void testRetriesReplaceWhenTargetIsTemporarilyLocked(@TempDir Path tempDir) throws Exception {
        Method replace = ConfigStorage.class.getDeclaredMethod("replace", Path.class, Path.class);
        replace.setAccessible(true);

        Path source = tempDir.resolve("source.tmp");
        Path target = tempDir.resolve("target.json");
        Files.writeString(source, "new");
        Files.writeString(target, "old");

        try (FileChannel ignored = FileChannel.open(target, StandardOpenOption.WRITE)) {
            InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> replace.invoke(null, source, target));
            assertTrue(ex.getCause() instanceof IOException);
        }
        assertTrue(Files.exists(target));
    }

    @Test
    void testFallsBackWhenAtomicMoveIsUnsupported(@TempDir Path tempDir) throws Exception {
        Method replace = ConfigStorage.class.getDeclaredMethod("replace", Path.class, Path.class);
        replace.setAccessible(true);

        Path zip = tempDir.resolve("archive.zip");
        URI uri = URI.create("jar:" + zip.toUri());
        try (FileSystem fs = java.nio.file.FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
            Path source = fs.getPath("/source.tmp");
            Path target = fs.getPath("/target.json");
            Files.writeString(source, "new");
            Files.writeString(target, "old");

            replace.invoke(null, source, target);
            assertEquals("new", Files.readString(target));
        }
    }

    @Test
    void testSwallowsDeleteFailuresInCleanup(@TempDir Path tempDir) throws Exception {
        Method deleteQuietly = ConfigStorage.class.getDeclaredMethod("deleteQuietly", Path.class);
        deleteQuietly.setAccessible(true);

        Path nonEmptyDirectory = tempDir.resolve("non-empty");
        Files.createDirectories(nonEmptyDirectory);
        Files.writeString(nonEmptyDirectory.resolve("child.txt"), "x");

        deleteQuietly.invoke(null, nonEmptyDirectory);
        assertTrue(Files.exists(nonEmptyDirectory));
    }

    @Test
    void testLeavesNoTempFileWhenACommitFails(@TempDir Path tempDir) throws IOException {
        ConfigStorage storage = createStorage(tempDir);
        Path configPath = new ConfigPathResolver(tempDir, TestFixtures.SCOPE)
            .resolveForConfig(TestFixtures.SimpleConfig.class);
        Files.createDirectories(configPath);
        Files.writeString(configPath.resolve("child.txt"), "x");

        assertThrows(
            EasyConfigException.class,
            () -> storage.write(TestFixtures.SimpleConfig.class, new TestFixtures.SimpleConfig())
        );

        assertTrue(Files.exists(configPath), "a failed commit must not disturb the existing target");
        assertNoTempFilesIn(configPath.getParent());
    }

    @Test
    void testLeavesEveryTargetUntouchedWhenOneMemberFailsToStage(@TempDir Path tempDir) throws IOException {
        ConfigStorage storage = createStorage(tempDir);
        ConfigPathResolver resolver = new ConfigPathResolver(tempDir, TestFixtures.SCOPE);

        Map<Class<?>, Object> entries = new LinkedHashMap<>();
        entries.put(TestFixtures.MemberAConfig.class, new TestFixtures.MemberAConfig());
        entries.put(TestFixtures.UnserializableConfig.class, new TestFixtures.UnserializableConfig());

        EasyConfigException ex = assertThrows(EasyConfigException.class, () -> storage.writeAll(entries));

        assertEquals(ConfigError.IO_SAVE_FAILURE, ex.error());
        assertFalse(
            Files.exists(resolver.resolveForConfig(TestFixtures.MemberAConfig.class)),
            "a later member failing to stage must leave earlier targets alone");
        assertNoTempFilesIn(resolver.resolveForConfig(TestFixtures.MemberAConfig.class).getParent());
    }

    private static void assertNoTempFilesIn(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            assertEquals(
                0,
                entries.filter(p -> p.getFileName().toString().contains(".tmp")).count(),
                "a discarded stage must leave nothing behind");
        }
    }

    private static void assertNoBackupsIn(Path directory) throws IOException {
        try (var entries = Files.walk(directory)) {
            assertEquals(
                0,
                entries.filter(p -> p.getFileName().toString().contains(".corrupt-")).count(),
                "a skipped backup must leave nothing behind");
        }
    }

    private static ConfigStorage createStorage(Path tempDir) {
        return TestFixtures.storage(tempDir);
    }
}

