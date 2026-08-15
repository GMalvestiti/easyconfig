package com.gmalvestiti.minecraft.easyconfig.context;

import com.gmalvestiti.minecraft.easyconfig.api.EasyConfig;
import com.gmalvestiti.minecraft.easyconfig.api.FailurePolicy;
import com.gmalvestiti.minecraft.easyconfig.api.HolderImplementation;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.engine.state.StateClonerImplementation;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigContextAssemblerTest {

    @Test
    void testWiresEveryCollaborator(@TempDir Path tempDir) {
        ConfigContext<TestFixtures.ConfigWithExtension> context = ConfigContextAssembler.assemble(
            settings(TestFixtures.ConfigWithExtension.class, tempDir, FailurePolicy.STRICT));

        assertSame(TestFixtures.ConfigWithExtension.class, context.type());
        assertEquals("mod", context.scope().modId());
        assertNotNull(context.executor());
        assertNotNull(context.engine());
        assertNotNull(context.validationRunner());
        assertNotNull(context.stateCloner());
        assertNotNull(context.exceptionHandler());
    }

    @Test
    void testAssemblesGroupRoots(@TempDir Path tempDir) {
        ConfigContext<TestFixtures.GroupConfig> context =
            ConfigContextAssembler.assemble(settings(TestFixtures.GroupConfig.class, tempDir));
        assertNotNull(context.engine());
    }

    @Test
    void testValidatesTheModelBeforeWiring(@TempDir Path tempDir) {
        ConfigSettings<TestFixtures.ParentWithConfigRef> settings =
            settings(TestFixtures.ParentWithConfigRef.class, tempDir);

        assertThrows(EasyConfigException.class, () -> ConfigContextAssembler.assemble(settings));
    }

    @Test
    void testRunsValidatorsThroughTheAssembledRunner(@TempDir Path tempDir) {
        ConfigContext<TestFixtures.SimpleConfig> context =
            ConfigContextAssembler.assemble(settings(TestFixtures.SimpleConfig.class, tempDir));
        assertTrue(context.validationRunner().run(new TestFixtures.SimpleConfig()).isEmpty());
    }

    @Test
    void testBuildingThroughThePublicApiProducesAWorkingContext(@TempDir Path tempDir) {
        assertNotNull(
            EasyConfig.holder(TestFixtures.SimpleConfig.class)
                .modId("mod")
                .baseDir(tempDir.toString())
                .create());
    }

    private static <T> ConfigSettings<T> settings(Class<T> type, Path tempDir) {
        return settings(type, tempDir, FailurePolicy.FALLBACK);
    }

    private static <T> ConfigSettings<T> settings(Class<T> type, Path tempDir, FailurePolicy policy) {
        return new ConfigSettings<>(
            type,
            new ConfigScope("mod"),
            tempDir.toAbsolutePath().normalize(),
            policy,
            policy,
            policy,
            new StateClonerImplementation<>(type),
            HolderImplementation.ASYNC,
            List.of()
        );
    }
}

