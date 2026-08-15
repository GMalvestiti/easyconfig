package com.gmalvestiti.minecraft.easyconfig.engine.state;

import com.gmalvestiti.minecraft.easyconfig.engine.state.AsyncConfigStateManager;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class AsyncConfigStateManagerTest {

    @Test
    void testExposesPublishedCanonicalAndSnapshotState() {
        AsyncConfigStateManager<TestFixtures.SimpleConfig> manager = new AsyncConfigStateManager<>(
            source -> {
                TestFixtures.SimpleConfig copy = new TestFixtures.SimpleConfig();
                copy.value = source.value;
                copy.text = source.text;
                return copy;
            },
            new TestFixtures.SimpleConfig()
        );

        assertEquals(1, manager.published().value);
        assertEquals(1, manager.canonical().value);

        TestFixtures.SimpleConfig snapshot = manager.copyOfCanonical();
        assertNotSame(snapshot, manager.canonical());
    }

    @Test
    void testReplacesStateWithFreshPublishedSnapshot() {
        AsyncConfigStateManager<TestFixtures.SimpleConfig> manager = new AsyncConfigStateManager<>(
            source -> {
                TestFixtures.SimpleConfig copy = new TestFixtures.SimpleConfig();
                copy.value = source.value;
                return copy;
            },
            new TestFixtures.SimpleConfig()
        );
        TestFixtures.SimpleConfig next = new TestFixtures.SimpleConfig();
        next.value = 42;
        manager.replaceState(next);

        assertSame(next, manager.canonical());
        assertEquals(42, manager.published().value);
        assertNotSame(next, manager.published());
    }
}

