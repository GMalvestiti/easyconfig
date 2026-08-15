# EasyConfig

EasyConfig is a lean config library for Minecraft mods on Fabric and NeoForge. You annotate a
plain Java class with `@Config`, hand it to a builder, and get back a `ConfigHolder` that handles
file path resolution, read/write lifecycle, corrupt-file recovery, deep copies, validation
callbacks, and change notification — all without a code generator, annotation processor, or config
screen.

The core design principle is explicitness: every behavioral choice surfaces as a builder call or
an annotation attribute. There is no hidden global state, no classpath scanning, and no
convention-over-configuration magic that you would have to reverse-engineer later. What you write
is what EasyConfig does.

**What EasyConfig does:**
- Resolves and owns the config file path on disk
- Reads the file on first create, backs up corrupt files, restores defaults when needed
- Writes only when you ask, with an atomic replace so the file is never half-written
- Publishes a validated, deep-copied snapshot that callers read without locking
- Runs `afterLoad`, `beforeSave`, and `validate` hooks implemented on the config class itself
- Notifies registered listeners after each accepted change
- Enforces restart-only fields that cannot change at runtime

**What EasyConfig does not do:**
- Config screen — bring your own (Cloth Config, YACL, etc.)
- Client/server sync — hook `onChange` and send packets yourself
- Dynamic reload watching — call `load()` manually when needed

## Setup

Artifacts are published to Maven Central under the group `com.gmalvestiti.minecraft`, with one
artifact per loader:

| Loader   | Artifact              |
|----------|-----------------------|
| Fabric   | `easyconfig-fabric`   |
| NeoForge | `easyconfig-neoforge` |

The library version tracks the Minecraft major version family, not the loader version:

| Minecraft | EasyConfig |
|-----------|------------|
| `1.21.x`  | `1.x.x`    |
| `26.x.x+` | `2.x.x`    |

**Embedded vs. standalone:** Embedding (via `include` / `jarJar`) bundles EasyConfig inside your
mod jar so players install nothing extra. Standalone requires players to have EasyConfig installed
as a separate mod. Embedding is recommended for most mods.

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

`include` nests the mod jar inside yours. No `depends` entry is needed in `fabric.mod.json`.
</details>

<details>
<summary><b>Fabric — standalone</b></summary>

```kotlin
dependencies {
    modImplementation("com.gmalvestiti.minecraft:easyconfig-fabric:1.0.0")
}
```

Declare the dependency so the loader refuses to start without it:

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
<summary><b>NeoForge — standalone</b></summary>

```kotlin
dependencies {
    implementation("com.gmalvestiti.minecraft:easyconfig-neoforge:1.0.0")
}
```

Declare the dependency in `META-INF/neoforge.mods.toml`:

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

The three terminal builder methods correspond to three threading models:

| Method | Thread safety | Best fit |
|---|---|---|
| `create()` | Caller's thread only | Regular single-threaded config |
| `createAsync()` | Safe from any thread | Shared or background access |
| `createImmutable()` | Read-only, safe from any thread | Config that never mutates at runtime |

Declare the config class. Fields must be public, initialized to their defaults, and the class must
have a public no-argument constructor. Name the file after your mod — the config directory is
shared with every other mod:

```java
@Config(name = "mymod")
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

Read through `data()`, write through `update` / `updateAndSave`:

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

## Documentation

<details>
<summary><b>Names and paths — <code>name</code> is the file, <code>path</code> is the directory</b></summary>

The two `@Config` attributes have strictly separate responsibilities:

- **`name`** — the **file name**, and nothing else. One component only; no slashes.
- **`path`** — the **directory** (or nested directories) under the base directory. Never a file
  name and never a file extension.

They combine under the holder's base directory:

```
<baseDir> / <@Config.path()> / <@Config.name()>.<format extension>
   dirs          dirs                     file
