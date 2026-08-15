package com.gmalvestiti.minecraft.easyconfig.reflection;

import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigObjectFactoryTest {

    @Test
    void testCreatesInstanceWhenNoArgConstructorExists() {
        TestFixtures.SimpleConfig instance = ConfigObjectFactory.newInstance(TestFixtures.SimpleConfig.class, TestFixtures.SCOPE);
        assertNotNull(instance);
    }

    @Test
    void testThrowsWhenNoDefaultConstructorExists() {
        assertThrows(
            EasyConfigException.class,
            () -> ConfigObjectFactory.newInstance(TestFixtures.NoDefaultConstructorConfig.class, TestFixtures.SCOPE)
        );
    }

    @Test
    void testWrapsConstructorInvocationFailure() {
        assertThrows(
            EasyConfigException.class,
            () -> ConfigObjectFactory.newInstance(TestFixtures.ThrowingConstructorConfig.class, TestFixtures.SCOPE)
        );
    }
}

