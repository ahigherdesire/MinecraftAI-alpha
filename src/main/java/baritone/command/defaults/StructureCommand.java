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
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Locates the nearest named structure (stronghold, village, etc.) and navigates there.
 *
 * Works in singleplayer by delegating to the integrated server's structure finder —
 * which knows about all structures in the world regardless of whether they are
 * currently loaded.  In multiplayer the server structure-finder is unavailable;
 * if you already know the coordinates (e.g. from a seed calculator), use
 * {@code #goto X ~ Z} instead.
 *
 * Usage:
 *   #structure stronghold          - find and go to nearest stronghold
 *   #structure village             - find and go to nearest village
 *   #structure <tag>               - find any structure whose registry tag matches
 */
public class StructureCommand extends Command {

    // Maps short player-facing names to Minecraft structure tag paths.
    // Tags use the plural form as registered in net.minecraft.tags.StructureTags.
    private static final Map<String, String> ALIASES = new HashMap<>();
    static {
        ALIASES.put("stronghold",        "strongholds");
        ALIASES.put("village",           "villages");
        ALIASES.put("nether_fortress",   "fortresses");
        ALIASES.put("fortress",          "fortresses");
        ALIASES.put("bastion",           "bastions");
        ALIASES.put("bastion_remnant",   "bastions");
        ALIASES.put("mansion",           "mansions");
        ALIASES.put("woodland_mansion",  "mansions");
        ALIASES.put("monument",          "monuments");
        ALIASES.put("ocean_monument",    "monuments");
        ALIASES.put("mineshaft",         "mineshafts");
        ALIASES.put("buried_treasure",   "buried_treasures");
        ALIASES.put("desert_pyramid",    "desert_pyramids");
        ALIASES.put("desert_temple",     "desert_pyramids");
        ALIASES.put("jungle_pyramid",    "jungle_pyramids");
        ALIASES.put("jungle_temple",     "jungle_pyramids");
        ALIASES.put("pillager_outpost",  "pillager_outposts");
        ALIASES.put("outpost",           "pillager_outposts");
        ALIASES.put("end_city",          "end_cities");
        ALIASES.put("ancient_city",      "ancient_cities");
        ALIASES.put("trail_ruins",       "trail_ruins");
        ALIASES.put("ruined_portal",     "ruined_portals");
        ALIASES.put("shipwreck",         "shipwrecks");
        ALIASES.put("igloo",             "igloos");
        ALIASES.put("swamp_hut",         "swamp_huts");
        ALIASES.put("witch_hut",         "swamp_huts");
        ALIASES.put("ocean_ruin",        "ocean_ruins");
        ALIASES.put("ocean_ruins",       "ocean_ruins");
    }

    public StructureCommand(IBaritone baritone) {
        super(baritone, "structure", "struct", "locate");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);
        args.requireMax(1);

        String input = args.getString().toLowerCase();

        // Resolve alias (or pass through as-is for raw tag paths)
        String tagPath = ALIASES.getOrDefault(input, input);

        // Must be in singleplayer — the integrated server has full world-gen access
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            throw new CommandInvalidStateException(
                "Structure finding requires singleplayer (integrated server).\n" +
                "In multiplayer, use a seed calculator and then: #goto X ~ Z"
            );
        }

        ServerLevel serverLevel = server.getLevel(ctx.world().dimension());
        if (serverLevel == null) {
            throw new CommandInvalidStateException(
                "Current dimension is not available on the integrated server."
            );
        }

        TagKey<Structure> tag = TagKey.create(
            Registries.STRUCTURE,
            Identifier.withDefaultNamespace(tagPath)
        );

        logDirect("Searching for nearest '" + tagPath + "' — this may take a moment...");

        // Run the (potentially slow) structure search off the main thread, then
        // schedule the result back on the main thread so Baritone can act on it.
        final BlockPos searchOrigin = ctx.playerFeet();
        Thread searchThread = new Thread(() -> {
            BlockPos found;
            try {
                found = serverLevel.findNearestMapStructure(tag, searchOrigin, 100, false);
            } catch (Exception e) {
                Minecraft.getInstance().execute(() ->
                    logDirect("Structure search failed: " + e.getMessage())
                );
                return;
            }

            final BlockPos result = found;
            Minecraft.getInstance().execute(() -> {
                if (result == null) {
                    logDirect("No '" + tagPath + "' found within ~1600 blocks. Try exploring further.");
                    return;
                }
                int dist = (int) Math.sqrt(searchOrigin.distSqr(result));
                logDirect("Found '" + tagPath + "' at " +
                    result.getX() + ", " + result.getY() + ", " + result.getZ() +
                    "  (~" + dist + " blocks away)");
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(result));
            });
        }, "BaritoneStructureSearch");
        searchThread.setDaemon(true);
        searchThread.start();
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String prefix = "";
            try { prefix = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String p = prefix;
            return ALIASES.keySet().stream()
                .filter(k -> k.startsWith(p))
                .sorted();
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Find and navigate to a nearby structure";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
            "The structure command locates the nearest named structure and tells Baritone to navigate there.",
            "",
            "Only works in singleplayer — the integrated server's structure locator is used, which",
            "searches the world generator even for unexplored areas.  In multiplayer, use an",
            "external seed calculator and then run:  #goto X ~ Z",
            "",
            "Short aliases are accepted for all common structure types.",
            "",
            "Usage:",
            "> structure stronghold       - nearest stronghold (End portal)",
            "> structure village          - nearest village",
            "> structure nether_fortress  - nearest Nether fortress",
            "> structure bastion          - nearest bastion remnant",
            "> structure mansion          - nearest woodland mansion",
            "> structure monument         - nearest ocean monument",
            "> structure ancient_city     - nearest ancient city",
            "> structure end_city         - nearest End city",
            "> structure mineshaft        - nearest mineshaft",
            "> structure buried_treasure  - nearest buried treasure",
            "> structure desert_pyramid   - nearest desert pyramid",
            "> structure jungle_pyramid   - nearest jungle temple",
            "> structure pillager_outpost - nearest pillager outpost",
            "> structure shipwreck        - nearest shipwreck",
            "> structure <tag>            - any other structure tag (plural form, e.g. 'igloos')"
        );
    }
}