```

`baseDir` defaults to the platform config directory (`config/`). `path` defaults to `""`, which
places the file directly in `baseDir`. The extension is determined by `@Config.format()` —
`.json5` by default, `.toml` for `ConfigFormat.TOML`.

| Declaration | Directory | File | Resolved path |
|---|---|---|---|
| `@Config(name = "mymod")` | `config/` | `mymod.json5` | `config/mymod.json5` |
| `@Config(name = "mymod.json5")` | `config/` | `mymod.json5` | `config/mymod.json5` |
| `@Config(name = "client", path = "mymod")` | `config/mymod/` | `client.json5` | `config/mymod/client.json5` |
| `@Config(name = "hud", path = "mymod/gui")` | `config/mymod/gui/` | `hud.json5` | `config/mymod/gui/hud.json5` |
| `@Config(name = "mymod", format = TOML)` | `config/` | `mymod.toml` | `config/mymod.toml` |

Any directory in `path` that does not exist is created on the first save.

**Rules for `name`:** must not be blank, must not contain `/` or `\`, and must not be exactly the
format extension (e.g. `.json5` alone is rejected). The format extension is appended when missing,
case-insensitively, so `MyMod.JSON5` is left alone.

**Rules for `path`:** relative only, `/`-separated, and it must stay inside `baseDir` after
normalization. Absolute paths and `..` escapes are rejected with
`ConfigError.INVALID_CONFIG_PATH`.

**Naming your files:** the mod id is not automatically part of the path. Use the mod id in the
file name or in the path to avoid collisions with other mods:

```java
// Single file — name it after your mod
@Config(name = "mymod")             // → config/mymod.json5

// Several files — isolate them in your own directory
@Config(name = "client", path = "mymod")   // → config/mymod/client.json5
@Config(name = "server", path = "mymod")   // → config/mymod/server.json5

// Avoid — generic names in the shared root collide with other mods
@Config(name = "client")            // → config/client.json5 — not yours alone
```

Two config types that resolve to the same absolute path fail at holder construction with
`ConfigError.CONFLICTING_CONFIG_PATH`. This is detected globally across all holders in the
process.

To relocate the config root — for example, into a per-world directory or a temp directory in
tests — override `baseDir` on the builder:

```java
EasyConfig.holder(MyModConfig.class)
    .modId("mymod")
    .baseDir(FabricLoader.getInstance().getConfigDir().resolve("mymod"))
    .create();
```

Providing `baseDir` also means the platform config directory is never queried, which makes holders
fully usable in plain unit tests without any loader present.
</details>

<details>
<summary><b>Holder implementations — threading model</b></summary>

The terminal builder method you call determines the holder implementation. All three expose the
same `ConfigHolder` API; `createAsync()` returns the wider `AsyncConfigHolder` which adds
non-blocking `*Async` variants.

| Method | Where work runs | Thread safety | Best fit |
|---|---|---|---|
| `create()` | Calling thread, inline | Confine to one thread (e.g. the server thread) | Regular runtime config |
| `createAsync()` | Shared config worker thread | Safe from any thread | Config accessed from multiple threads |
| `createImmutable()` | Frozen at build time | Read-only, safe from any thread | Startup-only config |

```java
// Single-threaded — all operations run on whichever thread calls them
ConfigHolder<MyModConfig> simple = EasyConfig.holder(MyModConfig.class)
    .modId("mymod")
    .create();

// Thread-safe — operations are submitted to the worker; blocking callers wait
AsyncConfigHolder<MyModConfig> async = EasyConfig.holder(MyModConfig.class)
    .modId("mymod")
    .createAsync();

// Immutable — loaded once during create; update and reset are refused
ConfigHolder<MyModConfig> immutable = EasyConfig.holder(MyModConfig.class)
    .modId("mymod")
    .createImmutable();
