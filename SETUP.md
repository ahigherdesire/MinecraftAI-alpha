# Installation

## Requirements

- **Minecraft 26.1** with **Fabric Loader 0.18.4**
- **Java 25** (required by MC 26.1)

## Installing the mod

1. Install [Fabric Loader 0.18.4](https://fabricmc.net/use/) for Minecraft 26.1.
2. Copy `build/libs/baritone-1.0.0-mc26.1-dirty.jar` into your `mods/` folder.
3. Launch Minecraft. You should see `baritone/` created in your game directory.

## Building from source

> **Important:** The Gradle daemon fails on this project due to a Java 25 + Gradle 9.5.1 Windows incompatibility. Use the manual `javac` + `jar uf` workflow documented in [CLAUDE.md](CLAUDE.md) instead of `./gradlew build`.

**Requirements for building:**
- Java 25 JDK with `JAVA_HOME` set
- `C:\T\patchout` must exist (patches a broken Windows NIO selector in Java 25 — required for both compiling and running Gradle)
- Gradle cache populated (run `./gradlew dependencies` once to download jars, even if the daemon crashes after)

**Short version:** Compile changed `.java` files with `javac @argsfile` where the args file lists `--release 25`, `--patch-module=java.base=C:\T\patchout`, and a classpath including the existing JAR plus MC/Fabric jars from the Gradle cache. Then update the JAR with `jar uf`. Full details in CLAUDE.md.

## After installation

Type `#help` in the Minecraft chat for a list of all commands.

See [USAGE.md](USAGE.md) for command documentation and [FEATURES.md](FEATURES.md) for a full feature overview.
