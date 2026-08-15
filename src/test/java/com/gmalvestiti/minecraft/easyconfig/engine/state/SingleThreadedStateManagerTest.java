package com.gmalvestiti.minecraft.easyconfig.engine.state;

import com.gmalvestiti.minecraft.easyconfig.engine.state.ImmutableConfigStateManager;
import com.gmalvestiti.minecraft.easyconfig.engine.state.SimpleConfigStateManager;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SingleThreadedStateManagerTest {

    @Test
    void testSimpleManagerIsolatesThePublishedSnapshotFromTheCanonicalState() {
        SimpleConfigStateManager<TestFixtures.SimpleConfig> manager =
            new SimpleConfigStateManager<>(SingleThreadedStateManagerTest::copy, new TestFixtures.SimpleConfig());

        TestFixtures.SimpleConfig next = new TestFixtures.SimpleConfig();
        next.value = 42;
        manager.replaceState(next);

        assertSame(next, manager.canonical());
        assertNotSame(next, manager.published());
        assertEquals(42, manager.published().value);
        assertNotSame(next, manager.copyOfCanonical());
        assertEquals(42, manager.copyOfCanonical().value);
    }

    @Test
    void testSimpleManagerKeepsTheCanonicalStateSafeWhenAReaderMutatesPublishedState() {
        TestFixtures.SimpleConfig initial = new TestFixtures.SimpleConfig();
        initial.value = 7;
        SimpleConfigStateManager<TestFixtures.SimpleConfig> manager =
            new SimpleConfigStateManager<>(SingleThreadedStateManagerTest::copy, initial);

        manager.published().value = -1;

        assertEquals(7, manager.canonical().value);
    }

    @Test
    void testImmutableManagerRejectsEveryWrite() {
        TestFixtures.SimpleConfig state = new TestFixtures.SimpleConfig();
        ImmutableConfigStateManager<TestFixtures.SimpleConfig> manager =
            new ImmutableConfigStateManager<>(SingleThreadedStateManagerTest::copy, TestFixtures.SCOPE, state);

        assertNotSame(state, manager.published());
        assertSame(state, manager.canonical());
        assertNotSame(state, manager.copyOfCanonical());

        EasyConfigException failure = assertThrows(
            EasyConfigException.class,
            () -> manager.replaceState(new TestFixtures.SimpleConfig())
        );
        assertEquals(ConfigError.HOLDER_OPERATION_UNSUPPORTED, failure.error());
    }

    @Test
    void testImmutableManagerKeepsFrozenStateSafeWhenAReaderMutatesPublishedState() {
        TestFixtures.SimpleConfig state = new TestFixtures.SimpleConfig();
        state.value = 7;
        ImmutableConfigStateManager<TestFixtures.SimpleConfig> manager =
            new ImmutableConfigStateManager<>(SingleThreadedStateManagerTest::copy, TestFixtures.SCOPE, state);

        manager.published().value = -1;

        assertEquals(7, manager.canonical().value);
    }

    private static TestFixtures.SimpleConfig copy(TestFixtures.SimpleConfig source) {
        TestFixtures.SimpleConfig copy = new TestFixtures.SimpleConfig();
        copy.value = source.value;
        copy.text = source.text;
        return copy;
    }
}

