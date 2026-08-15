package com.gmalvestiti.minecraft.easyconfig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class EasyConfigCommonTest {

    @Test
    void testExposesModIdAndInitIsNoop() {
        assertEquals("easyconfig", EasyConfigCommon.MOD_ID);
        assertDoesNotThrow(EasyConfigCommon::new);
    }
}

