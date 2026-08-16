package com.gmalvestiti.minecraft.easyconfig.storage;

import com.gmalvestiti.minecraft.easyconfig.api.annotations.Config;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigPathResolverTest {

    @Config(name = "named", path = "nested")
    static class PathConfig {
    }

    @Config(name = "bad/name")
    static class BadNameConfig {
    }

    @Config(name = "name", path = "../escape")
    static class EscapePathConfig {
    }

    @Config(name = "already.json5")
    static class JsonSuffixConfig {
    }

    @Config(name = ".json5")
    static class DotJsonNameConfig {
    }

    @Config(name = "name", path = "C:\\absolute")
    static class AbsolutePathConfig {
    }

    @Config(name = "bad\u0000name")
    static class InvalidPathCharsNameConfig {
    }

    @Config(name = "name", path = "bad\u0000path")
    static class InvalidPathCharsPathConfig {
    }

    @Config(name = "C:")
    static class DriveRelativeNameConfig {
    }

    @Test
    void testResolvesAndCachesPaths(@TempDir Path tempDir) {
        ConfigPathResolver resolver = new ConfigPathResolver(tempDir, TestFixtures.SCOPE);
        Path first = resolver.resolveForConfig(PathConfig.class);
        Path second = resolver.resolveForConfig(PathConfig.class);
        Path jsonSuffix = resolver.resolveForConfig(JsonSuffixConfig.class);

        assertEquals(first, second);
        assertTrue(first.toString().endsWith("nested" + java.io.File.separator + "named.json5"));
        assertTrue(jsonSuffix.toString().endsWith("already.json5"));
    }

    @Test
    void testRejectsInvalidDefinitions(@TempDir Path tempDir) {
        ConfigPathResolver resolver = new ConfigPathResolver(tempDir, TestFixtures.SCOPE);
        assertThrows(EasyConfigException.class, () -> resolver.resolveForConfig(String.class));
        assertThrows(EasyConfigException.class, () -> resolver.resolveForConfig(BadNameConfig.class));
        assertThrows(EasyConfigException.class, () -> resolver.resolveForConfig(EscapePathConfig.class));
        assertThrows(EasyConfigException.class, () -> resolver.resolveForConfig(DotJsonNameConfig.class));
        assertThrows(EasyConfigException.class, () -> resolver.resolveForConfig(AbsolutePathConfig.class));
        assertThrows(EasyConfigException.class, () -> resolver.resolveForConfig(InvalidPathCharsNameConfig.class));
        assertThrows(EasyConfigException.class, () -> resolver.resolveForConfig(InvalidPathCharsPathConfig.class));
        assertThrows(EasyConfigException.class, () -> resolver.resolveForConfig(DriveRelativeNameConfig.class));
    }

    @Test
    void testRejectsTwoTypesThatClaimTheSameFile(@TempDir Path tempDir) {
        ConfigPathResolver resolver = new ConfigPathResolver(tempDir, TestFixtures.SCOPE);
        resolver.resolveForConfig(TestFixtures.MemberAConfig.class);

        EasyConfigException failure = assertThrows(
            EasyConfigException.class,
            () -> resolver.resolveForConfig(TestFixtures.ClashingMemberConfig.class)
        );
        assertEquals(ConfigError.CONFLICTING_CONFIG_PATH, failure.error());
    }

    @Test
    void testRejectsTwoTypesThatClaimTheSameFileAcrossResolvers(@TempDir Path tempDir) {
        new ConfigPathResolver(tempDir, TestFixtures.SCOPE).resolveForConfig(TestFixtures.MemberAConfig.class);

        EasyConfigException failure = assertThrows(
            EasyConfigException.class,
            () -> new ConfigPathResolver(tempDir, TestFixtures.SCOPE)
                .resolveForConfig(TestFixtures.ClashingMemberConfig.class)
        );

        assertEquals(ConfigError.CONFLICTING_CONFIG_PATH, failure.error());
    }

    @Test
    void testAllowsSameTypeToClaimTheSameFileAcrossResolvers(@TempDir Path tempDir) {
        Path first = new ConfigPathResolver(tempDir, TestFixtures.SCOPE)
            .resolveForConfig(TestFixtures.MemberAConfig.class);
        Path second = new ConfigPathResolver(tempDir, TestFixtures.SCOPE)
            .resolveForConfig(TestFixtures.MemberAConfig.class);

        assertEquals(first, second);
    }
}