```

**Async:** the blocking methods (`load`, `save`, `update`, `updateAndSave`) submit work to the
worker and join. Never call them from inside a config hook or `onChange` listener — that deadlocks
and is reported as `ConfigError.BLOCKING_CALL_ON_CONFIG_THREAD`. Use the `*Async` variants
(`loadAsync`, `saveAsync`, `updateAsync`, `updateAndSaveAsync`) when you are already on the config
thread or simply do not want to block. Those methods are exclusive to `AsyncConfigHolder`.

**Immutable:** `update`, `reset`, and their variants are refused through the update failure policy
— they throw under `STRICT` and return a rejected `UpdateResult` under `FALLBACK`. `load` and
`save` still work: a reload replaces the published state wholesale.
</details>

<details>
<summary><b>Reading and writing</b></summary>

```java
MyModConfig shared = holder.data();   // cheap, shared — treat as read-only
MyModConfig mine = holder.copy();     // deep copy you own, safe to mutate freely
```

`data()` returns the live published snapshot. It is cheap — no locking, no copying — and is the
right choice for hot paths. Never mutate the returned object; it is shared with every concurrent
reader. Take a `copy()` when you need to hold a reading that cannot shift while you examine
multiple fields at once, or when you need to mutate values locally before applying them.

```java
holder.update(config -> config.showHints = false);   // publish in memory only
holder.updateAndSave(config -> config.hudScale = 3); // publish and write to disk
```

The lambda passed to `update` receives a private candidate copy, not the live state. The candidate
is validated before publication. If validation fails, the live state is never touched and the
failed update is reported on the returned `UpdateResult`. A mutator that throws is treated as a
defect and propagates regardless of the failure policy.

```java
UpdateResult result = holder.updateAndSave(config -> config.hudScale = 99);
if (!result.accepted()) {
    result.violations().forEach(v -> LOGGER.warn("{}: {}", v.id(), v.message()));
}
```

Holders built with `createAsync()` add non-blocking variants that return `CompletableFuture`:

```java
AsyncConfigHolder<MyModConfig> holder = /* ... */;
holder.saveAsync();
holder.updateAndSaveAsync(config -> config.hudScale = 3).thenAccept(result -> { /* ... */ });
```
</details>

<details>
<summary><b>Failure policies</b></summary>

Each operation family has its own `FailurePolicy`, independent of the others, defaulting to
`FALLBACK`:

```java
EasyConfig.holder(MyModConfig.class)
    .modId("mymod")
    .readFailurePolicy(FailurePolicy.FALLBACK)   // corrupt/invalid file → back it up, restore defaults
    .writeFailurePolicy(FailurePolicy.STRICT)    // save failure → throw EasyConfigException
    .updateFailurePolicy(FailurePolicy.FALLBACK) // invalid edit → discard, keep current state
    .create();
```

Use `failurePolicy(policy)` as a shorthand to set all three at once.

| Policy | On failure | Use when |
|---|---|---|
| `FALLBACK` | Logs and degrades gracefully | User-facing errors that should not crash the game |
| `STRICT` | Throws `EasyConfigException` | Tests, or operations where silent failure is worse than crashing |

**Important distinction — recoverable failures vs. defects:**

`FALLBACK` only suppresses *recoverable* failures — a corrupt file, a missing field, a validation
rejection. *Defects* are always re-thrown regardless of policy. A defect is a programming mistake
in the mod itself: a `null` mutator, a config model that violates EasyConfig rules, a validator
that throws an unexpected exception.

Every `EasyConfigException` carries a `ConfigError` code. Branch on the code rather than on
message text:

```java
try {
    holder.load();
} catch (EasyConfigException ex) {
    if (ex.error() == ConfigError.IO_LOAD_FAILURE) {
        // file is gone or unreadable
    }
}
```

**Read fallback behavior:** when `FALLBACK` handles a corrupt or invalid file found during `create()`
or `load()`, the bad file is renamed to `<filename>.corrupt-<timestamp>` and the defaults are
written in its place. The holder starts (or continues) in a clean state, and the player can
recover their values from the backup.
</details>

<details>
<summary><b>Validation and lifecycle hooks</b></summary>

Implement `ConfigExtension` on the config class to participate in the load/save/validate
lifecycle:

```java
@Config(name = "mymod")
public final class MyModConfig implements ConfigExtension {

    public int hudScale = 2;
    public String worldPreset = "default";

    @Override
    public void afterLoad() {
        // Fix up values after loading — clamp ranges, derive computed fields, etc.
        hudScale = Math.max(1, Math.min(8, hudScale));
    }

    @Override
    public void beforeSave() {
        // Normalize values before writing to disk, if needed.
        // Most configs leave this empty.
    }

