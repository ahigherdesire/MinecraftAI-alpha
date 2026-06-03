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
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Master toggle that batch-enables/disables all four Autopilot Survival features
 * at once. Does not override individual settings — just flips the group.
 */
public class AutopilotCommand extends Command {

    public AutopilotCommand(IBaritone baritone) {
        super(baritone, "autopilot", "survive");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);
        if (!args.hasAny()) {
            // Show status as the default no-arg behavior — switching all 4 at once via toggle
            // is too unpredictable.
            showStatus();
            return;
        }
        String arg = args.getString().toLowerCase();
        switch (arg) {
            case "on", "true", "enable" -> {
                Baritone.settings().autoEat.value = true;
                Baritone.settings().autoFlee.value = true;
                Baritone.settings().autoSleep.value = true;
                Baritone.settings().autoTorch.value = true;
                logDirect("✓ Autopilot ON — autoEat, autoFlee, autoSleep, autoTorch all enabled.");
                logDirect("(deathpoint waypointing was already on via Baritone's built-in doDeathWaypoints)");
                logDirect("Use individual commands to fine-tune: #autoeat, #autoflee, #autosleep, #autotorch");
            }
            case "off", "false", "disable" -> {
                Baritone.settings().autoEat.value = false;
                Baritone.settings().autoFlee.value = false;
                Baritone.settings().autoSleep.value = false;
                Baritone.settings().autoTorch.value = false;
                logDirect("✗ Autopilot OFF — all four features disabled.");
            }
            case "status" -> showStatus();
            default -> throw new CommandInvalidStateException(
                    "Unknown argument '" + arg + "'. Try on/off/status.");
        }
    }

    private void showStatus() {
        logDirect("──── Autopilot Survival ────");
        logDirect("autoEat:    " + onOff(Baritone.settings().autoEat.value)
                + "  (threshold " + Baritone.settings().autoEatThreshold.value + ")");
        logDirect("autoFlee:   " + onOff(Baritone.settings().autoFlee.value)
                + "  (trigger " + Baritone.settings().autoFleeThreshold.value + " HP, dist "
                + Baritone.settings().autoFleeDistance.value + ")");
        logDirect("autoSleep:  " + onOff(Baritone.settings().autoSleep.value)
                + "  (interrupt=" + Baritone.settings().autoSleepInterruptTasks.value + ")");
        logDirect("autoTorch:  " + onOff(Baritone.settings().autoTorch.value)
                + "  (light<" + Baritone.settings().autoTorchLightLevel.value
                + ", cavesOnly=" + Baritone.settings().autoTorchOnlyInCaves.value + ")");
        logDirect("deathpoint: " + onOff(Baritone.settings().doDeathWaypoints.value)
                + "  (built-in Baritone)");
    }

    private static String onOff(boolean b) {
        return b ? "ON " : "off";
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String p = "";
            try { p = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String pf = p;
            return Stream.of("on", "off", "status").filter(s -> s.startsWith(pf));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Toggle all Autopilot Survival features at once";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Master toggle for the Autopilot Survival update.",
                "",
                "Usage:",
                "> autopilot          - show current status of all features",
                "> autopilot on       - enable autoEat, autoFlee, autoSleep, autoTorch",
                "> autopilot off      - disable all four",
                "> autopilot status   - show current status",
                "",
                "Individual commands fine-tune thresholds:",
                "  #autoeat / #eat        - hunger autopilot",
                "  #autoflee / #paranoia  - low-health flee autopilot",
                "  #autosleep / #nightowl - night-time bed autopilot",
                "  #autotorch / #torch    - dark-area torch placer",
                "",
                "Aliases: #survive"
        );
    }
}
