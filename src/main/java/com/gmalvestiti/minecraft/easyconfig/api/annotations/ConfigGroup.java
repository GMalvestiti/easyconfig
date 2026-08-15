package com.gmalvestiti.minecraft.easyconfig.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Groups several {@link Config} roots behind one holder.
 *
 * <p>Every non-static, non-transient field whose declared type carries {@link Config}
 * becomes its own file, keeping its own {@link Config#name()} file name and
 * {@link Config#path()} directories. Non-public fields are supported.
 * Other fields are ignored for validation and persistence:
 *
 * <pre>{@code
 * @Config(name = "client", path = "mymod") // config/mymod/client.json
 * public final class ClientConfig {
 *     public boolean showHints = true;
 * }
 *
 * @Config(name = "server", path = "mymod") // config/mymod/server.json
 * public final class ServerConfig {
 *     public int maxPlayers = 20;
 * }
 *
 * @ConfigGroup
 * public final class ModConfigs {
 *     public ClientConfig client = new ClientConfig();
 *     public ServerConfig server = new ServerConfig();
 *     public String label = "ignored"; // not a @Config type, never persisted
 * }
 *
 * ConfigHolder<ModConfigs> configs = EasyConfig.holder(ModConfigs.class).modId("mymod").create();
 * boolean hints = configs.data().client.showHints;
 * }</pre>
 *
 * <p>Members must not be final — group loads replace them — and no two members may
 * share a config type, since both fields would then claim one file. Holder construction rejects
 * either mistake.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigGroup {
}
