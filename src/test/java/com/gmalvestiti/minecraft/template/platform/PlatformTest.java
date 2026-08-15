package com.gmalvestiti.minecraft.template.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlatformTest {

    @Test
    void testConstructor() {
        assertDoesNotThrow(Platform::new);
    }

    @Test
    void testPlatform() {
        assertTrue(Platform.getConfigDir().toString().contains("config"));
    }
}
