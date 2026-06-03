# Changelog

All notable changes to this Baritone fork are documented here.

---

## [Unreleased] — MC 26.1.2

### 🛡️ Autopilot Survival update 🧪 *experimental*

A coherent "keep me alive" package — bot now reacts to hunger, health, light level, and time of day. **All four features are marked experimental** — they print a one-time in-chat warning the moment they're enabled, matching the convention from `elytraWaveMode` / `elytraConserveFireworks`. May misfire or interact poorly with active processes (`#mine`, `#elytra`, `#farm`). All default off and opt-in.

| Command | Aliases | Description |
|---|---|---|
| `#autoeat [n]` | `#eat` | Auto-eats food from hotbar when hunger < threshold (default 14). Numeric arg sets threshold. Skips suspicious stew, rotten flesh, poisonous potato, pufferfish, spider eye; skips golden apples unless `autoEatAllowGapples=true`. |
| `#autoflee [hp]` | `#paranoia` | Snapshots position and runs `GoalRunAway(distance, origin)` when health drops below `autoFleeThreshold` (default 6 HP). Releases when health recovers above `autoFleeRecoverThreshold` (default 16). |
| `#autosleep` | `#nightowl` | At night (or thunderstorm), navigates to the nearest cached bed. Won't interrupt active tasks unless `autoSleepInterruptTasks=true`. |
| `#autotorch [light]` | `#torch` | Places a torch on the floor when block light < threshold (default 8). Disabled in Nether/End. Defaults to caves-only (sky-light < 4); toggle surface mode via `autotorch surface`. |
| `#autopilot on/off/status` | `#survive` | Master toggle — flips all four features at once. Doesn't override individual settings. |

**Death waypointing** is already provided by Baritone's built-in `WaypointBehavior` (setting `doDeathWaypoints`, default `true`). The `#autopilot status` listing surfaces it for visibility.

**Implementation:** new `AutopilotBehavior` (extends `Behavior`, registered in `Baritone.java`) ticks all four watchers per frame; new `SleepHelper` extracts bed-search + night-detection from `SleepCommand`. 12 new settings under `Settings.java` (search "Autopilot Survival" comment block).

**Known limitation:** auto-eat and auto-torch only act on items already in the **hotbar** — MC 26.1.2's `ClickType.SWAP` API was renamed, so cross-slot swapping is left for a follow-up. Keep food and torches in your hotbar for the autopilot to use them.

---

### New Commands

#### `#structure <name>` / `#struct` / `#locate`
Finds the nearest named structure and navigates to it.
- **Singleplayer:** queries the integrated server's chunk generator directly — works for unexplored areas, no seed required.
- **Multiplayer:** uses the world seed (entered via `#seedinput`) and `RandomSpreadStructurePlacement` grid math to calculate candidate chunk positions client-side, without any server involvement.
- Runs on a background thread so the game never stutters during the search.
- Navigation uses `GoalXZ` so the bot paths to the correct X/Z at ground level rather than Y=0 underground.
- Full structure list: `village`, `stronghold`, `nether_fortress` / `fortress`, `bastion` / `bastion_remnant`, `mansion`, `monument`, `ancient_city`, `end_city`, `buried_treasure`, `desert_pyramid` / `desert_temple`, `jungle_pyramid` / `jungle_temple`, `pillager_outpost` / `outpost`, `shipwreck`, `mineshaft` / `mine`, `igloo`, `swamp_hut` / `witch_hut`, `ruined_portal`, `ocean_ruin`, `trial_chambers`, `trail_ruins`.
- Tab completion for all structure names.

#### `#where [structure]`
- `#where` — prints current X Y Z and dimension name.
- `#where <structure>` — finds the nearest structure and reports its coordinates, horizontal distance, and compass direction (e.g. `NE ↗`) **without starting navigation**. Useful for scouting before committing to a path.
- Distance display uses horizontal-only measurement (X/Z plane) so Y differences never skew the readout.

#### `#seedinput <seed>` / `#seed`
Stores the world seed for multiplayer structure finding.
- `#seedinput <seed>` — saves the seed to `baritone/seed.txt` (persists across restarts).
- `#seedinput` — shows the currently stored seed.
- `#seedinput clear` — deletes the stored seed.
- Supports negative seeds. Only needs to be entered once per world.
- Used automatically by `#structure` and `#where` on multiplayer servers.

#### `#nether` / `#nc` / `#coords`
Converts coordinates between the Overworld and the Nether. X and Z are scaled ÷8 (overworld→nether) or ×8 (nether→overworld). Y is the same in both dimensions.
- `#nether` — converts current position (auto-detects dimension).
- `#nether X Y Z` — converts given coordinates (direction auto-detected from current dimension).
- `#nether X Z` — X and Z only; Y shown as `?`.
- `#nether overworld X Y Z` / `#nether nether X Y Z` — explicit direction regardless of current dimension.

