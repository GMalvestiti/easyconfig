# EasyConfig

EasyConfig is a small config library for Minecraft mods on Fabric and NeoForge, writing JSON5 or
TOML from the same config class.
You declare a normal Java class, annotate it with `@Config`, and EasyConfig handles the file path,
read/write lifecycle, validation, and the live holder that publishes the current state.

There is no config screen, no generator, and no annotation processor. You keep your config model in
plain Java, create one holder from the builder, and read or update values through that holder.

By design, EasyConfig does not manage client/server config sync or ship config screens. That would
pull in Minecraft or loader APIs that belong outside a data-focused library. Instead, it provides
the holder, callbacks, commands, and data access points you need to wire those integrations in your
mod.

The API is intentionally explicit:

- choose the holder mode with `create()`, `createAsync()`, or `createImmutable()`
- choose the file format with `@Config(format = ...)`
- choose the failure strategy per operation family with `STRICT` or `FALLBACK`
- let EasyConfig manage the rest: path resolution, corrupt-file backups, deep copies, lifecycle hooks,
  and validation

## Setup

Artifacts are published to Maven Central under the group `com.gmalvestiti.minecraft`, with one
artifact per loader:

| Loader   | Artifact              |
|----------|-----------------------|
| Fabric   | `easyconfig-fabric`   |
| NeoForge | `easyconfig-neoforge` |

The EasyConfig version follows:

| Minecraft | EasyConfig |
|-----------|------------|
| `1.21.x`  | `1.x.x`    |
| `26.x.x+` | `2.x.x`    |

This is intentional: the library version reflects the mod family, not the loader runtime version.

Pick **embedded** if you want EasyConfig bundled inside your mod so users install nothing extra
(recommended for most mods). Pick **not embedded** if you would rather declare it as a normal mod
dependency that users download separately.

<details>
<summary><b>Fabric — embedded</b></summary>

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    modImplementation("com.gmalvestiti.minecraft:easyconfig-fabric:1.0.0")
    include("com.gmalvestiti.minecraft:easyconfig-fabric:1.0.0")
}
```

`include` nests the mod jar inside yours, so no `depends` entry is required in `fabric.mod.json`.
</details>

<details>
<summary><b>Fabric — not embedded</b></summary>

```kotlin
dependencies {
    modImplementation("com.gmalvestiti.minecraft:easyconfig-fabric:1.0.0")
}
```

Then declare the dependency so the loader refuses to start without it:

```json
{
  "depends": {
    "easyconfig": ">=1.0.0"
  }
}
```
</details>

<details>
<summary><b>NeoForge — embedded</b></summary>

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    jarJar(implementation("com.gmalvestiti.minecraft:easyconfig-neoforge:1.0.0") {
        version {
            strictly("[1.0.0,)")
            prefer("1.0.0")
        }
    })
}
```
</details>

<details>
<summary><b>NeoForge — not embedded</b></summary>

```kotlin
dependencies {
    implementation("com.gmalvestiti.minecraft:easyconfig-neoforge:1.0.0")
}
```

Then declare the dependency in `META-INF/neoforge.mods.toml`:

```toml
[[dependencies.yourmodid]]
modId = "easyconfig"
type = "required"
versionRange = "[1.0.0,)"
ordering = "NONE"
side = "BOTH"
```
</details>

## Quickstart

Use the smallest configuration model that matches your use case:

- `create()` for normal single-threaded config access
- `createAsync()` for shared access from multiple threads
- `createImmutable()` for read-mostly config that should never be mutated at runtime

Declare the config class. Fields are public, initialized to their defaults, and the class needs a
public no-argument constructor. Name the file after your mod — the config directory is shared with
every other mod:

```java
@Config(name = "mymod")
public final class MyModConfig {
    public boolean showHints = true;
    public int hudScale = 2;
}
```

Create the holder once, during mod initialization, and keep it. `create()` writes the config file
if it is missing and loads it if it is there, so the holder is ready to read immediately:

```java
public final class MyMod implements ModInitializer {

    public static final ConfigHolder<MyModConfig> CONFIG = EasyConfig.holder(MyModConfig.class)
        .modId("mymod")
        .create();
}
```

If you need to keep the config immutable after startup, use `createImmutable()` instead. The
`update` and `reset` calls still exist, but they are refused through the configured update policy.

Call `load()` later only to pick up a file that changed after startup.

Read through `data()`, write through `update` / `updateAndSave`:

```java
if (MyMod.CONFIG.data().showHints) {
    // ...
}

MyMod.CONFIG.updateAndSave(config -> config.hudScale = 3);
```

That writes `config/mymod.json`:

```json
{
  "showHints": true,
  "hudScale": 3
}
```

