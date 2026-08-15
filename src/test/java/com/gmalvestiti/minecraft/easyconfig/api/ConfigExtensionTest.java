package com.gmalvestiti.minecraft.easyconfig.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import com.gmalvestiti.minecraft.easyconfig.api.spi.Violation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigExtensionTest {

    @Test
    void testDefaultHooksAreNoops() {
        ConfigExtension extension = new ConfigExtension() {
        };

        assertDoesNotThrow(extension::afterLoad);
        assertDoesNotThrow(extension::beforeSave);

        List<Violation> violations = new ArrayList<>();
        assertDoesNotThrow(() -> extension.validate(violations));
        assertTrue(violations.isEmpty());
    }
}