#### `#whereportal` / `#portal` / `#findportal`
Navigates to the nearest nether portal in the current dimension.
- Portal blocks (`minecraft:nether_portal`) are already tracked by Baritone's block cache — any portal previously visited is found instantly.
- If no portal is in the cache, Baritone explores to find one.
- With `enterPortal = true` (the default), the bot walks directly into the portal and teleports through it.
- Works in both directions: overworld portals (→ Nether) and nether portals (→ Overworld).

---

### Elytra Improvements

#### Any-dimension flight
Removed the Nether-only restriction. `#elytra` and `#elytra goto` now work in the Overworld, End, and any custom dimension.

#### `#elytra goto X Z` / `#elytra goto X Y Z`
New `goto` subcommand for flying directly to coordinates without setting a separate goal first.
- Supports **tilde notation** (`~`, `~10`, `~-50`) for relative coordinates in all three axes.
- Examples: `#elytra goto ~500 ~`, `#elytra goto 1000 ~ -500`, `#elytra goto ~1k ~-500`.
- 2-arg form (`X Z`) lets the pathfinder choose height automatically — safe for all destinations.
- 3-arg form (`X Y Z`) flies to an exact block position.

#### Full-height obstacle avoidance (overworld fix)
The voxel octree used by the native pathfinder now covers **all chunk sections** in every dimension:
- **Before:** only the bottom 8 sections (Y 0–127) were packed. Terrain above sea level — mountains, cliffs, tall structures — was invisible to the pathfinder, causing the bot to fly straight through it.
- **After:** all 24 overworld sections (Y −64 to +320) are packed using `chunkInternalStorageArray.length` (not hardcoded 8) and `yReal = (minSectionIndex + sectionIndex) << 4` for correct absolute Y offsets.

#### Live block-update tracking at any height
Removed the `y >= 128` cutoff in `queueBlockUpdate`. Block changes above sea level (e.g. destroying a mountain block mid-flight) are now reflected in the pathfinder immediately, not ignored.

#### Wave-flight mode (`elytraWaveMode`)
New optional flight style that saves fireworks by alternating climb and dive phases:
- **CLIMB phase:** bot aims above the planned path to gain altitude; fireworks fire normally.
- **DIVE phase:** bot aims at path nodes directly, converting altitude into horizontal speed for free — no fireworks used during this phase.
- Transitions: CLIMB → DIVE when player reaches `elytraWaveClimbHeight × 0.75` blocks above the path; DIVE → CLIMB when player drops back within 4 blocks of path height.
- **Auto-disabled in the Nether** — the Nether's low ceiling (Y=128) and dense obstacle field make climbing dangerous. The `isWaveModeActive()` helper enforces this; wave phase resets to CLIMB on dimension change.
- Default: `false` (opt-in). Experimental — shows a warning when first enabled.

#### Conserve fireworks (`elytraConserveFireworks`)
- Skips fireworks while the bot is descending toward a waypoint (saves rockets on downhill segments).
- **Auto-disabled in the Nether** — aggressive firing is necessary to avoid stalling into netherrack and lava.
- Default: `false` (opt-in). Experimental — shows a warning when first enabled.

#### Experimental setting warnings
Both `elytraWaveMode` and `elytraConserveFireworks` display a one-time in-chat warning the moment they are turned on, explaining the experimental nature and how to disable them.

#### Underground destination handling (crash fix + smart redirect)
Giving `#elytra goto` a destination inside a solid block (e.g. Y=0) previously caused a **JVM crash** — `NetherPathfinder.pathFind` (a native JNI call) segfaults on solid destinations and the crash cannot be caught by Java. The new behaviour:
1. If the destination chunk is loaded: check `BlockState.blocksMotion()`. If solid, scan upward to find the surface, add 3 blocks of clearance.
2. If the destination chunk is not loaded but Y < `seaLevel − 20`: assume underground, use `max(Y + 60, seaLevel + 30)` as the surface landing point.
3. Fly to the surface position normally via elytra.
4. After landing, automatically hand off to ground pathfinding (`CustomGoalProcess`) to mine down to the original target.

---

### Ore Mining Improvements

#### Nearest-ore-first scanning
The ore scanner now always runs a live scan of loaded chunks in **spiral (nearest-first) order**, on top of the disk cache. Previously the cache short-circuited the live scan entirely, causing the bot to ignore nearby veins and walk to distant cached ore instead.
- Live scan collects up to 256 candidate positions with `limit(max * 4)` so nearby ores always win.
- Both sources are merged and distance-sorted on every rescan.
- The 64 closest overall (`mineMaxOreLocationsCount`) are kept as targets.

#### Automatic deepslate pairing
Specifying `iron_ore` automatically includes `deepslate_iron_ore` and vice versa. No need to list both variants.

---

### MC 26.1 → 26.1.2 API Fixes

