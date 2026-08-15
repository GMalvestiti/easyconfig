package com.gmalvestiti.minecraft.easyconfig.engine;

import com.gmalvestiti.minecraft.easyconfig.layout.ConfigLayout;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigEngineTest {

    @Mock
    private ConfigLayout<TestFixtures.ConfigWithExtension> layout;

    @Captor
    private ArgumentCaptor<TestFixtures.ConfigWithExtension> saved;

    @Test
    void testInitializesThroughTheLayout() {
        when(layout.createDefaults()).thenReturn(new TestFixtures.ConfigWithExtension());

        assertEquals(1, engine().initialize().value);
        verify(layout).createDefaults();
    }

    @Test
    void testRunsAfterLoadOnTheLoadedState() {
        TestFixtures.ConfigWithExtension loaded = new TestFixtures.ConfigWithExtension();
        loaded.value = 7;
        when(layout.load(any())).thenReturn(loaded);

        TestFixtures.ConfigWithExtension result = engine().load(TestFixtures.ConfigWithExtension::new);

        assertSame(loaded, result);
        assertTrue(result.afterLoadCalled);
    }

    @Test
    void testSavesACopyNormalisedByBeforeSave() {
        TestFixtures.ConfigWithExtension published = new TestFixtures.ConfigWithExtension();
        published.value = 7;

        engine().save(published);

        verify(layout).save(saved.capture());
        assertNotSame(published, saved.getValue());
        assertEquals(7, saved.getValue().value);
        assertTrue(saved.getValue().beforeSaveCalled);
        assertFalse(published.beforeSaveCalled, "beforeSave must not touch the published state");
    }

    @Test
    void testSavesTheStateDirectlyWhenTheRootHasNoBeforeSaveHook() {
        @SuppressWarnings("unchecked")
        ConfigLayout<TestFixtures.SimpleConfig> plainLayout = mock(ConfigLayout.class);
        ConfigLifecycleHooks<TestFixtures.SimpleConfig> hooks = new ConfigLifecycleHooks<>(
            TestFixtures.SimpleConfig.class,
            TestFixtures.SCOPE,
            TestFixtures.fieldAccess()
        );
        ConfigEngine<TestFixtures.SimpleConfig> engine =
            new ConfigEngine<>(plainLayout, hooks, source -> {
                throw new AssertionError("a root without beforeSave must not be copied");
            });
        TestFixtures.SimpleConfig published = new TestFixtures.SimpleConfig();

        engine.save(published);

        verify(plainLayout).save(published);
    }

    private ConfigEngine<TestFixtures.ConfigWithExtension> engine() {
        ConfigLifecycleHooks<TestFixtures.ConfigWithExtension> hooks = new ConfigLifecycleHooks<>(
            TestFixtures.ConfigWithExtension.class,
            TestFixtures.SCOPE,
            TestFixtures.fieldAccess()
        );
        return new ConfigEngine<>(layout, hooks, source -> {
            TestFixtures.ConfigWithExtension copy = new TestFixtures.ConfigWithExtension();
            copy.value = source.value;
            copy.afterLoadCalled = source.afterLoadCalled;
            copy.beforeSaveCalled = source.beforeSaveCalled;
            return copy;
        });
    }
}

