package com.gmalvestiti.minecraft.easyconfig.exception;

import com.gmalvestiti.minecraft.easyconfig.api.spi.Violation;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasyConfigExceptionTest {

    @Test
    void testStoresTheRawMessageBody() {
        EasyConfigException ex = EasyConfigException.of(
            ConfigError.IO_LOAD_FAILURE, "mymod", "msg", List.of(), null);

        assertEquals("msg", ex.getMessage());
        assertEquals("msg", ex.rawMessage());
        assertEquals(ConfigError.IO_LOAD_FAILURE, ex.error());
        assertNull(ex.getCause());
        assertTrue(ex.violations().isEmpty());
    }

    @Test
    void testStoresEverythingTheCanonicalConstructorReceives() {
        RuntimeException cause = new RuntimeException("cause");
        List<Violation> violations = List.of(Violation.of("rule", "broken"));

        EasyConfigException ex = new EasyConfigException(
            ConfigError.VALIDATION_FAILED, "msg", violations, cause);

        assertEquals("msg", ex.getMessage());
        assertEquals(ConfigError.VALIDATION_FAILED, ex.error());
        assertSame(cause, ex.getCause());
        assertEquals(List.of("rule"), ex.violations().stream().map(Violation::id).toList());
    }

    @Test
    void testCopiesAndFreezesTheViolationsItReceives() {
        List<Violation> mutable = new ArrayList<>();
        mutable.add(Violation.of("rule", "broken"));

        EasyConfigException ex = new EasyConfigException(
            ConfigError.VALIDATION_FAILED, "msg", mutable, null);
        mutable.clear();

        assertEquals(1, ex.violations().size(), "the exception must not alias the caller's list");
        assertThrows(UnsupportedOperationException.class, () -> ex.violations().clear());
    }

    @Test
    void testReadsTheDefectFlagFromItsErrorCode() {
        assertTrue(new EasyConfigException(ConfigError.UNEXPECTED_FAILURE, "msg", List.of(), null).defect());
        assertFalse(new EasyConfigException(ConfigError.VALIDATION_FAILED, "msg", List.of(), null).defect());
    }

    @Test
    void testDropsViolationsButKeepsTheFailureAcrossJavaSerialization() throws Exception {
        EasyConfigException original = new EasyConfigException(
            ConfigError.VALIDATION_FAILED, "msg", List.of(Violation.of("rule", "broken")), null);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }

        EasyConfigException restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (EasyConfigException) in.readObject();
        }

        assertTrue(restored.violations().isEmpty(), "transient violations must not survive deserialization");
        assertEquals(ConfigError.VALIDATION_FAILED, restored.error());
        assertEquals("msg", restored.getMessage());
    }

    @Test
    void testRejectsMissingErrorCodeAndViolations() {
        assertThrows(NullPointerException.class,
            () -> new EasyConfigException(null, "msg", List.of(), null));
        assertThrows(NullPointerException.class,
            () -> new EasyConfigException(ConfigError.IO_LOAD_FAILURE, "msg", null, null));
        assertThrows(NullPointerException.class,
            () -> EasyConfigException.of(ConfigError.IO_LOAD_FAILURE, null, "msg", List.of(), null));
        assertThrows(NullPointerException.class,
            () -> EasyConfigException.of(ConfigError.IO_LOAD_FAILURE, "mymod", null, List.of(), null));
    }
}

