# Create Block Config Addon

A NeoForge 1.21.1 addon for the [Create](https://github.com/Creators-of-Create/Create) mod that lets server administrators control how many of a specific block may (or must) be present in a contraption for assembly to succeed.

---

## Features

- **Per-block maximum**: prevent players from building contraptions with too many of a given block.
- **Per-block minimum**: require a minimum number of a given block for assembly to succeed.
- **Server-side config**: the rule list lives in `<world>/serverconfig/createblocklimit-server.toml` — different worlds can have different rules, no client mod needed.
- **Player-facing error**: when assembly is blocked, Create's standard error UI shows a descriptive chat message (e.g. *"Too many create:drill in contraption: 6 (max 4 allowed)"*).

---

## Installation

1. Install [NeoForge 1.21.1](https://neoforged.net/) and [Create for NeoForge 1.21.1](https://github.com/Creators-of-Create/Create).
2. Drop `createblocklimit-<version>.jar` into your `mods/` folder.
3. Start the server once to generate the config file.
4. Edit `<world>/serverconfig/createblocklimit-server.toml`.

---

## Configuration

File: `<world>/serverconfig/createblocklimit-server.toml`

```toml
# List of block-limit rules.
# Format: "namespace:path:minCount:maxCount"
#   Use -1 to disable a check.
#   Shorthand "namespace:path:maxCount" sets only a maximum (min = -1).
blockLimits = [
    "create:mechanical_bearing:1:1",   # exactly 1 bearing per contraption
    "create:drill:0:4",                 # at most 4 drills
    "minecraft:tnt:-1:0",              # TNT completely forbidden
    "create:rope_pulley:1:-1",         # at least 1 rope pulley
]
```

### Rule format

| Field | Description |
|-------|-------------|
| `namespace:path` | Block registry ID (e.g. `create:drill`, `minecraft:stone`) |
| `minCount` | Minimum required. `-1` = no minimum check. |
| `maxCount` | Maximum allowed. `-1` = no maximum check. |

---

## Building

```bash
./gradlew build
```

The compiled jar is placed in `build/libs/`.

> **Note:** Adjust `create_version`, `flywheel_version`, and `registrate_version` in
> `gradle.properties` to match the exact Create release you are targeting.

---

## How it works

A [Mixin](https://github.com/SpongePowered/Mixin) is applied to
`com.simibubi.create.content.contraptions.AbstractContraption#assemble()`.
After Create successfully collects all contraption blocks, the mixin counts each
block type and compares the counts against the configured rules.  If any rule is
violated the mixin clears the collected block set and throws
`AssemblyException` — the same exception class Create uses internally — so the
player sees a descriptive chat message instead of a silent failure.

---

## License

MIT
