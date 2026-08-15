package com.gmalvestiti.minecraft.easyconfig.reflection;

import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigFieldAccessTest {

    @Test
    void testCachesAndReadsConfigFields() throws Exception {
        ConfigFieldAccess access = new ConfigFieldAccess(TestFixtures.SCOPE);
        List<Field> fields = access.configFieldsOf(TestFixtures.GroupConfig.class);
        assertEquals(2, fields.size());

        List<Field> again = access.configFieldsOf(TestFixtures.GroupConfig.class);
        assertSame(fields, again);
    }

    @Test
    void testReadsAndWritesFieldValues() throws Exception {
        ConfigFieldAccess access = new ConfigFieldAccess(TestFixtures.SCOPE);
        TestFixtures.GroupConfig group = new TestFixtures.GroupConfig();
        Field field = access.configFieldsOf(TestFixtures.GroupConfig.class).get(0);

        TestFixtures.MemberAConfig value = new TestFixtures.MemberAConfig();
        access.write(field, group, value);

        Object read = access.read(field, group);
        assertSame(value, read);
    }

    @Test
    void testSkipsStaticTransientAndNonPublicFields() {
        ConfigFieldAccess access = new ConfigFieldAccess(TestFixtures.SCOPE);

        List<Field> ignoring = access.configFieldsOf(TestFixtures.GroupWithIgnoredFieldsConfig.class);
        assertEquals(List.of("valid"), ignoring.stream().map(Field::getName).toList(),
            "static and transient members must not be persisted");

        assertEquals(List.of("hidden"), access.configFieldsOf(TestFixtures.PrivateFieldGroupConfig.class)
                .stream().map(Field::getName).toList(),
            "non-public fields are accessible via trySetAccessible and should be persisted");
    }

    @Test
    void testWrapsIllegalFieldAccess() throws Exception {
        ConfigFieldAccess access = new ConfigFieldAccess(TestFixtures.SCOPE);
        Field field = String.class.getDeclaredField("value");
        assertThrows(RuntimeException.class, () -> access.read(field, new Object()));
        assertThrows(RuntimeException.class, () -> access.write(field, new Object(), null));
    }
}

