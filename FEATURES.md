# Features

## Pathfinding

- **Long-distance spliced pathing** — calculates in segments, pre-calculates the next segment before the current one ends so movement is continuous.
- **Chunk caching** — explored chunks are compressed to a 2-bit-per-block representation (AIR / SOLID / WATER / AVOID) and optionally saved to disk for faster long-distance routing.
- **Block breaking** — considers tool set and hotbar when deciding whether to mine through obstacles. Diamond Eff V pick makes stone walls cheap; a wood pick makes climbing over them cheaper.
- **Block placing** — sneak-back-placing, pillaring, bridging. Configurable penalty (default 1 second) to conserve resources.
- **Falling** — up to 3 blocks onto solid ground (configurable). With a water bucket: up to 23 blocks. Unlimited into existing still water.
- **Vines and ladders** — climbs, descends, and grabs mid-air to break falls. Optional strafe-to-adjacent-column support (`allowVines`).
- **Doors and fence gates**
- **Slabs and stairs**
- **Falling-block awareness** — accounts for the cost of gravel/sand columns above a target block.
- **Danger avoidance** — fire, magma, lava corners, blocks touching liquids.
- **Parkour** — sprint-jumping over 1–3 block gaps; parkour-place (place the landing block mid-jump).

## Mining

- **Nearest-ore-first scanning** — chunk scanner runs in spiral order (nearest chunk first) so the 64 ore targets tracked at any time are always the closest available, not random distant cached ones.
- **Always live-scans loaded chunks** — the disk cache no longer short-circuits the live scan. Both sources are merged and distance-sorted on every rescan.
- **Automatic deepslate pairing** — specifying `iron_ore` automatically includes `deepslate_iron_ore` and vice versa, no need to list both.
- **Dropped-item pickup** — after breaking ore, lingers briefly to pick up drops before moving to the next target.
- **Legit mine mode** — optional setting that restricts mining to ores the player can actually see (no x-ray effect).

## Elytra flying

- **Any dimension** — works in the overworld, Nether, End, and custom dimensions. No longer restricted to the Nether.
- **`#elytra goto X Z` / `#elytra goto X Y Z`** — fly directly to coordinates without setting a goal first.
- **Full-height obstacle avoidance** — the voxel octree covers the complete world height for every dimension (overworld −64 to +320). Mountains, hills, and structures above sea level are correctly avoided.
- **Correct Y-coordinate mapping** — blocks at any world height are stored at their true absolute Y in the pathfinder's octree, including below sea level (Y < 0 in the overworld).
- **Live terrain updates** — block changes at any Y level are reflected immediately in the pathfinder, not just below Y=128.
- **Auto takeoff** — finds a nearby ledge or cliff to jump from automatically (`elytraAutoJump`).
- **Auto landing** — finds a safe landing spot when the path is complete, or emergency-lands on low durability/fireworks.
- **Dimension-aware safe landing** — Nether lands on netherrack/gravel/basalt/blackstone; overworld/End accepts any solid non-hazardous block.
- **Nether terrain prediction** — optionally uses the world seed to predict terrain in unloaded Nether chunks ahead of the flight path (`elytraPredictTerrain`).

## Structure finding

- **`#structure <name>`** — locates the nearest named structure and navigates to it. Runs asynchronously so there's no tick stutter.
  - **Singleplayer:** uses the integrated server's chunk generator — works for unexplored areas, no seed needed.
  - **Multiplayer:** uses the world seed (entered via `#seedinput`) and RandomSpreadStructurePlacement grid math to calculate candidate positions client-side.
- **`#where <structure>`** — same search, but only reports coordinates, distance, and compass direction — no navigation started. Useful for scouting.
- **`#where`** (no argument) — prints current X Y Z and dimension name.
- **Full structure list:** `stronghold`, `village`, `nether_fortress` / `fortress`, `bastion` / `bastion_remnant`, `mansion` / `woodland_mansion`, `monument` / `ocean_monument`, `ancient_city`, `end_city`, `buried_treasure`, `desert_pyramid` / `desert_temple`, `jungle_pyramid` / `jungle_temple`, `pillager_outpost` / `outpost`, `shipwreck`, `mineshaft` / `mine`, `igloo`, `swamp_hut` / `witch_hut`, `ruined_portal`, `ocean_ruin` / `ocean_ruins`, `trial_chambers` / `trial_chamber`, `trail_ruins`.
- All searches run on a background thread and print results to chat when done.

## Multiplayer seed-based features

- **`#seedinput <seed>`** — stores the world seed to `baritone/seed.txt`, persisted across restarts. Required to use `#structure` and `#where` on multiplayer servers.
- **`#seedinput`** (no arg) — shows the currently stored seed.
- **`#seedinput clear`** — deletes the stored seed.
- **Client-side structure math** — for RandomSpreadStructurePlacement structures (villages, fortresses, bastions, etc.) the mod calculates candidate chunk positions from the seed without any server involvement.
- **Stronghold limitation** — strongholds use ConcentricRingsStructurePlacement which requires biome data unavailable client-side. On multiplayer, the command redirects you to chunkbase.com with your stored seed.

## Coordinate utilities

- **`#nether`** (no args) — converts your current X Y Z between dimensions based on which dimension you're in. X and Z are scaled ÷8 (overworld→nether) or ×8 (nether→overworld). Y is the same in both.
- **`#nether X Y Z`** — convert given coordinates; direction is auto-detected from current dimension.
- **`#nether X Z`** — same but without Y.
- **`#nether overworld X Y Z`** / **`#nether nether X Y Z`** — explicit direction regardless of current dimension.
- Aliases: `#nc`, `#coords`.

## Portal navigation

- **`#whereportal`** — navigates to the nearest nether portal in the current dimension. Portal blocks are tracked in Baritone's block cache, so any portal previously visited is found instantly. If none are cached, the bot explores to find one.
- **Auto-entry** — if the `enterPortal` setting is enabled (default: true), the bot walks directly into the portal block and teleports through it.
- Works in both directions: overworld portals (→ Nether) and nether portals (→ Overworld).
- Aliases: `#portal`, `#findportal`.

## Other automation

- **Farming** — harvests, replants, and bone-meals crops automatically within a configurable radius.
- **Builder** — builds Litematica / NBT schematics block by block.
- **Explorer** — systematically explores the world from an origin point, always pathing toward the nearest unseen chunk.
- **Follower** — follows a player or entity type, staying within a configurable radius.
- **Backfill** — fills in mined tunnels behind the player.
- **Waypoints** — named positions saved to disk. Auto-saves death and bed locations.

## Pathfinding internals

- **A\* with segmented calculation** — exits early at the render distance edge or on timeout, selects best partial segment via incremental cost backoff.
- **Minimum improvement repropagation** — skips repropagating alternate routes with < 0.01 tick improvement.
- **Backtrack cost favoring** — reduces cost of backtracking the current segment so the path can splice onto the next segment sooner.
- **Backtrack detection and pausing** — pauses execution when the best calculated path passes back through the player's current position, avoiding unnecessary forward movement.