    @Override
    public void validate(List<Violation> violations) {
        // Report constraint violations. Must be side-effect free.
        if (hudScale < 1 || hudScale > 8) {
            violations.add(Violation.of("hud-scale.range", "hudScale must be between 1 and 8, was " + hudScale));
        }
    }
}
```

**Hook order and contract:**
- `afterLoad` runs after deserialization, before validation. Use it to normalize or clamp values.
- `validate` runs after `afterLoad`, on every load and before every update is published. It must
  be side-effect free — correct values in `afterLoad`, *report* them in `validate`.
- `beforeSave` runs immediately before serialization.

**Handling validation failures:**

Under `FALLBACK` update policy, violations come back on the `UpdateResult`:

```java
UpdateResult result = holder.updateAndSave(config -> config.hudScale = 99);
if (!result.accepted()) {
    result.violations().forEach(v -> LOGGER.warn("{}: {}", v.id(), v.message()));
}
```

Under `STRICT`, the same violations ride on `EasyConfigException.violations()`.

**Validation on load:** if the file on disk contains values that fail `validate`, the behavior
depends on `readFailurePolicy`. Under `FALLBACK`, the file is backed up and defaults are written.
Under `STRICT`, the load throws.
</details>

<details>
<summary><b>Config groups — several files, one holder</b></summary>

Annotate a shell class with `@ConfigGroup` to manage multiple config files through one holder. Each
non-static, non-transient field whose declared type carries `@Config` becomes its own file. The
file names come from the member types' own `@Config` annotations — keep them inside your mod's
`path` to avoid collisions:

```java
@Config(name = "client", path = "mymod")
public final class ClientConfig {
    public boolean showHints = true;
}

@Config(name = "server", path = "mymod")
public final class ServerConfig {
    public int maxPlayers = 20;
}

@ConfigGroup
public final class ModConfigs {
    public ClientConfig client = new ClientConfig();
    public ServerConfig server = new ServerConfig();
}
```

```java
ConfigHolder<ModConfigs> configs = EasyConfig.holder(ModConfigs.class).modId("mymod").create();
// reads and writes config/mymod/client.json5 and config/mymod/server.json5
boolean hints = configs.data().client.showHints;
```

**Group rules:**
- Members must be non-final (group loads replace the references).
- No two members may share a config type — both fields would resolve to the same file.
- Non-`@Config` fields in the group class are silently ignored for persistence and validation.
- `@ConfigIgnore` can exclude a `@Config`-typed member from the group entirely.

**Lifecycle with groups:** member hooks run first on load and validate; the group root runs first
on save. Each member may independently implement `ConfigExtension`.

**Partial failure recovery:** when one member's file is corrupt, only that member falls back to
defaults. Other members load normally. Each bad file is backed up independently.
</details>

<details>
<summary><b>File formats — JSON5 and TOML</b></summary>

The format is declared on the config class, not on the builder, because it determines what the
file on disk looks like — including the extension:

```java
@Config(name = "mymod")                             // → config/mymod.json5  (default)
@Config(name = "mymod", format = ConfigFormat.TOML) // → config/mymod.toml
```

Both formats are first-class. The same class, the same annotations, and the same holder API work
identically either way; only the on-disk representation changes. In a `@ConfigGroup`, each member
chooses its own format independently.

**JSON5** (`ConfigFormat.JSON`) writes files with the `.json5` extension. The format is a superset
of JSON that preserves comments, tolerates trailing commas, and accepts unquoted keys — so a
player can hand-edit the file and EasyConfig will still read it back:

```json5
// Settings for MyMod.
{
  // Scale of the HUD overlay.
  "hudScale": 3,
  "showHints": true
}
```

**TOML** (`ConfigFormat.TOML`) writes standard TOML:

```toml
#Settings for MyMod.

#Scale of the HUD overlay.
hudScale = 3
showHints = true
```

**TOML limitation — null fields:** TOML has no null literal, so a `null` field is omitted from
the file entirely and comes back as its declared default on the next load. JSON5 preserves `null`
as a literal. If your config has nullable fields that may legitimately be `null`, stay on JSON5
or give them non-null defaults.

**Switching formats:** changing `format` changes the file extension, so the old file is simply
never read again and the defaults are written to the new one. There is no automatic migration.
Plan the format choice before shipping.
</details>

<details>
<summary><b>Field entries — <code>@ConfigEntry</code> and <code>@ConfigIgnore</code></b></summary>

`@ConfigEntry` describes one field's on-disk name, comment, and restart behavior.
`@Config(comment = ...)` does the same for the file header.

```java
@Config(name = "mymod", comment = "Settings for MyMod.")
public final class MyModConfig {

