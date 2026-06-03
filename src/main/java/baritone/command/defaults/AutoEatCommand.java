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

public class AutoEatCommand extends Command {

    public AutoEatCommand(IBaritone baritone) {
        super(baritone, "autoeat", "eat");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);
        if (!args.hasAny()) {
            // Toggle
            boolean newVal = !Baritone.settings().autoEat.value;
            Baritone.settings().autoEat.value = newVal;
            logDirect("autoEat is now " + (newVal ? "ON" : "OFF") + " (threshold=" +
                    Baritone.settings().autoEatThreshold.value + ")");
            return;
        }
        String arg = args.getString().toLowerCase();
        switch (arg) {
            case "on", "true", "enable" -> {
                Baritone.settings().autoEat.value = true;
                logDirect("autoEat enabled (threshold=" + Baritone.settings().autoEatThreshold.value + ")");
            }
            case "off", "false", "disable" -> {
                Baritone.settings().autoEat.value = false;
                logDirect("autoEat disabled.");
            }
            case "status" -> {
                logDirect("autoEat:           " + (Baritone.settings().autoEat.value ? "ON" : "off"));
                logDirect("autoEatThreshold:  " + Baritone.settings().autoEatThreshold.value + " (out of 20)");
                logDirect("autoEatAllowGapples: " + Baritone.settings().autoEatAllowGapples.value);
            }
            default -> {
                // Numeric threshold
                try {
                    int n = Integer.parseInt(arg);
                    if (n < 1 || n > 20) {
                        throw new CommandInvalidStateException("Threshold must be 1–20.");
                    }
                    Baritone.settings().autoEatThreshold.value = n;
                    Baritone.settings().autoEat.value = true;
                    logDirect("autoEat enabled, threshold = " + n);
                } catch (NumberFormatException e) {
                    throw new CommandInvalidStateException(
                            "Unknown argument '" + arg + "'. Try on/off/status or a number 1–20.");
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
        return "Auto-eat food when hungry";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Watches your hunger level and automatically eats food from your inventory",
                "when it drops below the threshold (default 14 of 20).",
                "",
                "Usage:",
                "> autoeat            - toggle on/off",
                "> autoeat on / off   - explicit",
                "> autoeat <1-20>     - set threshold and enable",
                "> autoeat status     - show current settings",
                "",
                "Related settings:",
                "  autoEatThreshold     - hunger level below which to eat (default 14)",
                "  autoEatAllowGapples  - allow eating golden apples (default false)",
                "",
                "Aliases: #eat"
        );
    }
}