#### Structure tag rename (plural → singular)
All MC 26.1 structure tags changed from plural to singular. Old code using `villages`, `fortresses`, `mineshafts`, etc. returned no results. Updated all references:
- `village` (was `villages`), `mineshaft` (was `mineshafts`), `shipwreck` (was `shipwrecks`), etc.
- Many structures (fortress, bastion, ancient city, end city, desert pyramid, pillager outpost, igloo, trail ruins) have **no tag at all** in 26.1 — these now use direct registry ID lookup via `HolderSet.direct(holder)`.

#### `ChunkGenerator.findNearestMapStructure` API
Switched from the old `ServerLevel.findNearestMapStructure(TagKey, ...)` (removed in 26.1) to `ChunkGenerator.findNearestMapStructure(ServerLevel, HolderSet<Structure>, BlockPos, int, boolean)` which accepts a `HolderSet` and works for both tagged and untagged structures.

#### `RegistryAccess` → `HolderLookup.Provider`
MC 26.1 dropped `registryOrThrow()` from `RegistryAccess`. The code now uses `lookupOrThrow(Registries.STRUCTURE)` which returns a `HolderLookup.RegistryLookup<T>` — the correct API for both tag and direct-key lookups.

#### `ResourceLocation` → `Identifier`
All uses of the removed `ResourceLocation` class updated to `net.minecraft.resources.Identifier`.

#### `ChunkPos.x` / `.z` field access
`ChunkPos.x` and `.z` fields became private in 26.1. Updated to use `cp.x()` / `cp.z()` accessor methods.

#### Version bump
Updated `minecraft_version` from `26.1` to `26.1.2` in `gradle.properties`. Updated `fabric/src/main/resources/fabric.mod.json` from exact-match `["26.1"]` to semver range `">=26.1"` so the mod loads on any 26.1.x patch version without needing a manifest change.

---

### CI / Build Fixes

#### GitHub Actions Java version
`gradle_build.yml` and `run_tests.yml` both specified `java-version: '21'`, causing `error: invalid source release: 25` on every CI run. Updated to `java-version: '25-ea'` (matching the already-correct `build.yml` and `release.yml`).

#### Artifact path fix
`gradle_build.yml` was archiving from `dist/` and `mapping/` — directories that don't exist. Updated to `build/libs/*.jar` where Gradle actually writes the output.

---

### Bug Fixes

| # | File | Description |
|---|------|-------------|
| 1 | `ElytraProcess.java` | **Critical:** `pendingGroundTarget` was set on line N, then `onLostControl()` on line N+5 cleared it back to `null`. The mine-down handoff after landing never triggered. Fixed by saving to a local variable before `onLostControl()` and restoring it after. |
| 2 | `WhereCommand.java` | Multiplayer distance calculation used `origin.distSqr(result)` (3D, includes Y). Since `ClientStructureFinder` always returns Y=64 as a placeholder, players at other Y levels saw incorrect distances. Fixed to use horizontal-only `sqrt(dx²+dz²)`. |
| 3 | `ElytraProcess.java` | `MutableBlockPos.move(0, 1, 0)` may not exist in MC 26.1 (`move(int,int,int)` is not guaranteed). Rest of codebase uses `mut.set(x, y, z)`. Fixed to `mut.set(mut.getX(), mut.getY() + 1, mut.getZ())`. |
| 4 | `StructureCommand.java` | All structures returned "not found" even when one was 100 blocks away. Root cause: MC 26.1 renamed all structure tags from plural to singular and removed tags for many structures entirely. Fixed by redesigning the alias table to use `tag:xxx` / `id:xxx` format with correct 26.1 names. |
| 5 | `StructureCommand.java` / `WhereCommand.java` | Navigation to Y=0 (underground) when going to structures. `ChunkGenerator.findNearestMapStructure` returns Y=0 or a structural Y that's often underground. Fixed by switching from `GoalBlock(result)` to `GoalXZ(result.getX(), result.getZ())`. |
| 6 | `ElytraCommand.java` | `#elytra` (no-args) showed "only works in the nether" error even after the Nether-only restriction was removed from `ElytraProcess`. The leftover dimension check in `ElytraCommand` was removed. |
| 7 | `ElytraCommand.java` | `#elytra goto X Z` gave "Invalid action" because the `goto` case didn't exist in the switch statement. Added. |
| 8 | `ElytraCommand.java` | `#elytra goto X Y Z` / tilde notation (`~`) not supported — coordinates were parsed with raw `Integer.parseInt`. Replaced with a `parseRelativeCoord` helper that handles `~`, `~N`, and `~-N`. |
| 9 | `NetherPathfinderContext.java` | Block update tracking had a `y >= 128` early-exit, silently discarding all block changes above Y=127 in the overworld. Removed. |
| 10 | `NetherPathfinderContext.java` | `writeChunkData` looped `y0 < 8` (Nether-only assumption) and used `yReal = y0 << 4` (also Nether-only). Fixed to `y0 < chunkInternalStorageArray.length` and `yReal = (minSectionIndex + y0) << 4` to cover all 24 overworld sections correctly. |
