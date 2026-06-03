# Installation

## Requirements

- **Minecraft 26.1.2** with **Fabric Loader 0.18.4**
- **Java 25 JDK** — required both to run MC 26.1.2 and to build from source

## Installing the mod

1. Install [Fabric Loader 0.18.4](https://fabricmc.net/use/) for Minecraft 26.1.2.
2. Copy `build/libs/baritone-1.0.0-mc26.1-dirty.jar` into your `mods/` folder.
   (The JAR filename uses `mc26.1` — short minor — but it targets MC 26.1.2.)
3. Launch Minecraft. You should see `baritone/` created in your game directory.

## First-time setup (multiplayer)

If you play on multiplayer and want to use `#structure` or `#where` to find structures, enter your world seed once:

```
#seedinput 12345678
```

The seed is saved to `baritone/seed.txt` and reloaded automatically on every future launch — you only need to do this once per world.

## First-run survival tips &nbsp;·&nbsp; 🧪 EXPERIMENTAL

If you want the bot to walk you to a bed at night, turn on auto-sleep:

```
#autosleep
```

For it to work, walk near your bed at least once so Baritone caches it.

> ⚠️ `#autosleep` is **experimental** — it may misfire or interact poorly with other Baritone processes. Use `#cancel` if anything looks wrong, and `#autosleep off` to disable.

For auto-eat / auto-flee / auto-torch, use **Meteor Client** — Baritone's plans for those were dropped to avoid duplicating Meteor's work.

## Building from source

**Requirements:**
- Java 25 JDK with `JAVA_HOME` pointing to it — if you get `error: invalid source release: 25`, your JDK is too old
- Git (for the version tag in the build command)

**Build command:**
```bash
./gradlew build -Pmod_version="$(git describe --always --tags --first-parent | cut -c2-)"
```

The output JAR is written to `build/libs/`.

> On Windows, use Git Bash or WSL to run the `$(git describe ...)` substitution, or set `mod_version` manually:
> ```
> gradlew build -Pmod_version=1.0.0
> ```

## After installation

Type `#help` in the Minecraft chat for a list of all commands.

See [USAGE.md](USAGE.md) for full command documentation and [FEATURES.md](FEATURES.md) for a complete feature overview.
