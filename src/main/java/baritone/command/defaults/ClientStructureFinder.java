/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.command.defaults;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/**
 * Client-side structure finder that works without a ServerLevel.
 *
 * Uses the world seed (entered via {@code #seedinput}) together with the
 * client's RegistryAccess to locate structures that use
 * {@link RandomSpreadStructurePlacement}.  Structures that use
 * ConcentricRingsStructurePlacement (i.e. strongholds) require biome data
 * unavailable client-side and are NOT supported — the result will be null
 * for those, and the calling command should tell the user to check a seed map.
 *
 * The seed is persisted to {@code baritone/seed.txt} in the game directory
 * so it survives game restarts.
 */
public final class ClientStructureFinder {

    private static volatile long storedSeed = Long.MIN_VALUE; // Long.MIN_VALUE = not set
    private static volatile boolean fileAttempted = false;    // lazy-load guard
    private static final long UNSET = Long.MIN_VALUE;

    private ClientStructureFinder() {}

    // -------------------------------------------------------------------------
    // Seed management
    // -------------------------------------------------------------------------

    public static void setSeed(long seed) {
        storedSeed = seed;
        fileAttempted = true; // don't re-read file after explicit set
        try {
            File f = seedFile();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), String.valueOf(seed).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    public static boolean hasSeed() {
        ensureLoaded();
        return storedSeed != UNSET;
    }

    public static long getSeed() {
        ensureLoaded();
        return storedSeed;
    }

    public static void clearSeed() {
        storedSeed = UNSET;
        fileAttempted = true;
        try { Files.deleteIfExists(seedFile().toPath()); } catch (Exception ignored) {}
    }

    // -------------------------------------------------------------------------
    // Structure search
    // -------------------------------------------------------------------------

    /**
     * Find the nearest structure matching {@code tag} by searching within
     * {@code searchChunkRadius} chunks of {@code origin}.
     *
     * Returns {@code null} if:
     *   - no seed is stored
     *   - the registry has no structures for the tag
     *   - the matching structure set uses ConcentricRingsStructurePlacement (strongholds)
     *   - no candidate was found within the search radius
     */
    public static BlockPos findNearest(TagKey<Structure> tag, BlockPos origin, int searchChunkRadius) {
        if (!hasSeed()) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;
        RegistryAccess registries = mc.getConnection().registryAccess();

        // Collect all structure ResourceKeys that belong to the tag
        Registry<Structure> structureReg = registries.registry(Registries.STRUCTURE).orElseThrow();
        Set<ResourceKey<Structure>> taggedKeys = new HashSet<>();
        structureReg.getTagOrEmpty(tag).forEach(holder ->
            holder.unwrapKey().ifPresent(taggedKeys::add));
        if (taggedKeys.isEmpty()) return null;

        // Find StructureSets that contain any of those structures
        Registry<StructureSet> setReg = registries.registry(Registries.STRUCTURE_SET).orElseThrow();

        BlockPos nearest = null;
        long nearestDistSq = Long.MAX_VALUE;

        for (StructureSet set : setReg) {
            boolean relevant = false;
            for (var entry : set.structures()) {
                if (entry.structure().unwrapKey().map(taggedKeys::contains).orElse(false)) {
                    relevant = true;
                    break;
                }
            }
            if (!relevant) continue;

            StructurePlacement placement = set.placement();
            if (!(placement instanceof RandomSpreadStructurePlacement rsp)) {
                // ConcentricRingsStructurePlacement (strongholds) — not supported client-side.
                // Return null so the caller can show a targeted message.
                return null;
            }

            BlockPos candidate = findNearestForPlacement(rsp, origin, searchChunkRadius);
            if (candidate != null) {
                long distSq = origin.distSqr(candidate);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = candidate;
                }
            }
        }

        return nearest;
    }

    // -------------------------------------------------------------------------
    // RandomSpreadStructurePlacement candidate search
    // -------------------------------------------------------------------------

    private static BlockPos findNearestForPlacement(RandomSpreadStructurePlacement placement,
                                                     BlockPos origin, int searchChunkRadius) {
        int spacing = placement.spacing();

        // Convert block coordinates to region (cell) coordinates.
        // A "region" is a spacing×spacing chunk grid cell.
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;
        int originRegX = Math.floorDiv(originChunkX, spacing);
        int originRegZ = Math.floorDiv(originChunkZ, spacing);
        int searchRegRadius = (searchChunkRadius / spacing) + 2;

        BlockPos nearest = null;
        long nearestDistSq = Long.MAX_VALUE;

        for (int dx = -searchRegRadius; dx <= searchRegRadius; dx++) {
            for (int dz = -searchRegRadius; dz <= searchRegRadius; dz++) {
                int regX = originRegX + dx;
                int regZ = originRegZ + dz;

                // getPotentialStructureChunk replicates the vanilla placement RNG logic.
                // It is a public method on RandomSpreadStructurePlacement since MC 1.19.
                ChunkPos chunk = placement.getPotentialStructureChunk(storedSeed, regX, regZ);

                // Use the centre of the chunk as our candidate block position.
                // Y=64 is a placeholder — the actual structure Y varies; the goal
                // processor will handle exact height once the bot arrives.
                BlockPos pos = new BlockPos((chunk.x() << 4) + 8, 64, (chunk.z() << 4) + 8);
                long distSq = origin.distSqr(pos);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = pos;
                }
            }
        }

        return nearest;
    }

    // -------------------------------------------------------------------------
    // Persistence helpers
    // -------------------------------------------------------------------------

    private static File seedFile() {
        return new File(Minecraft.getInstance().gameDirectory, "baritone/seed.txt");
    }

    private static void ensureLoaded() {
        if (fileAttempted) return;
        fileAttempted = true;
        try {
            File f = seedFile();
            if (f.exists()) {
                String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                storedSeed = Long.parseLong(content);
            }
        } catch (Exception ignored) {}
    }
}
