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

- **Any dimension** — works in the overworld, Nether, End, and custom dimensions.
- **Full-height obstacle avoidance** — the voxel octree covers the complete world height for every dimension (overworld −64 to +320). Mountains, hills, and structures above sea level are correctly avoided.
- **Correct Y-coordinate mapping** — blocks at any world height are stored at their true absolute Y in the pathfinder's octree, including below sea level (Y < 0 in the overworld).
- **Live terrain updates** — block changes at any Y level are reflected immediately in the pathfinder, not just below Y=128.
- **Auto takeoff** — finds a nearby ledge or cliff to jump from automatically (`elytraAutoJump`).
- **Auto landing** — finds a safe landing spot when the path is complete, or emergency-lands on low durability/fireworks.
- **Dimension-aware safe landing** — Nether lands on netherrack/gravel/basalt/blackstone; overworld/End accepts any solid non-hazardous block.
- **Nether terrain prediction** — optionally uses the world seed to predict terrain in unloaded Nether chunks ahead of the flight path (`elytraPredictTerrain`).

## Structure finding

- **`#structure <name>`** — locates the nearest named structure and navigates to it. Uses the integrated server's world-gen data in singleplayer, so it works for unexplored areas too. Runs asynchronously so there's no tick stutter during the search.
- Supports: `stronghold`, `village`, `nether_fortress`, `bastion`, `mansion`, `monument`, `ancient_city`, `end_city`, `mineshaft`, `buried_treasure`, `desert_pyramid`, `jungle_pyramid`, `pillager_outpost`, `shipwreck`, `igloo`, `swamp_hut`, `ocean_ruin`, `ruined_portal`, `trail_ruins`, and any raw structure tag name.

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
