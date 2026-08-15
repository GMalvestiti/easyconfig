package com.gmalvestiti.minecraft.easyconfig.engine;

import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLifecycleHooksTest {

    @Test
    void testSkipsHooksWhenTheRootIsNotAnExtension() {
        ConfigLifecycleHooks<TestFixtures.SimpleConfig> hooks = new ConfigLifecycleHooks<>(
            TestFixtures.SimpleConfig.class,
            TestFixtures.SCOPE,
            TestFixtures.fieldAccess()
        );
        assertFalse(hooks.hasBeforeSave());
        hooks.afterLoad(new TestFixtures.SimpleConfig());
        hooks.beforeSave(new TestFixtures.SimpleConfig());
    }

    @Test
    void testInvokesOverriddenHooksOnTheInstanceItself() {
        ConfigLifecycleHooks<TestFixtures.ConfigWithExtension> hooks = new ConfigLifecycleHooks<>(
            TestFixtures.ConfigWithExtension.class,
            TestFixtures.SCOPE,
            TestFixtures.fieldAccess()
        );
        assertTrue(hooks.hasBeforeSave());

        TestFixtures.ConfigWithExtension loaded = new TestFixtures.ConfigWithExtension();
        hooks.afterLoad(loaded);
        assertTrue(loaded.afterLoadCalled);

        TestFixtures.ConfigWithExtension candidate = new TestFixtures.ConfigWithExtension();
        hooks.beforeSave(candidate);
        assertTrue(candidate.beforeSaveCalled);
        assertFalse(candidate.afterLoadCalled);
    }

    @Test
    void testRunsGroupMemberAndRootHooksInMirroredOrder() {
        ConfigLifecycleHooks<TestFixtures.ExtendedGroupConfig> hooks = new ConfigLifecycleHooks<>(
            TestFixtures.ExtendedGroupConfig.class,
            TestFixtures.SCOPE,
            TestFixtures.fieldAccess()
        );
        assertTrue(hooks.hasBeforeSave());

        TestFixtures.ExtendedGroupConfig group = new TestFixtures.ExtendedGroupConfig();
        group.member = new TestFixtures.ExtendedMemberConfig();

        hooks.afterLoad(group);
        assertTrue(group.member.afterLoadCalled, "member afterLoad must run inside a group");
        assertEquals(List.of("root.afterLoad"), group.hookOrder);

        hooks.beforeSave(group);
        assertTrue(group.member.beforeSaveCalled, "member beforeSave must run inside a group");
        assertEquals(List.of("root.afterLoad", "root.beforeSave"), group.hookOrder);
    }

    @Test
    void testRunsMemberHooksWhenTheGroupRootHasNoExtension() {
        ConfigLifecycleHooks<TestFixtures.PlainGroupWithExtendedMemberConfig> hooks = new ConfigLifecycleHooks<>(
            TestFixtures.PlainGroupWithExtendedMemberConfig.class,
            TestFixtures.SCOPE,
            TestFixtures.fieldAccess()
        );
        assertTrue(hooks.hasBeforeSave());

        TestFixtures.PlainGroupWithExtendedMemberConfig group = new TestFixtures.PlainGroupWithExtendedMemberConfig();
        group.member = new TestFixtures.ExtendedMemberConfig();

        hooks.afterLoad(group);
        hooks.beforeSave(group);

        assertTrue(group.member.afterLoadCalled);
        assertTrue(group.member.beforeSaveCalled);
    }

    @Test
    void testToleratesAbsentGroupMembers() {
        ConfigLifecycleHooks<TestFixtures.ExtendedGroupConfig> hooks = new ConfigLifecycleHooks<>(
            TestFixtures.ExtendedGroupConfig.class,
            TestFixtures.SCOPE,
            TestFixtures.fieldAccess()
        );

        TestFixtures.ExtendedGroupConfig group = new TestFixtures.ExtendedGroupConfig();
        assertDoesNotThrow(() -> hooks.afterLoad(group));
        assertDoesNotThrow(() -> hooks.beforeSave(group));
    }

    @Test
    void testCallsDefaultHooksHarmlesslyWhenTheExtensionOverridesNothing() {
        ConfigLifecycleHooks<TestFixtures.BareExtensionConfig> hooks = new ConfigLifecycleHooks<>(
            TestFixtures.BareExtensionConfig.class,
            TestFixtures.SCOPE,
            TestFixtures.fieldAccess()
        );

        TestFixtures.BareExtensionConfig config = new TestFixtures.BareExtensionConfig();
        assertDoesNotThrow(() -> hooks.afterLoad(config));
        assertDoesNotThrow(() -> hooks.beforeSave(config));
        assertEquals(1, config.value);

        assertFalse(
            hooks.hasBeforeSave(),
            "an extension that overrides nothing must not trigger a save-time clone");
    }

    @Test
    void testWrapsRuntimeFailuresAndRethrowsEasyConfigException() {
        ConfigLifecycleHooks<TestFixtures.HookFailureConfig> hooks = new ConfigLifecycleHooks<>(
            TestFixtures.HookFailureConfig.class,
            TestFixtures.SCOPE,
            TestFixtures.fieldAccess()
        );

        TestFixtures.HookFailureConfig runtimeFailure = new TestFixtures.HookFailureConfig();
        assertThrows(EasyConfigException.class, () -> hooks.afterLoad(runtimeFailure));
        assertThrows(EasyConfigException.class, () -> hooks.beforeSave(runtimeFailure));

        TestFixtures.HookFailureConfig explicitFailure = new TestFixtures.HookFailureConfig();
        explicitFailure.explicit = true;
        assertThrows(EasyConfigException.class, () -> hooks.afterLoad(explicitFailure));
        assertThrows(EasyConfigException.class, () -> hooks.beforeSave(explicitFailure));
    }
}

