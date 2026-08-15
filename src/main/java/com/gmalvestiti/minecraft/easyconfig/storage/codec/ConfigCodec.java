package com.gmalvestiti.minecraft.easyconfig.storage.codec;

import com.gmalvestiti.minecraft.easyconfig.api.ConfigFormat;

/**
 * Turns config objects into file text and back, for one {@link ConfigFormat}.
 *
 * <p>A codec owns the text format and nothing else: storage handles files, missing files, atomic
 * replacement, and failure attribution, and {@code ConfigBinder} handles the object binding both
 * codecs share. What is left here is the tree-to-text step, plus writing the comments declared
 * with {@code @Config} and {@code @ConfigEntry}.
 *
 * <pre>{@code
 * ConfigCodec codec = ConfigCodec.of(ConfigFormat.TOML);
 * String text = codec.write(config);
 * MyModConfig parsed = codec.read(text, MyModConfig.class);
 * }</pre>
 *
 * <p>Implementations may throw any {@link RuntimeException}; storage catches those and reports
 * {@code ConfigError.MALFORMED_CONFIG_DATA} for reads and {@code ConfigError.IO_SAVE_FAILURE}
 * for writes. They must be stateless, because one instance serves every holder in the process.
 */
public interface ConfigCodec {

    /**
     * Returns the codec that reads and writes {@code format}.
     */
    static ConfigCodec of(ConfigFormat format) {
        return switch (format) {
            case JSON -> Json5Codec.INSTANCE;
            case TOML -> TomlCodec.INSTANCE;
        };
    }

    /**
     * Parses file text into an instance of {@code type}.
     *
     * @param text raw file content; never {@code null}
     * @param type config class to populate; never {@code null}
     * @param <V> the config type being parsed
     * @return the parsed config, or {@code null} when the text holds no object, which storage
     *         reports as malformed data
     * @throws RuntimeException when the text cannot be parsed or bound to {@code type}
     */
    <V> V read(String text, Class<V> type);

    /**
     * Renders a config object as the text to write to disk, comments included.
     *
     * @param data config object to render; never {@code null}
     * @return the complete file content; never {@code null}
     * @throws RuntimeException when the object cannot be rendered
     */
    String write(Object data);
}
