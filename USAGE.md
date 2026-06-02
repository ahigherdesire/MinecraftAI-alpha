# Usage

All commands use the `#` prefix in chat. Tab completion works on every command and argument.

Type `#help` in-game for a searchable, clickable list of all commands.

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
- `#elytra` (no args) — fly to whatever goal was set with `#goal` beforehand
- `#elytra reset` — reset pathfinding state (keeps flying to same goal, useful if stuck)
- `#elytra repack` — reload all chunks into the pathfinder (use after terrain changes)
- `#elytra supported` — check if the native pathfinding library loaded correctly

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
#whereportal
```

Navigates to the nearest nether portal in your current dimension. Portal blocks are part of Baritone's block cache — any portal you've been near is found immediately. If no portal is in the cache, Baritone will explore to find one.

By default (`enterPortal = true`), the bot walks directly **into** the portal and teleports through it.

To navigate to the portal without entering it:
```
#set enterPortal false
#whereportal
```

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

---

## Troubleshooting

**Baritone doesn't respond to commands** — check that the `baritone/` folder was created in your Minecraft directory (confirms it loaded). Make sure you're using the `#` prefix.

**It walks past ores without mining them** — run `#repack` to force a rescan of loaded chunks, or check that `allowBreak` is on.

**Elytra command does nothing** — make sure you have an elytra equipped and firework rockets in your inventory. Also check you're not in spectator mode.

**`#structure` says "not found" on multiplayer** — enter your world seed with `#seedinput <seed>` first. If you're looking for a stronghold specifically, use chunkbase.com — strongholds aren't calculable client-side.

**`#whereportal` says no portal found** — the portal needs to be within a chunk you've previously loaded. Walk to the portal area first so it gets cached, then use the command.

**`#nether` shows wrong direction** — use the explicit form: `#nether overworld X Y Z` or `#nether nether X Y Z` to force the conversion direction regardless of your current dimension.
