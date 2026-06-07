# MinecraftAI (Baritone fork — MC 26.1.2)

A Minecraft pathfinding bot forked from [Baritone](https://github.com/cabaletta/baritone), updated for **Minecraft 26.1.2** (Java 25, Fabric 0.18.4) with several new features.

> ## 🛡️ Autopilot Survival &nbsp;·&nbsp; 🧪 EXPERIMENTAL
>
> - `#autosleep` — navigate to the nearest cached bed when night falls
>
> ⚠️ **Experimental** — may misfire or interact poorly with other Baritone processes. Prints a one-time in-chat warning when enabled. Toggle off with `#autosleep off`.
>
> The originally-planned auto-eat / auto-flee / auto-torch / `#autopilot` master toggle have been **dropped** — Meteor Client already covers them. Death-point waypointing remains free via Baritone's built-in `doDeathWaypoints` (on by default).
>
> See [CHANGELOG.md](CHANGELOG.md) for details.

## Download & setup

1. Drop `build/libs/miencraftai-dirty.jar` into your `mods/` folder alongside Fabric Loader 0.18.4.
2. Launch Minecraft. On first command you'll see:
   ```
   [MinecraftAI] Type #activate <token> to activate.
   ```
3. Enter the token you were given:
   ```
   #activate <your-token>
   ```
   Saved permanently — you only need to do this once.

> **📖 Full guide: [GUIDE.md](GUIDE.md)** — every command, every feature, common workflows, troubleshooting. Start here if you just got this JAR.

> Minecraft 26.1.2 is fully unobfuscated so there is no remapping step — the jar contains plain class names.

## Quick start

Type commands in chat with the `#` prefix:

```
#goto 1000 500              → walk to x=1000 z=500
#mine diamond_ore           → mine diamonds (deepslate variant included automatically)
#elytra goto 5000 -2000     → fly to coordinates with elytra (any dimension)
#whereportal                → navigate to the nearest nether portal (and enter it)
#where                      → print your current X Y Z and dimension
#where village              → show where the nearest village is (coords + direction)
#structure stronghold       → find and walk to the nearest stronghold
#nether                     → convert your current XYZ to the other dimension
#seedinput <seed>           → store your world seed for multiplayer structure finding
#sleep                      → go to the nearest bed and sleep when night falls
#runaway 50                 → flee 50 blocks away from the current position
#bases                      → find likely player bases by clustering cached indicator blocks
#stop                       → stop everything
#help                       → list all commands with descriptions and tab completion
```

**Stay alive while doing other things** (Autopilot Survival update):
```
#autosleep                  → auto-navigate to a bed at night
#autosleep status           → show its settings
```
(Auto-eat / auto-flee / auto-torch were dropped — Meteor Client already does them.)

## What's new in this fork

### 🛡️ Autopilot Survival 🧪 *experimental*
A reactive watcher that walks you to the nearest cached bed when night falls (or during a thunderstorm). Toggle with `#autosleep`. Won't interrupt active tasks unless `autoSleepInterruptTasks=true`. Prints a one-time warning when enabled.

```
#autosleep              → toggle the bed-at-night watcher
#autosleep interrupt    → toggle whether to interrupt active tasks
#autosleep status       → show settings
```

Auto-eat / auto-flee / auto-torch / the `#autopilot` master toggle were **dropped** — Meteor Client already provides equivalents and there's no point duplicating them.

Built-in Baritone death-waypointing (`doDeathWaypoints`, on by default) creates a clickable death-position waypoint on every death, so you can always run `#wp goto death @ <timestamp>` to come back for your stuff.

### Ore detection overhaul
The scanner now always checks currently-loaded chunks in spiral (nearest-first) order on top of the disk cache. Previously the cache short-circuited the live scan, causing Baritone to ignore nearby veins and walk to distant cached ore instead. Detection rate is dramatically higher and the closest ores are always targeted first.

- Each `#mine` rescan collects up to 256 positions from nearest loaded chunks (sequential scan, limit fires closest-first) then merges with the cache and keeps the 64 closest overall.
- `deepslate_*` ore variants are automatically included when you specify the stone variant and vice versa — `#mine iron_ore` covers both.

### `#structure` — find any structure (singleplayer + multiplayer)
Locates the nearest structure and paths to it. In singleplayer, uses the integrated server's world-gen data so it works even for unexplored chunks. In multiplayer, uses the world seed (enter with `#seedinput`) and grid math to calculate candidate positions client-side.

```
#structure village          #structure nether_fortress   #structure bastion
#structure stronghold       #structure mansion           #structure monument
#structure ancient_city     #structure end_city          #structure buried_treasure
#structure desert_pyramid   #structure jungle_pyramid    #structure pillager_outpost
#structure shipwreck        #structure mineshaft         #structure igloo
#structure swamp_hut        #structure ruined_portal     #structure ocean_ruin
#structure trial_chambers   #structure trail_ruins
```

### `#where` — locate without navigating
`#where <structure>` shows the nearest structure's coordinates, distance, and compass direction without starting navigation. Useful for scouting before committing to a path. `#where` alone prints your current X Y Z and dimension.

### `#seedinput` — multiplayer structure finding
Stores your world seed so `#structure` and `#where` work on multiplayer servers.

```
#seedinput 12345678       → save seed
#seedinput                → show stored seed
#seedinput clear          → forget seed
```

The seed is persisted to `baritone/seed.txt` and reloaded automatically on next launch.

### `#nether` — coordinate converter
Converts coordinates between the Overworld and the Nether (X and Z ÷8 / ×8; Y is the same in both).

```
#nether                       → convert your current X Y Z (auto-detects dimension)
#nether 800 64 -400           → convert given coords (direction auto-detected)
#nether overworld 800 64 -400 → explicitly convert overworld → nether
#nether nether 100 64 -50     → explicitly convert nether → overworld
```

Aliases: `#nc`, `#coords`

### `#whereportal` — go to the nearest portal
Navigates to the nearest nether portal. Portal blocks are tracked in Baritone's cache, so any portal you've been near is found instantly. If `enterPortal` is enabled (the default), the bot walks straight into the portal and teleports through it.

```
#whereportal      → find and enter the nearest portal
```

Aliases: `#portal`, `#findportal`

### Elytra in the overworld (and any dimension)
`#elytra` previously did nothing outside the Nether. It now works in the overworld, the End, and any custom dimension:

- The pathfinder's voxel octree now packs **all** chunk sections (overworld has 24 sections, −64 to +320) with correct absolute Y coordinates. Previously only the bottom 8 sections (Y 0–127) were packed, so mountains above sea level were invisible.
- Block-update tracking no longer has a `y >= 128` cutoff.
- New `goto` subcommand: `#elytra goto X Z` or `#elytra goto X Y Z`.

## JourneyMap integration

If [JourneyMap](https://www.curseforge.com/minecraft/mc-mods/journeymap) is installed:

- **`#structure`** and **`#where`** automatically drop a gold waypoint on the map when a structure is found.
- **`#bases`** drops a purple waypoint for every detected base cluster.
- **Right-click anywhere on the fullscreen map** → select **"Baritone #goto"** → the bot starts pathing there. Works on multiplayer where JourneyMap's built-in Teleport is unavailable.

No extra configuration — the integration activates automatically if JourneyMap is present.

## Commands reference

See [USAGE.md](USAGE.md) for the full command list. See [FEATURES.md](FEATURES.md) for the complete feature overview.

## Settings

Settings persist in `baritone/settings.txt` in your Minecraft folder. Toggle booleans by typing their name in chat (`#allowSprint`), set numerics with `#primaryTimeoutMS 2000`, reset all with `#reset`.

Notable settings:
- `legitMine` — only mine ores actually visible, no x-ray effect
- `elytraAutoJump` — automatically find a ledge to jump from when starting elytra
- `elytraPredictTerrain` — use world seed to predict unloaded terrain ahead (Nether)
- `enterPortal` — walk into a portal when `#whereportal` arrives at it (default: true)
- `mineMaxOreLocationsCount` — how many ore targets to track at once (default 64)
- `renderCachedChunks` — visualise the disk cache in-game (GPU-heavy)

## Building from source

See [SETUP.md](SETUP.md). Requires Java 25 JDK — if the build fails with `invalid source release: 25`, your JDK is too old.

## Credits

**Original Baritone** — [leijurv](https://github.com/leijurv/) and [contributors](https://github.com/cabaletta/baritone/graphs/contributors)  
**3D elytra pathfinding** — [babbaj](https://github.com/babbaj/) (nether-pathfinder native library)  
**MC 26.1.2 port & new features** — this fork  
**License** — LGPL 3.0
