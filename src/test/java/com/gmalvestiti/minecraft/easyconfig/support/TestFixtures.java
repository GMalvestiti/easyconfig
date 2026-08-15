package com.gmalvestiti.minecraft.easyconfig.support;

import com.gmalvestiti.minecraft.easyconfig.api.ConfigFormat;
import com.gmalvestiti.minecraft.easyconfig.api.annotations.Config;
import com.gmalvestiti.minecraft.easyconfig.api.annotations.ConfigEntry;
import com.gmalvestiti.minecraft.easyconfig.api.annotations.ConfigGroup;
import com.gmalvestiti.minecraft.easyconfig.api.FailurePolicy;
import com.gmalvestiti.minecraft.easyconfig.api.ConfigExtension;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigExceptionHandler;
import com.gmalvestiti.minecraft.easyconfig.storage.ConfigStorage;
import com.gmalvestiti.minecraft.easyconfig.api.spi.Violation;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;
import com.gmalvestiti.minecraft.easyconfig.layout.ConfigLayoutContext;
import com.gmalvestiti.minecraft.easyconfig.reflection.ConfigFieldAccess;
import com.gmalvestiti.minecraft.easyconfig.storage.ConfigPathResolver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class TestFixtures {

    public static final ConfigScope SCOPE = new ConfigScope("mod");

    private TestFixtures() {
    }

    /**
     * Fallback-everywhere handler for tests that only care about the happy path.
     */
    public static ConfigExceptionHandler fallbackHandler(ConfigStorage storage) {
        return handler(storage::backupCorrupted, FailurePolicy.FALLBACK, FailurePolicy.STRICT,
            FailurePolicy.FALLBACK, SCOPE);
    }

    /**
     * Layout collaborators wired against {@code storage} with a forgiving handler.
     */
    public static ConfigLayoutContext layoutContext(ConfigStorage storage) {
        return new ConfigLayoutContext(SCOPE, storage, fallbackHandler(storage), fieldAccess());
    }

    public static ConfigFieldAccess fieldAccess() {
        return new ConfigFieldAccess(SCOPE);
    }

    /**
     * Storage rooted at {@code baseDirectory}, wired with the standard test scope.
     */
    public static ConfigStorage storage(Path baseDirectory) {
        return new ConfigStorage(new ConfigPathResolver(baseDirectory, SCOPE), SCOPE, fieldAccess());
    }

    public static ConfigExceptionHandler handler(
        Consumer<Class<?>> backupCorrupted,
        FailurePolicy readPolicy,
        FailurePolicy writePolicy,
        FailurePolicy updatePolicy,
        ConfigScope scope
    ) {
        return new ConfigExceptionHandler(scope, readPolicy, writePolicy, updatePolicy, backupCorrupted);
    }

    @Config(name = "hook-failure")
    public static class HookFailureConfig implements ConfigExtension {
        public int value = 1;
        public boolean explicit;

        @Override
        public void afterLoad() {
            throw failure();
        }

        @Override
        public void beforeSave() {
            throw failure();
        }

        private RuntimeException failure() {
            return explicit
                ? SCOPE.exception(ConfigError.VALIDATION_FAILED, "keep")
                : new IllegalStateException("boom");
        }
    }

    @Config(name = "throwing-validator")
    public static class ThrowingValidatorConfig implements ConfigExtension {
        public int value = 1;

        @Override
        public void validate(List<Violation> violations) {
            if (value < 0) {
                throw new NullPointerException("boom");
            }
        }
    }

    @Config(name = "simple")
    public static class SimpleConfig {
        public int value = 1;
        public String text = "default";
    }

    /**
     * Exercises every {@link ConfigEntry} attribute at once: a renamed field, a commented field,
     * and a field that may only change between runs.
     */
    @Config(name = "entries", comment = "Settings for the entry fixture.")
    public static class EntryConfig {
        @ConfigEntry(name = "hud_scale", comment = "Scale of the HUD overlay.")
        public int hudScale = 2;

        @ConfigEntry(restart = true)
        public String worldPreset = "default";

        @ConfigEntry(name = "nested", comment = "Grouped options.")
        public EntrySection section = new EntrySection();
    }

    public static class EntrySection {
        @ConfigEntry(name = "max_depth")
        public int maxDepth = 4;

        @ConfigEntry(restart = true)
        public boolean experimental;
    }

    /**
     * Comment text that would break the file if it reached the codec unescaped.
     */
    @Config(name = "awkward-comments", comment = {"Two lines,", "so this renders as a block."})
    public static class AwkwardCommentConfig {
        @ConfigEntry(comment = {"Contains */ a block terminator", "and // a line marker"})
        public int value = 1;
    }

    @Config(
        name = "awkward-comments-toml",
        format = ConfigFormat.TOML,
        comment = {"Two lines,", "so this renders as a block."})
    public static class AwkwardCommentTomlConfig {
        @ConfigEntry(comment = {"Contains */ a block terminator", "and // a line marker"})
        public int value = 1;
    }

    @Config(name = "toml-fixture", format = ConfigFormat.TOML, comment = "A TOML-backed fixture.")
    public static class TomlConfig {
        @ConfigEntry(comment = "How loud, from 0 to 10.")
        public int volume = 5;

        @ConfigEntry(name = "display_name")
        public String displayName = "player";

        public List<String> tags = new ArrayList<>(List.of("a", "b"));

        public TomlSection section = new TomlSection();
    }

    public static class TomlSection {
        public double ratio = 0.5;
        public boolean enabled = true;
    }

    /**
     * Implements the extension interface but overrides nothing, so every hook is the default.
     */
    @Config(name = "bare-extension")
    public static class BareExtensionConfig implements ConfigExtension {
        public int value = 1;
    }

    @Config(name = "with-extension")
    public static class ConfigWithExtension implements ConfigExtension {
        public int value = 1;
        public boolean afterLoadCalled;
        public boolean beforeSaveCalled;

        @Override
        public void afterLoad() {
            afterLoadCalled = true;
        }

        @Override
        public void beforeSave() {
            beforeSaveCalled = true;
        }

        @Override
        public void validate(List<Violation> violations) {
            if (value < 0) {
                violations.add(Violation.of("nonNegative", "value must be >= 0"));
            }
        }
    }

    @Config(name = "invalid-defaults")
    public static class InvalidDefaultsConfig implements ConfigExtension {
        public int value = -1;

        @Override
        public void validate(List<Violation> violations) {
            if (value < 0) {
                violations.add(Violation.of("nonNegative", "value must be >= 0"));
            }
        }
    }

    @Config(name = "child")
    public static class ChildConfig {
        public int nested = 7;
    }

    @Config(name = "parent")
    public static class ParentWithConfigRef {
        public ChildConfig child = new ChildConfig();
    }

    @Config(name = "member-a")
    public static class MemberAConfig {
        public int value = 10;
    }

    @Config(name = "member-b")
    public static class MemberBConfig {
        public int value = 20;
    }

    /**
     * Gson refuses to serialize {@link Class} values, which is the cheapest honest way to make a
     * write fail without stubbing out the storage layer.
     */
    @Config(name = "unserializable")
    public static class UnserializableConfig {
        public Class<?> marker = String.class;
    }

    @ConfigGroup
    public static class GroupWithUnserializableMemberConfig {
        public MemberAConfig memberA;
        public UnserializableConfig broken;
    }

    @ConfigGroup
    public static class GroupConfig {
        public MemberAConfig memberA;
        public MemberBConfig memberB;
    }

    @Config(name = "extended-member")
    public static class ExtendedMemberConfig implements ConfigExtension {
        public int value = 2;
        public boolean afterLoadCalled;
        public boolean beforeSaveCalled;

        @Override
        public void afterLoad() {
            afterLoadCalled = true;
        }

        @Override
        public void beforeSave() {
            beforeSaveCalled = true;
        }

        @Override
        public void validate(List<Violation> violations) {
            if ((value & 1) != 0) {
                violations.add(Violation.of("member.even", "member value must be even"));
            }
        }
    }

    /**
     * A group whose root and member both carry extensions, so both levels must run.
     */
    @ConfigGroup
    public static class ExtendedGroupConfig implements ConfigExtension {
        public ExtendedMemberConfig member;
        public MemberBConfig plain;

        public transient List<String> hookOrder = new ArrayList<>();

        @Override
        public void afterLoad() {
            hookOrder.add("root.afterLoad");
        }

        @Override
        public void beforeSave() {
            hookOrder.add("root.beforeSave");
        }

        @Override
        public void validate(List<Violation> violations) {
            if (plain != null && plain.value < 0) {
                violations.add(Violation.of("group.plain", "plain value must be >= 0"));
            }
        }
    }

    /**
     * A group root with no extension of its own, used to prove members still validate.
     */
    @ConfigGroup
    public static class PlainGroupWithExtendedMemberConfig {
        public ExtendedMemberConfig member;
    }

    @ConfigGroup
    public static class PrivateFieldGroupConfig {
        private MemberAConfig hidden = new MemberAConfig();
    }

    @ConfigGroup
    public static class DuplicateMemberGroupConfig {
        public MemberAConfig first;
        public MemberAConfig second;
    }

    /**
     * Declares the same file name as {@link MemberAConfig}, so both types compete for one file.
     */
    @Config(name = "member-a")
    public static class ClashingMemberConfig {
        public int value = 30;
    }

    @ConfigGroup
    public static class GroupWithIgnoredFieldsConfig {
        public static MemberAConfig STATIC_MEMBER = new MemberAConfig();
        public transient MemberBConfig transientMember = new MemberBConfig();
        public MemberAConfig valid = new MemberAConfig();
    }

    public static class NoDefaultConstructorConfig {
        public NoDefaultConstructorConfig(String ignored) {
        }
    }

    public static class ThrowingConstructorConfig {
        public ThrowingConstructorConfig() {
            throw new IllegalStateException("boom");
        }
    }

    /**
     * Skips the checks {@link Violation#of} performs, so tests can hand the runner input it
     * would otherwise refuse to construct — a blank id, for instance.
     */
    public record UncheckedViolation(String id, String message) implements Violation {
    }
}