    @ConfigEntry(name = "hud_scale", comment = "Scale of the HUD overlay, 1 to 4.")
    public int hudScale = 2;

    @ConfigEntry(restart = true, comment = "Takes effect on the next launch.")
    public String worldPreset = "default";

    @ConfigEntry(comment = {"Enable the particle overlay.", "Disable on low-end hardware."})
    public boolean particleOverlay = true;
}
```

**`name`** decouples the file key from the Java field name. Empty by default (uses the Java name).
Set it to keep `snake_case` on disk, to stabilize a key across a Java rename, or to use a key
that is not a legal Java identifier.

**`comment`** becomes the text above the entry. One array element per line; a blank element is a
blank comment line; an element containing newlines is split, so a text block works just as well as
an array. Leading whitespace in each line is preserved. Write the text, not the markers — each
format renders them its own way:

```java
// JSON5: single line → // comment, multiple lines → /* block */
@ConfigEntry(comment = "Single line.")
@ConfigEntry(comment = {"Line one.", "Line two."})

// TOML: each line gets its own # prefix
```

A `*/` in comment text is defused to `* /` so it can never truncate the JSON5 block.

**`restart`** marks a field that is read only at game startup. Any `update` that changes the value
of a restart field is rejected as a whole — none of the other fields in the same mutator are
applied either:

```java
UpdateResult result = holder.update(config -> config.worldPreset = "flat");
result.accepted();                       // false
result.violations().getFirst().id();     // "restart.worldPreset"
```

`reset` is the exception: it restores every other field to its default while leaving restart fields
at the value they had at startup. The restart check follows nested config objects; it does not
descend into collections or maps.

---

**`@ConfigIgnore`** excludes a public field from persistence entirely. The field never appears in
the file, is never read back, is never commented, and is never checked by the restart guard. Its
value is whatever the constructor sets it to after every load:

```java
@Config(name = "mymod")
public final class MyModConfig {

    public int hudScale = 2;               // persisted normally

    @ConfigIgnore
    public String sessionCache = "";       // public but never written to disk
}
```

Use `@ConfigIgnore` when the field must be public but should never appear in the config file.
</details>

<details>
<summary><b>Reacting to changes — <code>onChange</code></b></summary>

Register listeners on the builder to be notified whenever the published config state changes:

```java
EasyConfig.holder(MyModConfig.class)
    .modId("mymod")
    .onChange(config -> hudRenderer.setScale(config.hudScale))
    .onChange(config -> LOGGER.info("config reloaded"))
    .create();
```

**When listeners fire:** after an accepted `update`, after an accepted `reset`, and after a
successful `load` — in registration order, once the new state is already visible through `data()`.

**When listeners do not fire:** on a rejected update, on a load that fell back to defaults, and on
the internal load that `create()` performs during construction (nothing has changed at that point,
and the holder does not yet exist to attach listeners to).

**Listener contract:**
- Receive the published state — read it, do not mutate it, and do not hold on to the reference.
  The next change publishes a different object. Call `copy()` if you need to retain a value.
- Run on the thread that performed the change. For `createAsync()` that is the config worker —
  never call a blocking holder method from inside a listener on that thread. Such a call deadlocks
  and is reported as `ConfigError.BLOCKING_CALL_ON_CONFIG_THREAD`.
- A listener that throws is logged as `ConfigError.CHANGE_LISTENER_FAILED` and skipped. It cannot
  fail the triggering operation or prevent later listeners from running.
</details>

<details>
<summary><b>Custom cloning</b></summary>

Cloning defaults to a JSON round-trip through the config model, which is correct for any model but
adds overhead proportional to the number of fields. Replace it with a hand-written copy when
profiling shows `copy()` is on a hot path:

```java
public final class MyModConfigCloner implements StateCloner<MyModConfig> {

    @Override
    public MyModConfig copy(MyModConfig source) {
        MyModConfig copy = new MyModConfig();
        copy.hudScale = source.hudScale;
        copy.showHints = source.showHints;
        copy.hiddenHints = new ArrayList<>(source.hiddenHints);   // deep-copy the list
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

A custom `StateCloner` must return a fully independent object. Any field left as a shared
reference will silently alias between the published state and every copy derived from it,
which produces hard-to-diagnose corruption bugs when either side mutates the shared value.
</details>

