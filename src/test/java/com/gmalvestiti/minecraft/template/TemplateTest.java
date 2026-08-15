package com.gmalvestiti.minecraft.template;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TemplateTest {

    @BeforeAll
    static void beforeAll() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void testRegistries() {
        assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("minecraft", "diamond")));
    }

    @Test
    void testDependencies() {
        assertDoesNotThrow(() -> {
            Cache<String, String> cache = Caffeine.newBuilder().build();
            cache.put("key", "value");
        });
    }
}