## Documentation

<details>
<summary><b>Names and paths — <code>name</code> is the file, <code>path</code> is the directory</b></summary>

The two `@Config` attributes have strictly separate jobs:

- **`name`** — the **file**. Exactly one file name, and the only place the file name comes from.
- **`path`** — the **directory** (or nested directories) that will contain it. Never a file name,
  never a file extension.

They combine under the holder's base directory:

```
<baseDir> / <@Config.path()> / <@Config.name()>.<format extension>
   dirs          dirs                     file
```

`baseDir` defaults to the platform config directory (`config/`), `path` defaults to `""`, meaning
the file sits directly in `baseDir`, and the extension comes from `@Config.format()` — `.json` by
default, `.toml` for `ConfigFormat.TOML`. The tables below use the default JSON format.

| Declaration | Directory | File | Resolved |
|---|---|---|---|
| `@Config(name = "mymod")` | `config/` | `mymod.json` | `config/mymod.json` |
| `@Config(name = "mymod.json")` | `config/` | `mymod.json` | `config/mymod.json` |
| `@Config(name = "client", path = "mymod")` | `config/mymod/` | `client.json` | `config/mymod/client.json` |
| `@Config(name = "hud", path = "mymod/gui")` | `config/mymod/gui/` | `hud.json` | `config/mymod/gui/hud.json` |
| `@Config(name = "mymod", format = TOML)` | `config/` | `mymod.toml` | `config/mymod.toml` |

Any directory in `path` that does not exist yet is created on the first save.

Rules for `name` (a file name): a single file-name component only. It must not be blank, must not
be just the extension, and must not contain `/` or `\` — a name is never a place to put directories
in. The extension of the declared format is appended when missing (case insensitive, so
`MyMod.JSON` is left alone).

Rules for `path` (directories): relative only, `/`-separated, and it must stay inside `baseDir`
after normalization. Absolute paths and `..` escapes are rejected. Do not put the file name here:

```java
@Config(name = "client", path = "mymod")             // config/mymod/client.json
@Config(name = "client", path = "mymod/client.json") // config/mymod/client.json/client.json — wrong
```

`modId` is **not** part of the path, so the file name has to carry that information itself:

- **One config file — name it after your mod.** `@Config(name = "mymod")` → `config/mymod.json`.
- **Several config files — put your mod id in `path`.** `@Config(name = "client", path = "mymod")`
  → `config/mymod/client.json`.

Avoid generic names in the shared config root. Two mods that both declare
`@Config(name = "config")` — or `"client"`, or `"settings"` — collide on the same file, and the
second holder to be created fails with `CONFLICTING_CONFIG_PATH`:

```java
// One file — recommended
@Config(name = "mymod")
public final class MyModConfig { }

// Several files — your own directory
@Config(name = "client", path = "mymod")
public final class ClientConfig { }

// Avoid — config/client.json is not yours alone
@Config(name = "client")
public final class ClientConfig { }
```

To move the whole root elsewhere — a per-world directory, or a temp directory in tests — use
`baseDir`, which is also a directory and stacks in front of `path`:

```java
EasyConfig.holder(MyModConfig.class)
    .modId("mymod")
    .baseDir(FabricLoader.getInstance().getConfigDir().resolve("mymod"))
    .create();
```

Supplying `baseDir` also means the platform config directory is never queried, which is what makes
holders usable in plain unit tests.
</details>

<details>
<summary><b>Holder implementations — threading</b></summary>

The terminal builder method you choose is the actual holder mode. All three expose the same
`ConfigHolder` API; `createAsync()` returns the wider `AsyncConfigHolder`.

| Method | Where work runs | Thread safety | Best fit |
|---|---|---|---|
| `create()` | the calling thread, inline | confine to one thread (e.g. the server thread) | regular runtime config |
| `createAsync()` | shared config worker thread | safe from any thread | shared or async access |
| `createImmutable()` | frozen at the state loaded during the call | read-only, safe from any thread | read-mostly config |

```java
ConfigHolder<MyModConfig> simple = EasyConfig.holder(MyModConfig.class)
    .modId("mymod")
    .create();

AsyncConfigHolder<MyModConfig> async = EasyConfig.holder(MyModConfig.class)
    .modId("mymod")
    .createAsync();

ConfigHolder<MyModConfig> immutable = EasyConfig.holder(MyModConfig.class)
    .modId("mymod")
    .createImmutable();
