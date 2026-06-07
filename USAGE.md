# Usage

All commands use the `#` prefix in chat. Tab completion works on every command and argument.

Type `#help` in-game for a searchable, clickable list of all commands.

---

## Activation

```
#activate <token>
```

Enter your license token once. The token is saved to `baritone/license.key` and verified automatically on every future session — you never need to type it again unless the token expires or you reinstall Minecraft.

- Token expired or invalid → `Error 001. Please contact owner.`
- No token at all → `Type #activate <token> to activate.`

Aliases: `#license`

---

## 🛡️ Autopilot Survival &nbsp;·&nbsp; 🧪 EXPERIMENTAL

> ⚠️ **Experimental.** `#autosleep` may misfire, fail to act, or interact poorly with other Baritone processes. Prints a one-time in-chat warning when enabled. Toggle off with `#autosleep off`.

```
#autosleep                  → toggle night-time bed autopilot
#autosleep interrupt        → toggle whether it interrupts active tasks
#autosleep status           → show settings
```

### Behavior

- **autoSleep:** at night or thunderstorm, navigates to the nearest **cached** bed (any of 16 colours). Won't yank you out of an active task unless `autoSleepInterruptTasks = true`. Cache-only — walk near your bed once so Baritone learns its location.
- This watcher handles **navigation only**, not the actual right-click on the bed. For "go to bed and sleep right now," use the explicit `#sleep` command instead.

### Why isn't this an `#autopilot` master toggle?

It used to be planned that way (see `UPCOMING.md`). Auto-eat, auto-flee, auto-torch, and the master toggle were **dropped** because **Meteor Client already does all three** and there's no benefit to maintaining duplicates in Baritone. Only `#autosleep` survives because it's tightly coupled to Baritone's bed cache and the existing `#sleep` command.

### Death waypoints (built-in, separate)

Baritone's built-in `WaypointBehavior` saves a death-position waypoint on every death. Setting `doDeathWaypoints` (default `true`) controls it. After dying, click the chat link to navigate back to your stuff. This is a stable upstream feature, not part of Autopilot Survival.

---

## Sleep

```
#sleep
```

Find the nearest cached bed (any of 16 colours), navigate via `GoalNear(bed, 1)`, then on a background thread wait for night-time (or a thunderstorm) and right-click. Cancel any time with `#cancel`.

If no bed is in the cache, walk near a bed first so Baritone caches it, then re-run.

---

## Run away

```
#runaway             → flee 32 blocks (default)
#runaway 100         → flee 100 blocks
```

Locks the origin to where you're standing when the command runs, then sets `GoalRunAway(distance, origin)`. The bot stops once it's far enough away. Aliases: `#flee`, `#escape`.

---

## Base finder

```
#bases               → top 10 likely bases in current dimension
#bases 25            → top 25
#bases pie           → indicator type breakdown
#bases 1 goto        → path to base #1
```

Reads Baritone's per-dimension chunk cache, finds clusters of base-indicator blocks (beacons, ender chests, enchanting tables, anvils, shulkers, brewing stands, beds, chests), and ranks them by weighted score.

**Example output:**
```
══ Detected bases (4 total, showing top 10) ══
 1. score  247 │ X= 8421 Z=-2156 │ 47 indicators │ ~9482 blocks │ 12× shulker, 8× ender_chest, 6× bed, 4× anvil
 2. score  184 │ X=-4392 Z= 6011 │ 31 indicators │ ~7611 blocks │ 8× ender_chest, 8× anvil, 4× shulker, 3× bed
 3. score   95 │ X=  512 Z= -840 │ 18 indicators │ ~ 942 blocks │ 4× ender_chest, 4× anvil, 2× enchanting_table, 2× bed
 4. score   38 │ X= -200 Z=  300 │  6 indicators │ ~ 412 blocks │ 2× anvil, 1× ender_chest, 1× bed, 2× chest
```

**How it works:**
1. Pulls every indicator block position from the chunk cache.
2. Clusters by 2D (X/Z) proximity using DBSCAN with `baseFinderEpsilon` (default 50 blocks).
3. Scores each cluster: `beacon=50, ender_chest=30, enchanting_table=25, brewing_stand=15, anvil=15, shulker=15, trapped_chest=10, bed=8, chest=3, furnace=2, dragon_egg=100`.
4. Filters out clusters below `baseFinderMinScore` (default 30) or with fewer than `baseFinderMinIndicators` (default 3) blocks.
5. Sorts descending by score, prints top N.

**Tuning settings:**

