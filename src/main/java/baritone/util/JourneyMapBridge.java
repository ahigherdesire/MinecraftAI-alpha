/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalXZ;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import journeymap.client.waypoint.ClientWaypointImpl;
import journeymap.client.waypoint.WaypointHandler;
import journeymap.common.waypoint.WaypointBackend;
import journeymap.common.waypoint.WaypointIcon;
import journeymap.common.waypoint.WaypointPos;
import journeymap.common.waypoint.WaypointScope;
import journeymap.common.waypoint.WaypointSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.TreeSet;

/**
 * The actual JourneyMap API calls. This class is package-private and is ONLY
 * loaded by the JVM when {@link JourneyMapHelper#isAvailable()} has returned
 * {@code true}, guaranteeing JM classes are on the classpath.
 *
 * <p>Do NOT reference this class from anywhere except {@link JourneyMapHelper}.
 */
final class JourneyMapBridge {

    private JourneyMapBridge() {}

    /**
     * Subscribes a "#goto" item to JourneyMap's fullscreen right-click popup menu.
     * When clicked, the map position is sent to Baritone as a GoalXZ.
     * Called once from JourneyMapHelper after JM is confirmed available.
     */
    static void subscribePopupMenu() {
        FullscreenEventRegistry.FULLSCREEN_POPUP_MENU_EVENT.subscribe(
            "minecraftai",
            event -> event.getPopupMenu().addMenuItem(
                "Baritone #goto",
                pos -> {
                    // pos is the BlockPos the user right-clicked on the map (Y may be 0 on 2D map)
                    IBaritone bar = BaritoneAPI.getProvider().getPrimaryBaritone();
                    if (bar == null) return;
                    bar.getCustomGoalProcess().setGoalAndPath(new GoalXZ(pos.getX(), pos.getZ()));
                }
            )
        );
    }

    static void create(String name, BlockPos pos, int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        // Dimension string e.g. "minecraft:overworld"
        String dim = mc.level.dimension().identifier().toString();

        // Clamp Y — structure finder often returns Y=0 for structures
        int y = pos.getY() > 0 ? pos.getY() : 64;

        WaypointPos wpPos    = new WaypointPos(pos.getX(), y, pos.getZ(), dim);
        WaypointSettings cfg = new WaypointSettings(); // defaults: all enabled
        WaypointIcon icon    = new WaypointIcon(WaypointIcon.DEFAULT_ICON_NORMAL);

        TreeSet<String> dims = new TreeSet<>();
        dims.add(dim);

        ClientWaypointImpl wp = new ClientWaypointImpl(
            name,          // display name
            wpPos,         // position
            color,         // packed RGB colour
            cfg,           // show-on-map / beacon / etc.
            "minecraftai", // modId
            dims,          // dimensions this waypoint is visible in
            icon,          // icon
            null           // groupId — null → default group
        );

        WaypointHandler handler = WaypointHandler.getInstance();
        if (handler == null) return;

        WaypointBackend backend = handler.getBackend();
        if (backend == null) return;

        WaypointScope scope = backend.getDefaultScope();
        backend.saveWaypoint(scope, wp, /* markDirty= */ true);
    }
}
