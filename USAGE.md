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
#elytra cancel
```

Works in **any dimension** — overworld, Nether, End. Requires elytra equipped in chestplate slot and firework rockets in inventory.

Baritone will:
1. Find a nearby ledge or cliff to jump from (auto-jump)
2. Boost with fireworks to maintain altitude
3. Navigate around terrain (mountains, cliffs, structures)
4. Find a safe landing spot when close to the destination

Key settings:
- `elytraAutoJump` — automatically find a takeoff spot (default on)
- `elytraMinimumDurability` — land and replace elytra before it breaks
- `elytraMinFireworksBeforeLanding` — emergency land when fireworks run low
- `elytraPredictTerrain` — predict unloaded Nether terrain from the world seed

---

## Structure finder (singleplayer only)

```
#structure stronghold
#structure village
#structure nether_fortress
#structure bastion
#structure mansion
#structure monument
#structure ancient_city
#structure end_city
#structure buried_treasure
#structure desert_pyramid
#structure jungle_pyramid
#structure pillager_outpost
#structure shipwreck
#structure mineshaft
#structure igloo
#structure swamp_hut
#structure ruined_portal
#structure ocean_ruin
#structure trail_ruins
```

Searches the world generator for the nearest matching structure (including unexplored chunks) and starts pathing to it. The search runs in the background so the game doesn't stutter.

For multiplayer: use an external seed calculator and `#goto X ~ Z`.

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

**Structure command says "not in singleplayer"** — `#structure` requires access to the integrated server. On multiplayer, note the coordinates from a seed calculator and use `#goto X ~ Z`.
