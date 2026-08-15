package com.gmalvestiti.minecraft.easyconfig.engine.state;

import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class StateClonerImplementationTest {

    @Test
    void testDeepCopiesViaJson() {
        StateClonerImplementation<TestFixtures.SimpleConfig> cloner =
            new StateClonerImplementation<>(TestFixtures.SimpleConfig.class);
        TestFixtures.SimpleConfig source = new TestFixtures.SimpleConfig();
        source.value = 99;

        TestFixtures.SimpleConfig copy = cloner.copy(source);
        assertNotNull(copy);
        assertNotSame(source, copy);
        assertEquals(99, copy.value);
    }

    @Test
    void testReturnsNullForNullInput() {
        StateClonerImplementation<TestFixtures.SimpleConfig> cloner =
            new StateClonerImplementation<>(TestFixtures.SimpleConfig.class);
        assertNull(cloner.copy(null));
    }
}