```

With `createAsync`, the blocking methods (`load`, `save`, `update`) submit to the worker and wait, so
never call them from inside a config hook — that is reported as `BLOCKING_CALL_ON_CONFIG_THREAD`.
Use `loadAsync`, `saveAsync`, `updateAsync`, and `updateAndSaveAsync` when you are already on that
thread or simply do not want to block. Those methods exist only on `AsyncConfigHolder`.

With `createImmutable`, `update` and `reset` are refused through the failure policy; `load` and
`save` still work.
</details>

<details>
<summary><b>Reading and writing</b></summary>

```java
MyModConfig shared = holder.data();   // cheap, shared, treat as read-only
MyModConfig mine = holder.copy();     // deep copy you own, safe to mutate

holder.update(config -> config.showHints = false);   // publish in memory
holder.updateAndSave(config -> config.hudScale = 3); // publish and write to disk
```

Holders built with `createAsync()` add non-blocking variants:

```java
AsyncConfigHolder<MyModConfig> holder = /* ... */;
holder.saveAsync();                                  // does not block the caller
```

`data()` returns the live published instance — read from it on hot paths, never mutate it. `copy()`
runs the configured cloner, so use it only when you need isolation, such as taking a stable reading
of several fields at once.

The mutator passed to `update` receives a private candidate. It is validated first, and only an
accepted candidate is published, so a rejected edit can never leave the config half-applied.
</details>

<details>
<summary><b>Failure policies</b></summary>

Each operation family has its own policy, defaulting to `FALLBACK`:

```java
EasyConfig.holder(MyModConfig.class)
    .modId("mymod")
    .readFailurePolicy(FailurePolicy.FALLBACK)   // corrupt file -> back it up, restore defaults
    .writeFailurePolicy(FailurePolicy.STRICT)    // save failed -> throw
    .updateFailurePolicy(FailurePolicy.FALLBACK) // invalid edit -> discard, keep old state
    .create();
```

`STRICT` throws `EasyConfigException`; `FALLBACK` logs and degrades. Defects — a validator that
throws, an invalid config model, a `null` mutator — always propagate regardless of policy. Every
failure carries a `ConfigError` code, so branch on that code rather than on message text.

Both policies apply to `create()` as well: with `FALLBACK`, a corrupt or invalid file found at
startup is moved aside as `<file name>.corrupt-<timestamp>` and the defaults are written in its
place; with `STRICT`, `create()` throws and the file is left untouched.
</details>

<details>
<summary><b>Validation and lifecycle hooks</b></summary>

Implement `ConfigExtension` on the config class itself:

```java
@Config(name = "mymod")
public final class MyModConfig implements ConfigExtension {

    public int hudScale = 2;

    @Override
    public void afterLoad() {                       // fix up loaded values
        hudScale = Math.max(hudScale, 1);
    }

    @Override
    public void beforeSave() { }                    // normalize what goes to disk

    @Override
    public void validate(List<Violation> violations) {
        if (hudScale < 1 || hudScale > 8) {
            violations.add(Violation.of("hud-scale.range", "hudScale must be 1..8, was " + hudScale));
        }
    }
}
```

`validate` must be side-effect free — correct values in `afterLoad`, report them in `validate`.
Under a `FALLBACK` update policy the violations come back on the result:

```java
UpdateResult result = holder.updateAndSave(config -> config.hudScale = 99);
if (!result.accepted()) {
    result.violations().forEach(violation -> LOGGER.warn("{}: {}", violation.id(), violation.message()));
}
```

Under `STRICT`, the same violations ride on `EasyConfigException.violations()`.
</details>

<details>
<summary><b>Config groups — several files, one holder</b></summary>

Annotate a shell class with `@ConfigGroup`; every public field whose type is a `@Config` class
becomes its own file, keeping its own `name` and `path`. This is where per-file names like
`client` and `server` belong — inside your own `path`, never loose in the config root:

```java
@Config(name = "client", path = "mymod")
public final class ClientConfig { public boolean showHints = true; }

@Config(name = "server", path = "mymod")
public final class ServerConfig { public int maxPlayers = 20; }

@ConfigGroup
public final class ModConfigs {
    public ClientConfig client = new ClientConfig();
    public ServerConfig server = new ServerConfig();
}
```

```java
ConfigHolder<ModConfigs> configs = EasyConfig.holder(ModConfigs.class).modId("mymod").create();
boolean hints = configs.data().client.showHints;   // config/mymod/client.json + config/mymod/server.json
```

Members must be public, non-final, and no two members may share a config type. Group members and
the group root may each implement `ConfigExtension`; members run first on load and validate, root
first on save.
</details>

<details>
<summary><b>File formats — JSON5 and TOML</b></summary>

The format belongs to the config class, because it decides what the file on disk looks like:

```java
@Config(name = "mymod")                            // config/mymod.json  (default)
@Config(name = "mymod", format = ConfigFormat.TOML) // config/mymod.toml
```

Both are first class. The same class, the same annotations, and the same holder API work either
way; only the rendered file and its extension change. In a config group, each member picks its own
format, so a group can mix the two.

JSON files are written as JSON5: comments survive, and so do trailing commas and unquoted keys if
someone edits the file by hand.

```json5
// Settings for MyMod.
{
  // Scale of the HUD overlay.
  "hudScale": 3,
  "showHints": true
}
```

```toml
#Settings for MyMod.

