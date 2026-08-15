package com.gmalvestiti.minecraft.easyconfig.api.annotations;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigGroupTest {

    @Test
    void testHasRuntimeTypeTarget() {
        Retention retention = ConfigGroup.class.getAnnotation(Retention.class);
        Target target = ConfigGroup.class.getAnnotation(Target.class);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        assertArrayEquals(new ElementType[]{ElementType.TYPE}, target.value());
    }
}