| Setting | Default | Notes |
|---|---|---|
| `baseFinderMinScore` | 30 | Lower to surface smaller outposts |
| `baseFinderMinIndicators` | 3 | Raise to filter noise on huge servers |
| `baseFinderEpsilon` | 50 | Cluster radius in blocks; raise on sprawling bases |

**Notes:**
- Reads cache only — no packets, no live scan, no anticheat surface. Bulletproof.
- Only finds blocks in chunks you've previously loaded. Elytra-fly long distances at high altitude with `chunkCaching=true` to populate the cache.
- Per-dimension. Cached blocks in the Nether/End won't appear in an Overworld scan; run `#bases` in each dimension separately.
- The Y coordinate isn't shown — bases are XZ clusters; the goal uses `GoalXZ` for navigation.

Aliases: `#basefinder`.


---

## Navigation

| Command | Description |
|---------|-------------|
| `#goto X Y Z` | Walk to an exact coordinate |
| `#goto X Z` | Walk to an X,Z position at any Y |
| `#goto Y` | Walk to a Y level |
| `#goto <block>` | Walk to the nearest block of this type |
| `#goal X Y Z` | Set goal without starting (then `#path` to go) |
| `#path` | Start pathing toward the current goal |
| `#thisway 1000` | Go 1000 blocks in the direction you're facing |
| `#come` | Path toward your camera position (useful in freecam) |
| `#invert` | Path as far as possible *away* from the current goal |
| `#axis` | Go to an axis or diagonal axis at Y=120 (configurable) |
| `#surface` | Path to the nearest open surface area |
| `#cancel` / `#stop` | Stop everything |
| `#forcecancel` | Stop even if it's currently unsafe to cancel |

---

## Mining

```
#mine diamond_ore
#mine 64 iron_ore
#mine coal_ore diamond_ore
```

