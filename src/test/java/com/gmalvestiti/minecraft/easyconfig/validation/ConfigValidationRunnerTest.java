package com.gmalvestiti.minecraft.easyconfig.validation;

import com.gmalvestiti.minecraft.easyconfig.api.ConfigExtension;
import com.gmalvestiti.minecraft.easyconfig.api.spi.Violation;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.support.TestFixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidationRunnerTest {

    @Test
    void testSkipsValidationWhenTheRootIsNotAnExtension() {
        ConfigValidationRunner<TestFixtures.SimpleConfig> runner =
            new ConfigValidationRunner<>(TestFixtures.SimpleConfig.class, TestFixtures.SCOPE, TestFixtures.fieldAccess());

        assertTrue(runner.run(new TestFixtures.SimpleConfig()).isEmpty());
        assertDoesNotThrow(() -> runner.runOrThrow(new TestFixtures.SimpleConfig()));
    }

    @Test
    void testSkipsValidationWhenTheRootKeepsTheDefault() {
        ConfigValidationRunner<TestFixtures.HookFailureConfig> runner =
            new ConfigValidationRunner<>(TestFixtures.HookFailureConfig.class, TestFixtures.SCOPE, TestFixtures.fieldAccess());

        assertTrue(runner.run(new TestFixtures.HookFailureConfig()).isEmpty());
    }

    @Test
    void testAcceptsValidCandidates() {
        ConfigValidationRunner<ValidatingConfig> runner = runner();

        assertDoesNotThrow(() -> runner.runOrThrow(new ValidatingConfig()));
        assertTrue(runner.run(new ValidatingConfig()).isEmpty());
    }

    @Test
    void testReportsViolationsWithoutThrowing() {
        ConfigValidationRunner<ValidatingConfig> runner = runner();
        ValidatingConfig config = new ValidatingConfig();
        config.violations = List.of(Violation.of("first", "broken"));

        List<Violation> violations = runner.run(config);

        assertEquals(1, violations.size());
        assertEquals("first", violations.get(0).id());
    }

    @Test
    void testRejectsInvalidCandidatesWithEveryViolationSummarised() {
        ConfigValidationRunner<ValidatingConfig> runner = runner();
        ValidatingConfig config = new ValidatingConfig();
        config.violations = List.of(
            Violation.of("first", "broken"),
            Violation.of("second", "also broken")
        );

        EasyConfigException failure = assertThrows(
            EasyConfigException.class, () -> runner.runOrThrow(config));

        assertEquals(ConfigError.VALIDATION_FAILED, failure.error());
        assertTrue(failure.getMessage().contains("ValidatingConfig"));
        assertTrue(failure.getMessage().contains("broken"));
        assertTrue(failure.getMessage().contains("also broken"));
        assertEquals(List.of("first", "second"), failure.violations().stream().map(Violation::id).toList());
    }

    @Test
    void testWrapsRuntimeValidationFailures() {
        ConfigValidationRunner<TestFixtures.ThrowingValidatorConfig> runner =
            new ConfigValidationRunner<>(TestFixtures.ThrowingValidatorConfig.class, TestFixtures.SCOPE, TestFixtures.fieldAccess());
        TestFixtures.ThrowingValidatorConfig config = new TestFixtures.ThrowingValidatorConfig();
        config.value = -1;

        EasyConfigException failure = assertThrows(
            EasyConfigException.class, () -> runner.run(config));

        assertEquals(ConfigError.VALIDATOR_FAILED, failure.error());
        assertTrue(failure.defect());
    }

    @Test
    void testRejectsInvalidViolationPayloads() {
        ConfigValidationRunner<ValidatingConfig> runner = runner();

        ValidatingConfig nullEntry = new ValidatingConfig();
        nullEntry.violations = new ArrayList<>(Arrays.asList((Violation) null));
        assertEquals(
            ConfigError.VALIDATOR_PRODUCED_NULL_VIOLATION,
            assertThrows(EasyConfigException.class, () -> runner.run(nullEntry)).error());

        ValidatingConfig blankId = new ValidatingConfig();
        blankId.violations = List.of(new TestFixtures.UncheckedViolation("  ", "bad"));
        assertEquals(
            ConfigError.VALIDATOR_PRODUCED_BLANK_ID,
            assertThrows(EasyConfigException.class, () -> runner.run(blankId)).error());

        ValidatingConfig nullList = new ValidatingConfig();
        nullList.violations = null;
        assertTrue(runner.run(nullList).isEmpty());
    }

    @Test
    void testValidatesGroupMembersAndTheRootTogether() {
        ConfigValidationRunner<TestFixtures.ExtendedGroupConfig> runner =
            new ConfigValidationRunner<>(
                TestFixtures.ExtendedGroupConfig.class, TestFixtures.SCOPE, TestFixtures.fieldAccess());

        TestFixtures.ExtendedGroupConfig group = new TestFixtures.ExtendedGroupConfig();
        group.member = new TestFixtures.ExtendedMemberConfig();
        group.plain = new TestFixtures.MemberBConfig();
        assertTrue(runner.run(group).isEmpty());

        group.member.value = 3;
        group.plain.value = -1;
        List<Violation> violations = runner.run(group);

        assertEquals(
            List.of("member.even", "group.plain"),
            violations.stream().map(Violation::id).toList(),
            "members validate before the root");
    }

    @Test
    void testValidatesMembersWhenTheGroupRootHasNoExtension() {
        ConfigValidationRunner<TestFixtures.PlainGroupWithExtendedMemberConfig> runner =
            new ConfigValidationRunner<>(
                TestFixtures.PlainGroupWithExtendedMemberConfig.class,
                TestFixtures.SCOPE,
                TestFixtures.fieldAccess());

        TestFixtures.PlainGroupWithExtendedMemberConfig group =
            new TestFixtures.PlainGroupWithExtendedMemberConfig();
        group.member = new TestFixtures.ExtendedMemberConfig();
        group.member.value = 3;

        EasyConfigException failure = assertThrows(
            EasyConfigException.class, () -> runner.runOrThrow(group));

        assertEquals(ConfigError.VALIDATION_FAILED, failure.error());
        assertTrue(failure.getMessage().contains("member value must be even"));
    }

    @Test
    void testToleratesAbsentGroupMembers() {
        ConfigValidationRunner<TestFixtures.ExtendedGroupConfig> runner =
            new ConfigValidationRunner<>(
                TestFixtures.ExtendedGroupConfig.class, TestFixtures.SCOPE, TestFixtures.fieldAccess());

        assertTrue(runner.run(new TestFixtures.ExtendedGroupConfig()).isEmpty());
    }

    @Test
    void testReturnsNoViolationsWhenTheExtensionOverridesNothing() {
        ConfigValidationRunner<TestFixtures.BareExtensionConfig> runner =
            new ConfigValidationRunner<>(
                TestFixtures.BareExtensionConfig.class, TestFixtures.SCOPE, TestFixtures.fieldAccess());

        assertTrue(runner.run(new TestFixtures.BareExtensionConfig()).isEmpty());
        assertDoesNotThrow(() -> runner.runOrThrow(new TestFixtures.BareExtensionConfig()));
    }

    private static ConfigValidationRunner<ValidatingConfig> runner() {
        return new ConfigValidationRunner<>(ValidatingConfig.class, TestFixtures.SCOPE, TestFixtures.fieldAccess());
    }

    public static class ValidatingConfig implements ConfigExtension {
        public List<Violation> violations = List.of();

        @Override
        public void validate(List<Violation> sink) {
            if (violations != null) {
                sink.addAll(violations);
            }
        }
    }
}

