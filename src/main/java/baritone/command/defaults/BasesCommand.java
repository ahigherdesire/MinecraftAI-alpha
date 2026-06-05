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

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.cache.ICachedWorld;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.utils.BetterBlockPos;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Finds clusters of "base indicator" blocks in Baritone's chunk cache and ranks
 * them by score. Used to surface likely player bases on long-explored worlds.
 *
 * <p>Pure cache reader — no packets sent, no world scan, no anticheat surface.
 * Only finds what you've previously loaded; if you've never been near a chunk,
 * its blocks are not in the cache.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Pull every position of every indicator block from the per-dimension cache.</li>
 *   <li>Cluster by 2D (X/Z) proximity using DBSCAN with epsilon = baseFinderEpsilon.</li>
 *   <li>Score each cluster with the weight table {@link #INDICATORS}.</li>
 *   <li>Filter clusters by min-score and min-indicator-count.</li>
 *   <li>Sort by score descending, print top N.</li>
 * </ol>
 *
 * <p>Subcommands:
 * <pre>
 *   #bases               top N (default 10) by score in current dimension
 *   #bases &lt;N&gt;           top N
 *   #bases pie           type breakdown across all cached indicators
 *   #bases &lt;N&gt; goto      path to the N'th base via GoalXZ
 * </pre>
 */
public class BasesCommand extends Command {

    // ── Indicator block weights ───────────────────────────────────────────────
    // Higher weight = stronger signal that this is a real base.
    // Keys are Minecraft block registry paths (no "minecraft:" prefix), matching
    // CachedWorld.getLocationsOf signature.
    private static final Map<String, Integer> INDICATORS = new LinkedHashMap<>();
    static {
        INDICATORS.put("beacon",            50);
        INDICATORS.put("ender_chest",       30);
        INDICATORS.put("enchanting_table",  25);
        INDICATORS.put("brewing_stand",     15);
        INDICATORS.put("anvil",             15);
        // Shulker boxes — all 16 colours + undyed. Weighted as a group.
        INDICATORS.put("shulker_box",       15); // undyed (1.17+)
        for (String c : Arrays.asList("white", "orange", "magenta", "light_blue",
                "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
                "blue", "brown", "green", "red", "black")) {
            INDICATORS.put(c + "_shulker_box", 15);
        }
        INDICATORS.put("trapped_chest",     10);
        // Beds — count as soft signal; one bed = not a base, many = probably is.
        for (String c : Arrays.asList("white", "orange", "magenta", "light_blue",
                "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
                "blue", "brown", "green", "red", "black")) {
            INDICATORS.put(c + "_bed", 8);
        }
        INDICATORS.put("chest",             3);
        INDICATORS.put("furnace",           2);
        // Dragon egg = guaranteed end spawn area, very high signal
        INDICATORS.put("dragon_egg",        100);
    }

    public BasesCommand(IBaritone baritone) {
        super(baritone, "bases", "basefinder");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (ctx.world() == null || ctx.worldData() == null) {
            throw new CommandInvalidStateException("No world loaded.");
        }

        // Parse subcommands
        boolean pie = false;
        int topN = 10;
        int gotoIndex = -1;

        if (args.hasAny()) {
            String first = args.getString().toLowerCase();
            if (first.equals("pie")) {
                pie = true;
            } else {
                try {
                    topN = Integer.parseInt(first);
                    if (topN < 1) throw new CommandInvalidStateException("N must be ≥ 1.");
                } catch (NumberFormatException e) {
                    throw new CommandInvalidStateException(
                            "Unknown argument '" + first + "'. Try a number, 'pie', or '<N> goto'.");
                }
                if (args.hasAny()) {
                    String second = args.getString().toLowerCase();
                    if (second.equals("goto")) {
                        gotoIndex = topN; // user wants the N'th base; we'll compute and goto
                        topN = Math.max(topN, 10); // still list a few
                    } else {
                        throw new CommandInvalidStateException(
                                "Unknown argument '" + second + "'. Did you mean 'goto'?");
                    }
                }
            }
        }
        args.requireMax(0);

        final BetterBlockPos origin = ctx.playerFeet();
        final ICachedWorld cache = ctx.worldData().getCachedWorld();
        final int epsilon = Math.max(8, Baritone.settings().baseFinderEpsilon.value);
        final int minScore = Math.max(0, Baritone.settings().baseFinderMinScore.value);
        final int minIndicators = Math.max(1, Baritone.settings().baseFinderMinIndicators.value);

        // ── Step 1: pull every indicator position ────────────────────────────
        logDirect("Scanning chunk cache for indicators...");
        List<Indicator> points = new ArrayList<>();
        Map<String, Integer> typeCounts = new HashMap<>();
        for (Map.Entry<String, Integer> entry : INDICATORS.entrySet()) {
            String name = entry.getKey();
            int weight = entry.getValue();
            ArrayList<BlockPos> positions = cache.getLocationsOf(
                    name, Integer.MAX_VALUE, origin.x, origin.z, Integer.MAX_VALUE);
            if (positions.isEmpty()) continue;
            typeCounts.merge(name, positions.size(), Integer::sum);
            for (BlockPos p : positions) {
                points.add(new Indicator(p.getX(), p.getZ(), name, weight));
            }
        }

        if (points.isEmpty()) {
            logDirect("No base indicators in cache for this dimension. "
                    + "Walk or elytra-fly through more chunks to populate the cache, "
                    + "then try again.");
            return;
        }

        // ── #bases pie — type breakdown then exit ────────────────────────────
        if (pie) {
            int total = points.size();
            logDirect("══ Indicator type breakdown (" + total + " total) ══");
            typeCounts.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .forEach(e -> {
                        double pct = e.getValue() * 100.0 / total;
                        logDirect(String.format("  %5d (%5.1f%%) %s", e.getValue(), pct, e.getKey()));
                    });
            return;
        }

        // ── Step 2: DBSCAN cluster ───────────────────────────────────────────
        logDirect("Clustering " + points.size() + " indicators (epsilon=" + epsilon + " blocks)...");
        List<List<Indicator>> clusters = dbscan(points, epsilon, minIndicators);

        // ── Step 3: score and filter ─────────────────────────────────────────
        List<Cluster> bases = new ArrayList<>();
        for (List<Indicator> cl : clusters) {
            int score = cl.stream().mapToInt(p -> p.weight).sum();
            if (score < minScore) continue;
            bases.add(new Cluster(cl, score));
        }
        bases.sort(Comparator.comparingInt((Cluster c) -> -c.score));

        if (bases.isEmpty()) {
            logDirect("No clusters scored ≥ " + minScore + ". Try lowering with "
                    + "#set baseFinderMinScore <lower>, or widen with "
                    + "#set baseFinderEpsilon <larger>.");
            return;
        }

        // ── Step 4: print results ────────────────────────────────────────────
        logDirect("══ Detected bases (" + bases.size() + " total, showing top "
                + Math.min(topN, bases.size()) + ") ══");
        for (int i = 0; i < Math.min(topN, bases.size()); i++) {
            Cluster c = bases.get(i);
            int cx = c.centerX();
            int cz = c.centerZ();
            int dx = cx - origin.x;
            int dz = cz - origin.z;
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            String types = summarizeTypes(c.points);
            logDirect(String.format(
                    "%2d. score %4d │ X=%5d Z=%5d │ %2d indicators │ ~%5d blocks │ %s",
                    i + 1, c.score, cx, cz, c.points.size(), dist, types));
        }

        // ── #bases <N> goto — path to the N'th base ──────────────────────────
        if (gotoIndex > 0) {
            if (gotoIndex > bases.size()) {
                logDirect("Only " + bases.size() + " base"
                        + (bases.size() == 1 ? "" : "s") + " found. Cannot goto #" + gotoIndex + ".");
                return;
            }
            Cluster target = bases.get(gotoIndex - 1);
            int cx = target.centerX();
            int cz = target.centerZ();
            logDirect("→ Pathing to base #" + gotoIndex + " at X=" + cx + " Z=" + cz);
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(cx, cz));
        }
    }

    // ── DBSCAN over (x, z) points ────────────────────────────────────────────

    /**
     * Density-based clustering on 2D (X, Z) points. Two points are in the same
     * cluster if they're within {@code epsilon} blocks of each other. Clusters
     * with fewer than {@code minPoints} indicators are dropped as noise.
     *
     * <p>O(n²) which is fine for n ≤ a few thousand — typical cached worlds
     * will not have more than that many tracked blocks within any reasonable area.
     */
    private static List<List<Indicator>> dbscan(List<Indicator> points, int epsilon, int minPoints) {
        int eps2 = epsilon * epsilon;
        int n = points.size();
        boolean[] visited = new boolean[n];
        int[] cluster = new int[n];
        Arrays.fill(cluster, -1);
        List<List<Indicator>> result = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            if (visited[i]) continue;
            visited[i] = true;
            List<Integer> neighbors = neighborsOf(points, i, eps2);
            if (neighbors.size() < minPoints) {
                cluster[i] = -2; // noise (unless absorbed later)
                continue;
            }
            // Expand a new cluster
            int cid = result.size();
            List<Indicator> cl = new ArrayList<>();
            result.add(cl);
            cluster[i] = cid;
            cl.add(points.get(i));

            // BFS through neighbors
            List<Integer> queue = new ArrayList<>(neighbors);
            for (int q = 0; q < queue.size(); q++) {
                int j = queue.get(q);
                if (!visited[j]) {
                    visited[j] = true;
                    List<Integer> sub = neighborsOf(points, j, eps2);
                    if (sub.size() >= minPoints) {
                        for (int k : sub) if (!queue.contains(k)) queue.add(k);
                    }
                }
                if (cluster[j] < 0) {
                    cluster[j] = cid;
                    cl.add(points.get(j));
                }
            }
        }
        return result;
    }

    private static List<Integer> neighborsOf(List<Indicator> points, int idx, int eps2) {
        List<Integer> out = new ArrayList<>();
        Indicator p = points.get(idx);
        for (int j = 0; j < points.size(); j++) {
            if (j == idx) continue;
            Indicator q = points.get(j);
            int dx = p.x - q.x;
            int dz = p.z - q.z;
            if (dx * dx + dz * dz <= eps2) out.add(j);
        }
        return out;
    }

    /** Summarises the indicators of a cluster as e.g. "2 beacons, 8 ender_chests, 12 shulkers". */
    private static String summarizeTypes(List<Indicator> indicators) {
        Map<String, Integer> counts = new HashMap<>();
        for (Indicator i : indicators) counts.merge(canonicalGroup(i.type), 1, Integer::sum);
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(4)
                .map(e -> e.getValue() + "× " + e.getKey())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    /** Collapses dyed variants (e.g. "red_shulker_box" → "shulker") for readable summary. */
    private static String canonicalGroup(String name) {
        if (name.endsWith("_shulker_box") || name.equals("shulker_box")) return "shulker";
        if (name.endsWith("_bed")) return "bed";
        return name;
    }

    // ── Records ──────────────────────────────────────────────────────────────

    private record Indicator(int x, int z, String type, int weight) {}

    private static final class Cluster {
        final List<Indicator> points;
        final int score;
        Cluster(List<Indicator> points, int score) {
            this.points = points;
            this.score = score;
        }
        int centerX() {
            return (int) points.stream().mapToInt(p -> p.x).average().orElse(0);
        }
        int centerZ() {
            return (int) points.stream().mapToInt(p -> p.z).average().orElse(0);
        }
    }

    // ── Boilerplate ──────────────────────────────────────────────────────────

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String p = "";
            try { p = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String pf = p;
            return Stream.of("pie", "5", "10", "20").filter(s -> s.startsWith(pf));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Find player bases by clustering cached indicator blocks";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Scans Baritone's per-dimension chunk cache for clusters of base-indicator",
                "blocks (beacons, ender chests, enchanting tables, anvils, shulkers, etc.),",
                "scores each cluster, and lists the top N — likely player bases.",
                "",
                "Reads cache only — no packets, no scans, no anticheat surface. Only finds",
                "blocks in chunks you've previously loaded.",
                "",
                "Usage:",
                "> bases             - list top 10 bases in current dimension",
                "> bases <N>         - list top N",
                "> bases pie         - show indicator type breakdown (percentage)",
                "> bases <N> goto    - path to the N'th base (uses GoalXZ)",
                "",
                "Tuning settings:",
                "  baseFinderMinScore       - minimum cluster score (default 30)",
                "  baseFinderMinIndicators  - minimum block count per cluster (default 3)",
                "  baseFinderEpsilon        - cluster radius in blocks (default 50)",
                "",
                "Tips:",
                "  - Best signal: beacons (50) + ender chests (30) + enchanting tables (25)",
                "  - Run #repack first to flush any newly-loaded chunks into the cache",
                "  - Per-dimension; run in the Nether and End separately",
                "",
                "Aliases: #basefinder"
        );
    }
}
