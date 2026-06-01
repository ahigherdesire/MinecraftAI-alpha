# MinecraftAI (Baritone fork — MC 26.1)

A Minecraft pathfinding bot forked from [Baritone](https://github.com/cabaletta/baritone), updated for **Minecraft 26.1** (Java 25, Fabric 0.18.4) with several new features.

## Download

**Latest build:** `build/libs/baritone-1.0.0-mc26.1-dirty.jar`

Drop this jar into your Minecraft `mods/` folder alongside Fabric Loader 0.18.4. No other mods required.

> Minecraft 26.1 is fully unobfuscated so there is no remapping step — the jar contains plain class names.

## Quick start

Type commands in chat with the `#` prefix:

```
#goto 1000 500          → walk to x=1000 z=500
#mine diamond_ore       → mine diamonds (and deepslate_diamond_ore automatically)
#elytra                 → fly to a destination with elytra — now works in any dimension
#structure stronghold   → find and fly/walk to the nearest stronghold
#stop                   → stop everything
#help                   → list all commands with descriptions and tab completion
```

## What's new in this fork

### Ore detection overhaul
The scanner now always checks currently-loaded chunks in spiral (nearest-first) order on top of the disk cache. Previously the cache short-circuited the live scan, causing Baritone to ignore nearby veins and walk to distant cached ore instead. Detection rate is dramatically higher and the closest ores are always targeted first.

- Each `#mine` rescan collects up to 256 positions from nearest loaded chunks (sequential scan, limit fires closest-first) then merges with the cache and keeps the 64 closest overall.
- `deepslate_*` ore variants are automatically included when you specify the stone variant and vice versa — `#mine iron_ore` covers both.

### `#structure` command (singleplayer)
Locates the nearest structure and paths to it using the integrated server's world-gen data — works even for unexplored areas. Aliases cover all common structures.

```
#structure stronghold       → End portal room
#structure village
#structure nether_fortress  → also: fortress
#structure bastion
#structure ancient_city
#structure mansion
#structure monument
#structure end_city
#structure buried_treasure
#structure desert_pyramid   → also: desert_temple
#structure jungle_pyramid   → also: jungle_temple
#structure pillager_outpost → also: outpost
#structure shipwreck
#structure mineshaft
```

Tab-completes structure names. Runs the search off the game thread so there's no stutter. Multiplayer: use an external seed calculator and `#goto X ~ Z`.

### Elytra in the overworld (and any dimension)
`#elytra` previously silently did nothing outside the Nether. It now works in the overworld, the End, and any custom dimension:

- Jump-off Y target is dimension-aware (Nether: Y=31 inside tunnel; overworld: current Y+20 to find a cliff)
- Safe landing detection uses `block.defaultBlockState().isSolid()` for overworld blocks instead of only netherrack/gravel/nether bricks
- The pathfinder's voxel octree now packs **all** chunk sections (overworld has 24 sections, −64 to +320) with correct absolute Y coordinates. Previously only the bottom 8 sections (Y 0–127) were packed, so mountains above sea level were invisible and the bot flew straight through them.
- Block-update tracking no longer has a `y >= 128` cutoff, so terrain changes above sea level are reflected in real time.

## Commands reference

See [USAGE.md](USAGE.md) for the full command list. See [FEATURES.md](FEATURES.md) for the complete feature overview.

## Settings

Settings persist in `baritone/settings.txt` in your Minecraft folder. Toggle booleans by typing their name in chat (`#allowSprint`), set numerics with `#primaryTimeoutMS 2000`, reset all with `#reset`.

Notable settings:
- `legitMine` — only mine ores actually visible, no x-ray effect
- `elytraAutoJump` — automatically find a ledge to jump from when starting elytra
- `elytraPredictTerrain` — use world seed to predict unloaded terrain ahead (Nether)
- `mineMaxOreLocationsCount` — how many ore targets to track at once (default 64)
- `renderCachedChunks` — visualise the disk cache in-game (GPU-heavy)

## Building from source

See [SETUP.md](SETUP.md). The Gradle daemon has a known incompatibility with Java 25 on Windows; see [CLAUDE.md](CLAUDE.md) for the working manual `javac` + `jar uf` compilation workflow.

## Credits

**Original Baritone** — [leijurv](https://github.com/leijurv/) and [contributors](https://github.com/cabaletta/baritone/graphs/contributors)  
**3D elytra pathfinding** — [babbaj](https://github.com/babbaj/) (nether-pathfinder native library)  
**MC 26.1 port & new features** — this fork  
**License** — LGPL 3.0