#Scale of the HUD overlay.
hudScale = 3
showHints = true
```

The one difference that reaches your config class: TOML has no null literal, so a field left
`null` is omitted from the file and comes back as its declared default on the next load. Give
nullable fields a sensible default, or stay on JSON.

Changing the format of an existing config changes the file name too, so the old file is simply
never read again and the defaults are written to the new one. Migrate deliberately.
</details>

<details>
<summary><b>Field entries — <code>@ConfigEntry</code></b></summary>

`@ConfigEntry` describes one field. `@Config(comment = ...)` does the same job for the file as a
whole.

```java
@Config(name = "mymod", comment = "Settings for MyMod.")
public final class MyModConfig {

    @ConfigEntry(name = "hud_scale", comment = "Scale of the HUD overlay, 1 to 4.")
    public int hudScale = 2;

    @ConfigEntry(restart = true, comment = "Takes effect on the next launch.")
    public String worldPreset = "default";
}
```

- **`name`** lets you keep `snake_case` on disk and camelCase in code, or rename a field without
  breaking existing files. Empty (the default) uses the Java field name.
- **`comment`** becomes the text above the entry, one array element per line. `@Config` uses the
  same attribute for the file header.
- **`restart`** marks a field that is only read at startup. Any `update` that changes it is
  rejected as a whole and reports `ConfigError.RESTART_FIELD_CHANGED`. `reset` keeps the startup
  value for restart-only fields and restores every other field to its default.

```java
@ConfigEntry(comment = {"Scale of the on-screen HUD.", "Between 1 and 4."})
public int hudScale = 2;
```

Write the text, not the markers. Each format renders it its own way: JSON uses `//` for a single
line and one `/* */` block for several, TOML uses one `#` line each. A `*/` in the text is
defused rather than written as-is, since it would otherwise truncate the file. Comments are
written on save and ignored on load.

Only a class annotated `@Config` can carry a header, because that is what declares a file. Put the
section comment on the field that holds it:

```java
@ConfigEntry(comment = "Grouped rendering options.")
public RenderSection render = new RenderSection();
```

```java
UpdateResult result = holder.update(config -> config.worldPreset = "flat");
result.accepted();                       // false
result.violations().getFirst().id();     // "restart.worldPreset"
```

The check follows nested config objects, so a restart field inside a section is protected too. It
does not descend into lists or maps.
</details>

<details>
<summary><b>Reacting to changes — <code>onChange</code></b></summary>

Register listeners on the builder when something outside the config has to be rebuilt whenever the
config changes:

```java
EasyConfig.holder(MyModConfig.class)
    .modId("mymod")
    .onChange(config -> hudRenderer.setScale(config.hudScale))
    .onChange(config -> LOGGER.info("config reloaded"))
    .create();
```

Listeners fire, in registration order, after an accepted `update`, an accepted `reset`, and a
successful `load` — once the new state is already visible through `data()`. Nothing fires for a
rejected update, a load that fell back to defaults, or the load `create()` performs.

The listener receives the published state: read it, do not mutate it, and do not hold on to the
reference, because the next change publishes a different object. Use `copy()` if you need a value
after the next change.

Listeners run on the thread that performed the change, which for `createAsync()` is the config
worker, so keep them short and never call a blocking holder method from inside one. A listener
that throws is logged and skipped.
</details>

<details>
<summary><b>Custom cloning</b></summary>

Cloning defaults to a tree round-trip, which is correct for any config model but shows up in a
profile if you copy on a hot path. Replace it with a hand-written copy when that matters:

```java
public final class MyModConfigCloner implements StateCloner<MyModConfig> {

    @Override
    public MyModConfig copy(MyModConfig source) {
        MyModConfig copy = new MyModConfig();
        copy.hudScale = source.hudScale;
        copy.hiddenHints = new ArrayList<>(source.hiddenHints);   // copy the list, don't share it
        return copy;
    }
}
```

```java
EasyConfig.holder(MyModConfig.class)
    .modId("mymod")
    .stateCloner(new MyModConfigCloner())
    .create();
```

A custom `StateCloner` must return a fully independent object. A field it forgets is silently
shared between the published state and every copy of it.
</details>
