# Easy Config

Easy Config is a JSON5/TOML config library for Minecraft mods on Fabric and NeoForge. You just annotate a
plain Java class with `@Config`, hand it to a builder, and get back a `ConfigHolder` that handles
file path resolution, read/write operations, corrupt-file recovery, copies, validation, and 
lifecycle events.

**What Easy Config does:**
* **Configuration data layer:** Easy Config handles config files, including paths, loading, saving, default values, corruption recovery, atomic writes, and JSON5/TOML formats.
* **Safe state management:** provides validated snapshots, copies, runtime updates, resets, and custom state cloning.
* **Async and immutable configs:** choose synchronous, asynchronous, or immutable holders depending on your threading and lifecycle needs.
* **Restart guards:** mark fields as restart-only so runtime updates cannot change values that require a game restart.
* **Custom update API:** `update` and `updateAndSave` return an `UpdateResult` with success status and validation violations.
* **Fine-grained failure policies:** independently control how read, write, and update failures are handled, from graceful fallback to strict exceptions.
* **Lifecycle and event listeners:** hook into config load, save, update, and reset events, or use config-level hooks for normalization and validation.
* **Config groups:** manage multiple config files through a single holder, with independent formats and failure recovery.
* **Customizable entries:** control file paths, field names, comments, ignored fields, and other persistence details.

**Outside Easy Config's scope:**
- **Config screen:** Easy Config is a data layer — it does not render UI by itself.
- **Client/server sync:** use `onUpdate` to detect changes and dispatch packets.

## Setup

Artifacts are published to Maven Central under the group `com.gmalvestiti.minecraft`, with one
artifact per loader:

| Loader   | Artifact              |
|----------|-----------------------|
| Fabric   | `easyconfig-fabric`   |
| NeoForge | `easyconfig-neoforge` |

The library version tracks the Minecraft major version family, not the loader version:

| Minecraft | Easy Config |
|-----------|-------------|
| `1.21.x`  | `1.x.x`     |
| `26.x.x+` | `2.x.x`     |

**Embedded vs. standalone:** Embedding (via `include` / `jarJar`) bundles Easy Config inside your
mod jar so players install nothing extra. Standalone requires players to have Easy Config installed
as a separate mod.

<details>
<summary><b>Fabric — standalone</b></summary>

```groovy
repositories {
    mavenCentral()
}

dependencies {
    modImplementation 'com.gmalvestiti.minecraft:easyconfig-fabric:1.0.0' // or 2.0.0 for 26+
}
```

Declare the dependency so the loader refuses to start without it:

```json
{
  "depends": {
    "easyconfig": ">=1.0.0" // or 2.0.0 for 26+
  }
}
```
</details>

<details>
<summary><b>Fabric — embedded</b></summary>

```groovy
repositories {
    mavenCentral()
}

dependencies {
    modImplementation 'com.gmalvestiti.minecraft:easyconfig-fabric:1.0.0' // or 2.0.0 for 26+ 
    include 'com.gmalvestiti.minecraft:easyconfig-fabric:1.0.0' // or 2.0.0 for 26+
}
```
</details>

<details>
<summary><b>NeoForge — standalone</b></summary>

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'com.gmalvestiti.minecraft:easyconfig-neoforge:1.0.0' // or 2.0.0 for 26+
}
```

Declare the dependency in `META-INF/neoforge.mods.toml`:

```toml
[[dependencies.yourmodid]]
modId = "easyconfig"
type = "required"
versionRange = "[1.0.0,)" # or 2.0.0 for 26+
ordering = "NONE"
side = "BOTH"   
```
</details>

<details>
<summary><b>NeoForge — embedded</b></summary>

```groovy
repositories {
    mavenCentral()
}

dependencies {
    jarJar(implementation('com.gmalvestiti.minecraft:easyconfig-neoforge:1.0.0') { // or 2.0.0 for 26+
       version { 
           strictly '[1.0.0,)' // or 2.0.0 for 26+
           prefer '1.0.0' // or 2.0.0 for 26+
       } 
    })
}
```
</details>

## Quickstart

The three terminal builder methods correspond to three threading models:

| Method | Thread safety | Best fit |
|---|---|---|
| `create()` | Caller's thread only | Regular single-threaded config |
| `createAsync()` | Safe from any thread | Shared or background access |
| `createImmutable()` | Read-only, safe from any thread | Config that never mutates at runtime |

Declare the config class. Fields must be public, initialized to their defaults, and the class must
have a public no-argument constructor.

```java
@Config(name = "mymod") // format defaults to JSON5
public final class MyModConfig {
    public boolean showHints = true;
    public int hudScale = 2;
}
```

or for TOML:

```java
@Config(name = "mymod", format = ConfigFormat.TOML)
public final class MyModConfig {
    public boolean showHints = true;
    public int hudScale = 2;
}
```

Create the holder once during mod initialization and keep it for the lifetime of the mod. `create()`
resolves the file path, validates the model, loads any existing file (or writes the defaults if
none exists), and validates the loaded state — the holder is ready to read immediately after it
returns:

```java
public final class MyMod implements ModInitializer {

    public static final ConfigHolder<MyModConfig> CONFIG = EasyConfig.holder(MyModConfig.class)
        .modId("mymod")
        .create();
}
```

Read through `data()`, mutate through `update` / `updateAndSave`:

```java
if (MyMod.CONFIG.data().showHints) {
    // ...
}

MyMod.CONFIG.updateAndSave(config -> config.hudScale = 3);
```

That writes `config/mymod.json5`:

```json5
{
  "showHints": true,
  "hudScale": 3
}
```

## Documentation: [Wiki](https://github.com/gmalvestiti/easyconfig/wiki)

