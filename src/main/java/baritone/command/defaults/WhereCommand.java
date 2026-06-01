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

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reports location information without starting any navigation.
 *
 * No argument  → prints the player's current coordinates and dimension.
 * With argument → finds the nearest named structure and prints its coordinates,
 *                 distance, and compass direction — but does NOT path there.
 *                 Use {@code #structure <name>} to also navigate.
 */
public class WhereCommand extends Command {

    public WhereCommand(IBaritone baritone) {
        super(baritone, "where");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);

        if (!args.hasAny()) {
            // No argument — report the player's own position
            BlockPos pos = ctx.playerFeet();
            // ResourceKey.toString() → "ResourceKey[minecraft:dimension_type / minecraft:overworld]"
            // Strip everything up to " / " to get just "minecraft:overworld]", then drop the "]".
            String dim = "unknown";
            if (ctx.world() != null) {
                String raw = ctx.world().dimension().toString();
                int sep = raw.indexOf(" / ");
                dim = sep >= 0 ? raw.substring(sep + 3, raw.length() - 1) : raw;
            }
            logDirect("You are at  X=" + pos.getX()
                    + "  Y=" + pos.getY()
                    + "  Z=" + pos.getZ()
                    + "  (" + dim + ")");
            return;
        }

        // With argument — locate a structure
        String input   = args.getString().toLowerCase();
        String tagPath = StructureCommand.ALIASES.getOrDefault(input, input);

        TagKey<Structure> tag = TagKey.create(
            Registries.STRUCTURE,
            Identifier.withDefaultNamespace(tagPath)
        );

        final BlockPos origin = ctx.playerFeet();

        // Singleplayer path — use the integrated server.
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            ServerLevel serverLevel = server.getLevel(ctx.world().dimension());
            if (serverLevel == null) {
                throw new CommandInvalidStateException(
                    "Current dimension is not available on the integrated server.");
            }
            logDirect("Searching for nearest '" + tagPath + "'...");
            Thread t = new Thread(() -> {
                BlockPos found;
                try {
                    found = serverLevel.findNearestMapStructure(tag, origin, 100, false);
                } catch (Exception e) {
                    Minecraft.getInstance().execute(() ->
                        logDirect("Search failed: " + e.getMessage()));
                    return;
                }
                final BlockPos result = found;
                Minecraft.getInstance().execute(() -> {
                    if (result == null) {
                        logDirect("No '" + tagPath + "' found within ~1600 blocks.");
                        return;
                    }
                    int dist = (int) Math.sqrt(origin.distSqr(result));
                    String dir = compassDirection(origin, result);
                    logDirect(tagPath + ":"
                        + "  X=" + result.getX()
                        + "  Z=" + result.getZ()
                        + "  |  " + dir
                        + "  |  ~" + dist + " blocks"
                        + "  |  (use #structure " + input + " to go there)");
                });
            }, "BaritoneWhereSearch");
            t.setDaemon(true);
            t.start();
            return;
        }

        // Multiplayer path — use seed-based client-side finder.
        if (tagPath.equals("strongholds")) {
            logDirect("Strongholds require biome data not available client-side.");
            logDirect("Use  chunkbase.com  with seed " +
                (ClientStructureFinder.hasSeed() ? ClientStructureFinder.getSeed() : "<enter with #seedinput>") +
                "  to find the nearest stronghold.");
            return;
        }
        if (!ClientStructureFinder.hasSeed()) {
            throw new CommandInvalidStateException(
                "You are on multiplayer. Enter your world seed first:  #seedinput <seed>"
            );
        }
        logDirect("Searching for nearest '" + tagPath + "' using stored seed " +
            ClientStructureFinder.getSeed() + "...");
        Thread t = new Thread(() -> {
            BlockPos result;
            try {
                result = ClientStructureFinder.findNearest(tag, origin, 100);
            } catch (Exception e) {
                Minecraft.getInstance().execute(() ->
                    logDirect("Search failed: " + e.getMessage()));
                return;
            }
            Minecraft.getInstance().execute(() -> {
                if (result == null) {
                    logDirect("No '" + tagPath + "' found within ~1600 blocks.");
                    logDirect("Check chunkbase.com for the exact location.");
                    return;
                }
                int dist = (int) Math.sqrt(origin.distSqr(result));
                String dir = compassDirection(origin, result);
                logDirect(tagPath + " (seed-based):"
                    + "  X=" + result.getX()
                    + "  Z=" + result.getZ()
                    + "  |  " + dir
                    + "  |  ~" + dist + " blocks"
                    + "  |  (use #structure " + input + " to go there)");
            });
        }, "BaritoneWhereSeedSearch");
        t.setDaemon(true);
        t.start();
    }

    /** Returns a compass direction string with an arrow, e.g. "NE ↗". */
    private static String compassDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx == 0 && dz == 0) return "here";
        // atan2(dx, -dz) gives clockwise angle from north.
        // Minecraft: +X = East, +Z = South, -Z = North.
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) angle += 360;
        String[] dirs = { "N ↑", "NE ↗", "E →", "SE ↘", "S ↓", "SW ↙", "W ←", "NW ↖" };
        int idx = (int) Math.round(angle / 45.0) % 8;
        return dirs[idx];
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String prefix = "";
            try { prefix = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String p = prefix;
            return StructureCommand.ALIASES.keySet().stream()
                .filter(k -> k.startsWith(p))
                .sorted();
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Show your position or find a structure's coordinates";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
            "The where command reports location without starting navigation.",
            "",
            "Usage:",
            "> where               - print your current X Y Z and dimension",
            "> where <structure>   - find the nearest structure and show its coordinates,",
            "                        distance, and compass direction (does NOT path there)",
            "",
            "All structure names accepted by #structure also work here.",
            "To navigate after finding a location, use:  #structure <name>"
        );
    }
}
