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

public class AutoFleeCommand extends Command {

    public AutoFleeCommand(IBaritone baritone) {
        super(baritone, "autoflee", "paranoia");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);
        if (!args.hasAny()) {
            boolean newVal = !Baritone.settings().autoFlee.value;
            Baritone.settings().autoFlee.value = newVal;
            logDirect("autoFlee is now " + (newVal ? "ON" : "OFF") + " (trigger=" +
                    Baritone.settings().autoFleeThreshold.value + " HP, distance=" +
                    Baritone.settings().autoFleeDistance.value + ")");
            return;
        }
        String arg = args.getString().toLowerCase();
        switch (arg) {
            case "on", "true", "enable" -> {
                Baritone.settings().autoFlee.value = true;
                logDirect("autoFlee enabled (trigger at " +
                        Baritone.settings().autoFleeThreshold.value + " HP, fleeing " +
                        Baritone.settings().autoFleeDistance.value + " blocks).");
            }
            case "off", "false", "disable" -> {
                Baritone.settings().autoFlee.value = false;
                logDirect("autoFlee disabled.");
            }
            case "status" -> {
                logDirect("autoFlee:                  " + (Baritone.settings().autoFlee.value ? "ON" : "off"));
                logDirect("autoFleeThreshold:         " + Baritone.settings().autoFleeThreshold.value + " HP (triggers below)");
                logDirect("autoFleeRecoverThreshold:  " + Baritone.settings().autoFleeRecoverThreshold.value + " HP (releases above)");
                logDirect("autoFleeDistance:          " + Baritone.settings().autoFleeDistance.value + " blocks");
            }
            default -> {
                try {
                    double n = Double.parseDouble(arg);
                    if (n < 1 || n > 20) {
                        throw new CommandInvalidStateException("Threshold must be 1.0–20.0 HP.");
                    }
                    Baritone.settings().autoFleeThreshold.value = n;
                    Baritone.settings().autoFlee.value = true;
                    logDirect("autoFlee enabled, trigger = " + n + " HP.");
                } catch (NumberFormatException e) {
                    throw new CommandInvalidStateException(
                            "Unknown argument '" + arg + "'. Try on/off/status or a number (HP).");
                }
            }
        }
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
        return "Auto-flee when health drops below a threshold";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Watches your health and automatically runs away when it drops below the",
                "threshold (default 6 HP / 3 hearts). Releases control when health recovers.",
                "",
                "Usage:",
                "> autoflee           - toggle on/off",
                "> autoflee on / off  - explicit",
                "> autoflee <HP>      - set trigger HP and enable",
                "> autoflee status    - show current settings",
                "",
                "Related settings:",
                "  autoFleeThreshold         - HP below which to start fleeing (default 6.0)",
                "  autoFleeRecoverThreshold  - HP above which to stop fleeing  (default 16.0)",
                "  autoFleeDistance          - flee distance in blocks         (default 48)",
                "",
                "Aliases: #paranoia"
        );
    }
}