- Automatically includes both stone and deepslate variants (`iron_ore` covers `deepslate_iron_ore`).
- Scans currently-loaded chunks in nearest-first order on every rescan — always targets the closest ores, not distant cached ones.
- Add `64` before the block name to stop after collecting 64 of that item.
- `#blacklist` — tell Baritone to skip the current closest ore (useful if it's stuck).

**Legit mode** — `#legitMine` restricts mining to ores the player can actually see. Baritone will wander around at `#legitMineYLevel` until it spots one.

---

## Elytra

```
#elytra goto X Z
#elytra goto X Y Z
#elytra
#elytra reset
#elytra repack
#elytra supported
```

Works in **any dimension** — overworld, Nether, End. Requires elytra equipped in chestplate slot and firework rockets in inventory.

- `#elytra goto X Z` — fly to coordinates (bot handles height automatically)
- `#elytra goto X Y Z` — fly to an exact block position
- `#elytra goto ~500 ~ ~-200` — tilde notation: relative to your current position
- `#elytra` (no args) — fly to whatever goal was set with `#goal` beforehand
- `#elytra reset` — reset pathfinding state (keeps flying to same goal, useful if stuck)
- `#elytra repack` — reload all chunks into the pathfinder (use after terrain changes)
- `#elytra supported` — check if the native pathfinding library loaded correctly

**Crash-proof destinations:** if the target Y is inside terrain or in an unloaded chunk where seed-predicted terrain might be solid, the bot automatically lifts to a safe altitude above natural terrain (Y≈200 in the overworld, Y=115 in the Nether, Y=80 in the End). If the target is genuinely underground, the bot flies to the surface above it and hands off to ground pathfinding to mine down. This avoids the native pathfinder's JVM-crashing segfault on solid destinations.

Baritone will:
1. Find a nearby ledge or cliff to jump from (auto-jump)
2. Boost with fireworks to maintain altitude
3. Navigate around terrain at any height (mountains, structures — full overworld height coverage)
4. Find a safe landing spot when close to the destination

Key settings:
- `elytraAutoJump` — automatically find a takeoff spot (default on)
- `elytraMinimumDurability` — land and replace elytra before it breaks
- `elytraMinFireworksBeforeLanding` — emergency land when fireworks run low
- `elytraPredictTerrain` — predict unloaded Nether terrain from the world seed

---

## Where

```
#where
#where stronghold
#where village
#where nether_fortress
```

`#where` with no argument prints your current coordinates and dimension — a quick position check.

`#where <structure>` locates the nearest matching structure and tells you its coordinates, exact distance, and compass direction **without starting navigation**. Use `#structure <name>` when you want to both locate and travel there.

Example output:
```
stronghold:  X=847  Z=-342  |  NE ↗  |  ~250 blocks  |  (use #structure stronghold to go there)
```

Accepts all the same structure names and aliases as `#structure`.

**Multiplayer:** requires a seed stored via `#seedinput`. Strongholds are not calculable client-side — you'll be pointed to chunkbase.com.

---

## Structure finder

```
#structure village
#structure stronghold
#structure nether_fortress    (also: fortress)
#structure bastion            (also: bastion_remnant)
#structure mansion            (also: woodland_mansion)
#structure monument           (also: ocean_monument)
#structure ancient_city
#structure end_city
#structure buried_treasure
#structure desert_pyramid     (also: desert_temple)
#structure jungle_pyramid     (also: jungle_temple)
#structure pillager_outpost   (also: outpost)
#structure shipwreck
#structure mineshaft          (also: mine)
#structure igloo
#structure swamp_hut          (also: witch_hut)
#structure ruined_portal
#structure ocean_ruin         (also: ocean_ruins)
#structure trial_chambers     (also: trial_chamber)
#structure trail_ruins
```

Searches for the nearest matching structure and starts pathing to it. The search runs in the background so the game doesn't stutter.

**Singleplayer:** queries the integrated server's chunk generator — works even for unexplored areas, no seed needed.

**Multiplayer:** uses seed-based structure math. Enter your world seed first:

```
#seedinput 12345678
#structure village
```

**Strongholds on multiplayer:** strongholds can't be calculated client-side (they need biome data). The command will tell you to check chunkbase.com with your stored seed.

---

## Seed input (multiplayer)

```
#seedinput 12345678    → store a seed
#seedinput             → show the currently stored seed
#seedinput clear       → forget the stored seed
```

Also available as `#seed`.

The seed is written to `baritone/seed.txt` in your Minecraft folder and reloaded automatically each launch — you only need to enter it once per world. Supports negative seeds (e.g. `#seedinput -4100785268875389365`).

This seed is used by `#structure` and `#where` on multiplayer to calculate structure positions client-side without server access.

---

## Nether coordinate converter

```
#nether
#nether X Y Z
#nether X Z
#nether overworld X Y Z
#nether nether X Y Z
```

Converts coordinates between the Overworld and the Nether. X and Z are scaled ×8 or ÷8; Y is the same in both dimensions.

- `#nether` — converts your current position (auto-detects which dimension you're in)
- `#nether 800 64 -400` — converts the given X Y Z (direction auto-detected from your dimension)
- `#nether X Z` — X and Z only, Y shown as `?`
- `#nether overworld 800 64 -400` — explicitly convert overworld → nether regardless of dimension
- `#nether nether 100 64 -50` — explicitly convert nether → overworld regardless of dimension

Example output:
```
You are at (Overworld)  X=800  Y=64  Z=-400  →  Nether  X=100  Y=64  Z=-50
```

Aliases: `#nc`, `#coords`

---

## Portal navigation

```
#whereportal         → go to the nearest nether portal (and enter it)
#portal skip         → blacklist the nearest portal, try the next one
```

Navigates to the nearest nether portal in your current dimension. Portal blocks are part of Baritone's block cache — any portal you've been near is found immediately. If no portal is in the cache, Baritone will explore to find one.

By default (`enterPortal = true`), the bot walks directly **into** the portal and teleports through it.

To navigate to the portal without entering it:
```
#set enterPortal false
#whereportal
```

**`#portal skip`** — useful when the closest portal is unreachable. Marks it as unreachable and searches for the next one. Run again to skip further candidates.

Works in both directions:
- In the **overworld** → finds an overworld portal frame (to enter the Nether)
- In the **nether** → finds a nether-side portal (to return to the Overworld)

Aliases: `#portal`, `#findportal`

---

## Farming

```
#farm
#farm 50
#farm 50 home
```

Harvests, replants, and bone-meals crops in the area. Optional radius (blocks) and starting waypoint.

---

## Building

```
#build myhouse.litematic
#build myhouse.litematic ~ 64 ~
```

Builds a Litematica schematic. The origin defaults to player feet; relative coordinates (`~`) are supported for offset.

---

## Waypoints

```
#waypoints save user coolspot
#waypoints goal coolspot
#sethome          (alias for: #waypoints save home)
#home             (alias for: #waypoints goto home)
```

Death and bed locations are saved automatically.

---

## Exploration

```
#explore
#explore 0 0
#explorefilter filter.json
```

Continuously paths toward the nearest unexplored chunk from an origin. Useful for mapping large areas or finding biomes.

---

## Other commands

| Command | Description |
|---------|-------------|
| `#follow player Steve` | Follow a named player |
| `#follow players` | Follow any player in range |
| `#follow entity pig` | Follow entities of a type |
| `#tunnel` | Dig a 1×2 tunnel straight ahead |
| `#click` | Click a block to path to it |
| `#repack` | Re-cache the chunks around you |
| `#render` | Fix glitched chunk rendering |
| `#reloadall` / `#saveall` | Reload or save the disk chunk cache |
| `#find <block>` | Search the disk cache for a block type |
| `#proc` | Show what process is currently active and its state |
| `#eta` | Show estimated time to next segment and goal |
| `#version` | Show the loaded Baritone version |
| `#gc` | Run `System.gc()` to free memory |

---

## Settings

Toggle a boolean: type its name — `#allowSprint`  
Set a value: `#primaryTimeoutMS 2000`  
Reset one: `#allowBreak reset`  
Reset all: `#reset`  
List changed: `#modified`  

Commonly changed settings:

| Setting | Default | Notes |
|---------|---------|-------|
| `allowBreak` | true | Let Baritone break blocks |
| `allowSprint` | true | Sprint while pathing |
| `allowPlace` | true | Place blocks while pathing |
| `allowParkour` | true | Sprint-jump gaps |
| `allowParkourPlace` | true | Place landing blocks mid-jump |
| `legitMine` | false | Only mine visible ores |
| `legitMineYLevel` | -57 | Y level to explore when legit mining |
| `mineMaxOreLocationsCount` | 64 | How many ore targets to track |
| `elytraAutoJump` | true | Auto-find a takeoff ledge |
| `elytraPredictTerrain` | false | Predict unloaded Nether terrain |
| `enterPortal` | true | Walk into portal when `#whereportal` arrives |
| `backfill` | false | Fill mined tunnels behind you |
| `renderCachedChunks` | false | Visualise the disk cache (GPU-heavy) |
| `followRadius` | 3 | Distance to maintain when following |
| `blockPlacementPenalty` | 20 | Ticks penalty per block placed |
| `acceptableThrowawayItems` | cobble/dirt/netherrack | Blocks usable as scaffolding |
| `doDeathWaypoints` | true | Save clickable waypoint on player death |
| `doBedWaypoints` | true | Save waypoint when right-clicking a bed |

**Autopilot Survival settings:**

| Setting | Default | Notes |
|---------|---------|-------|
| `autoSleep` | false | Master enable for night-time bed autopilot |
| `autoSleepInterruptTasks` | false | Yank player out of active tasks to sleep |

(Earlier plan also included `autoEat*`, `autoFlee*`, `autoTorch*` settings; those features were dropped — see FEATURES.md.)

**Base finder settings:**

| Setting | Default | Notes |
|---------|---------|-------|
| `baseFinderMinScore` | 30 | Minimum weighted score for a cluster to count as a "base" |
| `baseFinderMinIndicators` | 3 | Minimum number of indicator blocks in a cluster |
| `baseFinderEpsilon` | 50 | DBSCAN cluster radius in blocks |

---

## Troubleshooting

**Baritone doesn't respond to commands** — check that the `baritone/` folder was created in your Minecraft directory (confirms it loaded). Make sure you're using the `#` prefix.

**It walks past ores without mining them** — run `#repack` to force a rescan of loaded chunks, or check that `allowBreak` is on.

**Elytra command does nothing** — make sure you have an elytra equipped and firework rockets in your inventory. Also check you're not in spectator mode.

**`#structure` says "not found" on multiplayer** — enter your world seed with `#seedinput <seed>` first. If you're looking for a stronghold specifically, use chunkbase.com — strongholds aren't calculable client-side.

**`#whereportal` says no portal found** — the portal needs to be within a chunk you've previously loaded. Walk to the portal area first so it gets cached, then use the command.

**`#nether` shows wrong direction** — use the explicit form: `#nether overworld X Y Z` or `#nether nether X Y Z` to force the conversion direction regardless of your current dimension.

**`#autosleep` isn't activating at night** — three common causes:
1. **No cached bed.** Walk near a bed at least once so Baritone caches it.
2. **A process is running.** By default, autosleep yields to active tasks. Run `#autosleep interrupt` to flip `autoSleepInterruptTasks=true`, or `#cancel` whatever's running.
3. **You're already in another dimension.** The bed cache is per-dimension.

**`#elytra goto` lifts to a high Y instead of going to the exact Y I asked** — this is intentional. The native pathfinder will crash the game if it hits seed-predicted solid terrain at the destination in an unloaded chunk. For underground destinations the bot flies to the surface then mines down. For high-Y destinations in unloaded chunks it lifts to a safe altitude. Use a loaded-chunk destination if you need exact Y placement.
