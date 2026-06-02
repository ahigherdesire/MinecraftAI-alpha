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
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Navigates to the nearest nether portal (in whatever dimension the player is in).
 *
 * <p>Portal blocks are already tracked by Baritone's block cache, so this works for any
 * portal the player has previously been near. When {@code enterPortal} is enabled (the
 * default), the bot will walk directly into the portal and teleport through it.
 *
 * <p>If no portal is found in the cache, the bot will explore to find one.
 */
public class PortalCommand extends Command {

    public PortalCommand(IBaritone baritone) {
        super(baritone, "whereportal", "portal", "findportal");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        if (ctx.world() == null) {
            throw new CommandInvalidStateException("No world loaded.");
        }

        boolean willEnter = Baritone.settings().enterPortal.value;
        if (willEnter) {
            logDirect("Navigating to the nearest nether portal and entering it.");
            logDirect("(To just walk up to it without entering, set #set enterPortal false first)");
        } else {
            logDirect("Navigating to the nearest nether portal.");
            logDirect("(enterPortal is disabled — bot will stop next to the portal, not enter it)");
        }

        baritone.getGetToBlockProcess().getToBlock(Blocks.NETHER_PORTAL);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Go to (and optionally enter) the nearest nether portal";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
            "Finds and navigates to the nearest nether portal in the current dimension.",
            "",
            "Nether portal blocks are tracked in Baritone's block cache, so any portal",
            "the player has previously been near will be found instantly.",
            "If no portal is cached, the bot will explore to search for one.",
            "",
            "By default (enterPortal = true), the bot walks INTO the portal and teleports.",
            "To disable automatic entry:  #set enterPortal false",
            "",
            "Usage:",
            "> whereportal    - go to the nearest nether portal",
            "",
            "Aliases: #portal, #findportal",
            "",
            "Works in any dimension:",
            "  Overworld → finds an overworld portal (to go to the Nether)",
            "  Nether    → finds a nether portal (to return to the Overworld)"
        );
    }
}
