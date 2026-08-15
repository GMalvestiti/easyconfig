package com.gmalvestiti.minecraft.easyconfig.storage;

import com.gmalvestiti.minecraft.easyconfig.api.annotations.Config;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.exception.EasyConfigException;

import java.lang.ref.WeakReference;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ConfigPathResolver {

    private static final boolean CASE_INSENSITIVE_PATHS =
        System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    private static final Map<PathKey, WeakReference<Class<?>>> PROCESS_OWNERS = new ConcurrentHashMap<>();

    private final ConfigScope scope;
    private final Path baseDirectory;
    private final Map<Class<?>, Path> resolved = new ConcurrentHashMap<>();

    public ConfigPathResolver(Path baseDirectory, ConfigScope scope) {
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
        this.scope = scope;
    }

    public Path resolveForConfig(Class<?> configType) {
        return resolved.computeIfAbsent(configType, this::resolve);
    }

    private Path resolve(Class<?> configType) {
        Config config = configType.getAnnotation(Config.class);
        if (config == null) {
            throw scope.exception(ConfigError.MISSING_CONFIG_MARKER, configType.getName());
        }

        String extension = config.format().extension();
        Path directory = resolveDirectory(configType, config.path());
        Path file = directory.resolve(normalizeFileName(configType, config.name(), extension)).normalize();
        if (!file.startsWith(baseDirectory)) {
            throw invalidPath(configType, config.path());
        }

        return claim(configType, file);
    }

    private Path claim(Class<?> configType, Path file) {
        PROCESS_OWNERS.compute(PathKey.of(file), (key, existing) -> {
            Class<?> owner = existing == null ? null : existing.get();
            if (owner != null && owner != configType) {
                throw scope.exception(
                    ConfigError.CONFLICTING_CONFIG_PATH, configType.getName(), file, owner.getName());
            }
            return owner == configType ? existing : new WeakReference<>(configType);
        });
        return file;
    }

    private Path resolveDirectory(Class<?> configType, String declaredPath) {
        if (declaredPath.isBlank()) {
            return baseDirectory;
        }

        Path relative = toPath(declaredPath, () -> invalidPath(configType, declaredPath));
        if (relative.isAbsolute()) {
            throw invalidPath(configType, declaredPath);
        }

        return baseDirectory.resolve(relative).normalize();
    }

    private String normalizeFileName(Class<?> configType, String name, String extension) {
        if (name.isBlank() || containsPathSeparator(name) || name.equalsIgnoreCase(extension)) {
            throw invalidName(configType, name);
        }

        Path candidate = toPath(name, () -> invalidName(configType, name));
        if (candidate.isAbsolute() || candidate.getNameCount() != 1) {
            throw invalidName(configType, name);
        }

        return endsWith(name, extension) ? name : name + extension;
    }

    private static Path toPath(String value, Supplier<EasyConfigException> onFailure) {
        try {
            return Path.of(value);
        } catch (InvalidPathException ex) {
            throw onFailure.get();
        }
    }

    private static boolean endsWith(String name, String extension) {
        int offset = name.length() - extension.length();
        return offset > 0 && name.regionMatches(true, offset, extension, 0, extension.length());
    }

    private static boolean containsPathSeparator(String value) {
        return value.indexOf('/') >= 0 || value.indexOf('\\') >= 0;
    }

    private EasyConfigException invalidName(Class<?> configType, String name) {
        return scope.exception(ConfigError.INVALID_CONFIG_NAME, configType.getName(), name);
    }

    private EasyConfigException invalidPath(Class<?> configType, String path) {
        return scope.exception(ConfigError.INVALID_CONFIG_PATH, configType.getName(), path);
    }

    private record PathKey(String value) {
        private static PathKey of(Path path) {
            String value = path.toAbsolutePath().normalize().toString();
            if (CASE_INSENSITIVE_PATHS) {
                value = value.toLowerCase(Locale.ROOT);
            }
            return new PathKey(value);
        }
    }
}
