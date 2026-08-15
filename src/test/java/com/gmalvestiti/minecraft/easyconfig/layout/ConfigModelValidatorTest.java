package com.gmalvestiti.minecraft.easyconfig.layout;

import com.gmalvestiti.minecraft.easyconfig.api.annotations.ConfigGroup;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigModelValidatorTest {

    private static ConfigLayoutContext context(Path tempDir) {
        return TestFixtures.layoutContext(TestFixtures.storage(tempDir));
    }

    @Test
    void testAcceptsSingleConfigRoots(@TempDir Path tempDir) {
        assertInstanceOf(
            ConfigSingleLayout.class,
            ConfigModelValidator.resolveLayout(TestFixtures.SimpleConfig.class, context(tempDir))
        );
    }

    @Test
    void testAcceptsGroupConfigRoots(@TempDir Path tempDir) {
        assertInstanceOf(
            ConfigGroupLayout.class,
            ConfigModelValidator.resolveLayout(TestFixtures.GroupConfig.class, context(tempDir))
        );
    }

    @Test
    void testRejectsMissingMarkers(@TempDir Path tempDir) {
        ConfigLayoutContext layoutContext = context(tempDir);
        assertThrows(
            EasyConfigException.class,
            () -> ConfigModelValidator.resolveLayout(String.class, layoutContext)
        );
    }

    @Test
    void testRejectsConfigReferencingAnotherConfig(@TempDir Path tempDir) {
        ConfigLayoutContext layoutContext = context(tempDir);
        assertThrows(
            EasyConfigException.class,
            () -> ConfigModelValidator.resolveLayout(TestFixtures.ParentWithConfigRef.class, layoutContext)
        );
    }

    @Test
    void testAcceptsNonPublicGroupConfigMembers(@TempDir Path tempDir) {
        assertInstanceOf(
            ConfigGroupLayout.class,
            ConfigModelValidator.resolveLayout(TestFixtures.PrivateFieldGroupConfig.class, context(tempDir))
        );
    }

    @Test
    void testRejectsFinalGroupConfigMembers(@TempDir Path tempDir) {
        ConfigLayoutContext layoutContext = context(tempDir);
        EasyConfigException failure = assertThrows(
            EasyConfigException.class,
            () -> ConfigModelValidator.resolveLayout(FinalMemberGroupConfig.class, layoutContext)
        );

        assertEquals(ConfigError.FINAL_CONFIG_GROUP_MEMBER, failure.error());
    }

    @Test
    void testIgnoresStaticAndTransientGroupMembers(@TempDir Path tempDir) {
        assertInstanceOf(
            ConfigGroupLayout.class,
            ConfigModelValidator.resolveLayout(
                TestFixtures.GroupWithIgnoredFieldsConfig.class, context(tempDir))
        );
    }

    @ConfigGroup
    static class FinalMemberGroupConfig {
        public final TestFixtures.MemberAConfig member = new TestFixtures.MemberAConfig();
    }
}

