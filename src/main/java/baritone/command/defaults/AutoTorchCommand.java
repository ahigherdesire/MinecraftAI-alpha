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

public class AutoTorchCommand extends Command {

    public AutoTorchCommand(IBaritone baritone) {
        super(baritone, "autotorch", "torch");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);
        if (!args.hasAny()) {
            boolean newVal = !Baritone.settings().autoTorch.value;
            Baritone.settings().autoTorch.value = newVal;
            logDirect("autoTorch is now " + (newVal ? "ON" : "OFF") +
                    " (light<" + Baritone.settings().autoTorchLightLevel.value + ")");
            return;
        }
        String arg = args.getString().toLowerCase();
        switch (arg) {
            case "on", "true", "enable" -> {
                Baritone.settings().autoTorch.value = true;
                logDirect("autoTorch enabled (light < " +
                        Baritone.settings().autoTorchLightLevel.value + ").");
            }
            case "off", "false", "disable" -> {
                Baritone.settings().autoTorch.value = false;
                logDirect("autoTorch disabled.");
            }
            case "surface" -> {
                // Toggle the "only in caves" restriction
                Baritone.settings().autoTorchOnlyInCaves.value =
                        !Baritone.settings().autoTorchOnlyInCaves.value;
                logDirect("autoTorchOnlyInCaves is now " +
                        (Baritone.settings().autoTorchOnlyInCaves.value ? "ON (caves only)" : "OFF (surface allowed)"));
            }
            case "status" -> {
                logDirect("autoTorch:              " + (Baritone.settings().autoTorch.value ? "ON" : "off"));
                logDirect("autoTorchLightLevel:    " + Baritone.settings().autoTorchLightLevel.value);
                logDirect("autoTorchIntervalTicks: " + Baritone.settings().autoTorchIntervalTicks.value);
                logDirect("autoTorchOnlyInCaves:   " + Baritone.settings().autoTorchOnlyInCaves.value);
            }
            default -> {
                try {
                    int n = Integer.parseInt(arg);
                    if (n < 0 || n > 15) {
                        throw new CommandInvalidStateException("Light level must be 0–15.");
                    }
                    Baritone.settings().autoTorchLightLevel.value = n;
                    Baritone.settings().autoTorch.value = true;
                    logDirect("autoTorch enabled, light < " + n + ".");
                } catch (NumberFormatException e) {
                    throw new CommandInvalidStateException(
                            "Unknown argument '" + arg + "'. Try on/off/surface/status or a number 0–15.");
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
            return Stream.of("on", "off", "surface", "status").filter(s -> s.startsWith(pf));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Place torches automatically in dark areas";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "While walking, automatically place a torch when the block light at the",
                "player drops below the threshold (default 8 — the mob-spawn level).",
                "",
                "Disabled in the Nether (lava risk) and the End (no point).",
                "By default restricted to caves (sky-light < 4) so it won't litter the",
                "surface of a base — toggle with `autotorch surface`.",
                "",
                "Usage:",
                "> autotorch            - toggle on/off",
                "> autotorch on / off   - explicit",
                "> autotorch surface    - toggle whether to allow surface placement",
                "> autotorch <0-15>     - set light threshold and enable",
                "> autotorch status     - show current settings",
                "",
                "Aliases: #torch"
        );
    }
}
