package com.gmalvestiti.minecraft.easyconfig.storage;

import com.gmalvestiti.minecraft.easyconfig.api.ConfigFormat;
import com.gmalvestiti.minecraft.easyconfig.api.annotations.Config;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigError;
import com.gmalvestiti.minecraft.easyconfig.exception.ConfigScope;
import com.gmalvestiti.minecraft.easyconfig.reflection.ConfigFieldAccess;
import com.gmalvestiti.minecraft.easyconfig.storage.codec.ConfigCodec;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ConfigStorage {

    private static final String TEMP_SUFFIX = ".tmp";
    private static final String BACKUP_SUFFIX = ".corrupt-";
    private static final DateTimeFormatter BACKUP_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final int REPLACE_ATTEMPTS = 5;
    private static final long REPLACE_BACKOFF_MILLIS = 5L;

    private final ConfigPathResolver pathResolver;
    private final ConfigScope scope;
    private final ConfigFieldAccess fieldAccess;

    public ConfigStorage(
        ConfigPathResolver pathResolver,
        ConfigScope scope,
        ConfigFieldAccess fieldAccess
    ) {
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.fieldAccess = Objects.requireNonNull(fieldAccess, "fieldAccess");
    }

    public <V> V read(Class<V> configType) {

        Path path = pathResolver.resolveForConfig(configType);

        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException absent) {
            return null;
        } catch (IOException ex) {
            throw scope.exception(ConfigError.IO_LOAD_FAILURE, ex, path);
        }

        V data;
        try {
            data = codecFor(configType).read(text, configType);
        } catch (RuntimeException ex) {
            throw scope.exception(ConfigError.MALFORMED_CONFIG_DATA, ex, path);
        }
        if (data == null) {
            throw scope.exception(ConfigError.MALFORMED_CONFIG_DATA, path);
        }

        return data;
    }

    public void write(Class<?> configType, Object data) {
        Staged staged = stage(configType, data);
        try {
            staged.commit();
        } finally {
            staged.discard();
        }
    }

    public void writeAll(Map<Class<?>, Object> entries) {
        Objects.requireNonNull(entries, "entries");
        List<Staged> staged = new ArrayList<>(entries.size());
        try {
            entries.forEach((configType, data) -> staged.add(stage(configType, data)));
            staged.forEach(Staged::commit);
        } finally {
            staged.forEach(Staged::discard);
        }
    }

    private Staged stage(Class<?> configType, Object data) {
        Objects.requireNonNull(data, "data");
        Path path = pathResolver.resolveForConfig(configType);
        Path temp = temporarySibling(path);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temp, codecFor(configType).write(data), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ex) {
            deleteQuietly(temp);
            throw scope.exception(ConfigError.IO_SAVE_FAILURE, ex, path);
        }
        return new Staged(temp, path);
    }

    private ConfigCodec codecFor(Class<?> configType) {
        return ConfigCodec.of(ConfigFormat.of(configType));
    }

    public void backupCorrupted(Class<?> configType) {
        if (configType.getAnnotation(Config.class) != null) {
            backupFileOf(configType);
            return;
        }
        for (Field member : fieldAccess.configFieldsOf(configType)) {
            backupFileOf(member.getType());
        }
    }

    private void backupFileOf(Class<?> configType) {
        Path path = pathResolver.resolveForConfig(configType);
        if (!Files.exists(path)) {
            return;
        }
        String timestamp = BACKUP_TIMESTAMP.format(Instant.now());
        String unique = Long.toUnsignedString(System.nanoTime());
        Path backup = path.resolveSibling(path.getFileName() + BACKUP_SUFFIX + timestamp + "-" + unique);
        try {
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (NoSuchFileException ignored) {
        } catch (IOException ex) {
            throw scope.exception(ConfigError.IO_SAVE_FAILURE, ex, path);
        }
    }

    private static Path temporarySibling(Path path) {
        return path.resolveSibling(path.getFileName() + TEMP_SUFFIX + "-" + Long.toUnsignedString(System.nanoTime()));
    }

    private static void replace(Path source, Path target) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                move(source, target);
                return;
            } catch (AccessDeniedException contended) {
                if (attempt == REPLACE_ATTEMPTS - 1) {
                    throw contended;
                }
                backOff(attempt);
            }
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void backOff(int attempt) throws IOException {
        try {
            Thread.sleep(REPLACE_BACKOFF_MILLIS << attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException("Config write retry interrupted");
            interrupted.initCause(ex);
            throw interrupted;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private final class Staged {

        private final Path temp;
        private final Path target;
        private boolean committed;

        private Staged(Path temp, Path target) {
            this.temp = temp;
            this.target = target;
        }

        void commit() {
            try {
                replace(temp, target);
                committed = true;
            } catch (IOException ex) {
                throw scope.exception(ConfigError.IO_SAVE_FAILURE, ex, target);
            }
        }

        void discard() {
            if (!committed) {
                deleteQuietly(temp);
            }
        }
    }
}
