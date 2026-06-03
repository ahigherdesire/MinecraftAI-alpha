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

package baritone.behavior;

import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.utils.Helper;
import baritone.api.utils.input.Input;
import baritone.util.SleepHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.Set;

/**
 * The Autopilot Survival behavior — keeps the player alive while idle or while
 * other Baritone processes are running. Each feature is reactive to a measurable
 * threshold (hunger, health, light, time of day). All features default OFF and
 * are independently toggled via settings or their dedicated commands.
 *
 * <p>Features:
 * <ul>
 *   <li><b>Auto-eat</b> — eats food when hunger drops below {@code autoEatThreshold}.</li>
 *   <li><b>Auto-flee</b> — runs away when health drops below {@code autoFleeThreshold}.</li>
 *   <li><b>Auto-sleep</b> — sleeps in the nearest cached bed when night falls.</li>
 *   <li><b>Auto-torch</b> — places torches in dark areas while traveling.</li>
 * </ul>
 *
 * <p>Death-point waypointing is handled by {@code WaypointBehavior} (built-in
 * Baritone feature via {@code doDeathWaypoints} setting, default {@code true}).
 */
public final class AutopilotBehavior extends Behavior implements AbstractGameEventListener {

    // ── Edible items (any inventory match qualifies for auto-eat) ────────────
    private static final Set<Item> NORMAL_FOOD = Set.of(
            Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN,
            Items.COOKED_MUTTON, Items.COOKED_RABBIT, Items.COOKED_SALMON,
            Items.COOKED_COD, Items.BAKED_POTATO, Items.BREAD,
            Items.GOLDEN_CARROT, Items.CARROT, Items.POTATO, Items.BEETROOT,
            Items.APPLE, Items.MELON_SLICE, Items.SWEET_BERRIES, Items.GLOW_BERRIES,
            Items.COOKIE, Items.PUMPKIN_PIE, Items.DRIED_KELP, Items.CHORUS_FRUIT,
            Items.BEETROOT_SOUP, Items.MUSHROOM_STEW, Items.RABBIT_STEW, Items.HONEY_BOTTLE,
            Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON,
            Items.RABBIT, Items.SALMON, Items.COD, Items.TROPICAL_FISH,
            Items.PUFFERFISH
    );
    private static final Set<Item> GAPPLES = Set.of(
            Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE
    );
    /** Items we will NEVER auto-eat even though they have food properties. */
    private static final Set<Item> NEVER_EAT = Set.of(
            Items.SUSPICIOUS_STEW,   // unpredictable status effect
            Items.POISONOUS_POTATO,  // poison
            Items.ROTTEN_FLESH,      // hunger effect
            Items.SPIDER_EYE         // poison
    );

    // ── Eat state machine ───────────────────────────────────────────────────
    private enum EatPhase { IDLE, SWITCHING, EATING, COOLDOWN }
    /** Ticks of eating animation required (~1.6 s = 32 ticks; +4 ticks safety margin). */
    private static final int EAT_TICKS = 36;
    /** Cool-down after a successful eat so we don't insta-trigger again. */
    private static final int EAT_COOLDOWN_TICKS = 20;

    private EatPhase eatPhase = EatPhase.IDLE;
    private int eatTicksRemaining = 0;
    private int previousHotbarSlot = -1;

    // ── Flee state ──────────────────────────────────────────────────────────
    private boolean fleeing = false;
    private BlockPos fleeOrigin = null;

    // ── Sleep state ─────────────────────────────────────────────────────────
    private boolean sleepInProgress = false;

    // ── Torch state ─────────────────────────────────────────────────────────
    private int torchCooldownTicks = 0;

    // ── Experimental-warning state ──────────────────────────────────────────
    // Tracks previous tick's setting value so we can fire a one-time warning
    // on each false→true transition. Mirrors the elytraWaveMode pattern.
    private boolean prevAutoEat = false;
    private boolean prevAutoFlee = false;
    private boolean prevAutoSleep = false;
    private boolean prevAutoTorch = false;

    public AutopilotBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            // World unloading — reset everything (including warning history,
            // so re-entering a world re-fires the experimental warnings).
            this.eatPhase = EatPhase.IDLE;
            this.eatTicksRemaining = 0;
            this.previousHotbarSlot = -1;
            this.fleeing = false;
            this.fleeOrigin = null;
            this.sleepInProgress = false;
            this.torchCooldownTicks = 0;
            this.prevAutoEat = false;
            this.prevAutoFlee = false;
            this.prevAutoSleep = false;
            this.prevAutoTorch = false;
            return;
        }
        if (ctx.player() == null || ctx.world() == null) return;

        // ── Experimental-warning detection (one-time on false→true) ──────────
        checkExperimentalWarnings();

        // Tick the sub-systems. Order matters: flee outranks eat outranks sleep.
        tickFlee();
        tickEat();
        tickSleep();
        tickTorch();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  EXPERIMENTAL WARNINGS
    //  Mirrors the elytraWaveMode pattern — fires one-time on false→true
    //  transition so the user knows the feature is experimental.
    // ════════════════════════════════════════════════════════════════════════

    private void checkExperimentalWarnings() {
        final boolean curAutoEat   = Baritone.settings().autoEat.value;
        final boolean curAutoFlee  = Baritone.settings().autoFlee.value;
        final boolean curAutoSleep = Baritone.settings().autoSleep.value;
        final boolean curAutoTorch = Baritone.settings().autoTorch.value;

        if (curAutoEat && !prevAutoEat) {
            logHelper("⚠ autoEat is EXPERIMENTAL. May misfire, fail to fire, or pick the wrong "
                    + "food. Hotbar-only (food must be in slots 1–9). Disable with #autoeat off "
                    + "or #set autoEat false.");
        }
        if (curAutoFlee && !prevAutoFlee) {
            logHelper("⚠ autoFlee is EXPERIMENTAL. GoalRunAway doesn't know about lava / cliffs — "
                    + "the bot may flee in a dangerous direction. Use #cancel if it picks a bad "
                    + "path. Disable with #autoflee off or #set autoFlee false.");
        }
        if (curAutoSleep && !prevAutoSleep) {
            logHelper("⚠ autoSleep is EXPERIMENTAL. Requires a previously-cached bed; will not "
                    + "search beyond the disk cache. Will not interrupt active tasks unless "
                    + "autoSleepInterruptTasks=true. Disable with #autosleep off.");
        }
        if (curAutoTorch && !prevAutoTorch) {
            logHelper("⚠ autoTorch is EXPERIMENTAL. Hotbar-only torch placement; may fire while "
                    + "you're trying to mine or build. Cave-only by default (sky-light < 4). "
                    + "Disable with #autotorch off or #set autoTorch false.");
        }

        prevAutoEat   = curAutoEat;
        prevAutoFlee  = curAutoFlee;
        prevAutoSleep = curAutoSleep;
        prevAutoTorch = curAutoTorch;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  AUTO-EAT
    // ════════════════════════════════════════════════════════════════════════

    private void tickEat() {
        if (!Baritone.settings().autoEat.value) {
            if (eatPhase != EatPhase.IDLE) cancelEat();
            return;
        }
        // Cannot eat while sleeping, while elytra flying, or while a GUI is open.
        if (ctx.player().isSleeping()
                || ctx.player().isFallFlying()
                || ctx.minecraft().screen != null) {
            return;
        }
        // Don't eat while actively fleeing — running takes priority.
        if (fleeing) return;

        switch (eatPhase) {
            case IDLE -> {
                final int hunger = ctx.player().getFoodData().getFoodLevel();
                final int threshold = Baritone.settings().autoEatThreshold.value;
                if (hunger >= threshold) return;

                final int slot = findFoodSlot();
                if (slot < 0) return; // no food in inventory

                // Only handles hotbar slots. Food in main inventory is ignored for now
                // (would require vanilla swap-with-hotbar API which moved/renamed in MC 26.1).
                // Workaround: keep food in your hotbar — most players do this anyway.
                if (slot < 9) {
                    previousHotbarSlot = ctx.player().getInventory().getSelectedSlot();
                    if (previousHotbarSlot != slot) {
                        ctx.player().getInventory().setSelectedSlot(slot);
                    }
                    startEating();
                } else {
                    // Don't spam this — only log once per cooldown
                    if (eatPhase == EatPhase.IDLE) {
                        eatPhase = EatPhase.COOLDOWN;
                        eatTicksRemaining = 200; // 10s cooldown before re-warning
                        logHelper("⚠ auto-eat: food found in main inventory slot " + slot
                                + " — move it to your hotbar so I can use it.");
                    }
                }
            }
            case SWITCHING -> {
                // SWITCHING is a 1-tick state to let the slot change propagate
                eatPhase = EatPhase.EATING;
            }
            case EATING -> {
                eatTicksRemaining--;
                // Hold right-click for the eat animation
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                if (eatTicksRemaining <= 0) {
                    baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
                    // Restore previous hotbar slot
                    if (previousHotbarSlot >= 0 && previousHotbarSlot < 9) {
                        ctx.player().getInventory().setSelectedSlot(previousHotbarSlot);
                    }
                    previousHotbarSlot = -1;
                    eatPhase = EatPhase.COOLDOWN;
                    eatTicksRemaining = EAT_COOLDOWN_TICKS;
                }
            }
            case COOLDOWN -> {
                eatTicksRemaining--;
                if (eatTicksRemaining <= 0) {
                    eatPhase = EatPhase.IDLE;
                }
            }
        }
    }

    private void startEating() {
        eatPhase = EatPhase.SWITCHING;
        eatTicksRemaining = EAT_TICKS;
    }

    private void cancelEat() {
        if (eatPhase == EatPhase.EATING) {
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
        }
        if (previousHotbarSlot >= 0 && previousHotbarSlot < 9 && ctx.player() != null) {
            ctx.player().getInventory().setSelectedSlot(previousHotbarSlot);
        }
        previousHotbarSlot = -1;
        eatPhase = EatPhase.IDLE;
        eatTicksRemaining = 0;
    }

    /** Returns inventory slot (0-35) of the best food, or -1 if none. */
    private int findFoodSlot() {
        final Inventory inv = ctx.player().getInventory();
        final boolean allowGapples = Baritone.settings().autoEatAllowGapples.value;
        int bestSlot = -1;
        int bestScore = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (NEVER_EAT.contains(item)) continue;
            if (GAPPLES.contains(item) && !allowGapples) continue;

            int score = scoreFood(item);
            if (score < 0) continue;
            // Prefer hotbar slots if score is tied (less inventory shuffling)
            if (score > bestScore || (score == bestScore && i < 9 && bestSlot >= 9)) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /** Crude saturation/hunger scoring. Higher = better food choice. */
    private int scoreFood(Item item) {
        if (item == Items.GOLDEN_CARROT) return 100;
        if (item == Items.COOKED_BEEF || item == Items.COOKED_PORKCHOP || item == Items.COOKED_MUTTON) return 90;
        if (item == Items.RABBIT_STEW || item == Items.BEETROOT_SOUP || item == Items.MUSHROOM_STEW) return 80;
        if (item == Items.COOKED_CHICKEN || item == Items.COOKED_SALMON || item == Items.BAKED_POTATO) return 70;
        if (item == Items.COOKED_RABBIT || item == Items.COOKED_COD || item == Items.PUMPKIN_PIE) return 60;
        if (item == Items.BREAD || item == Items.GLOW_BERRIES || item == Items.HONEY_BOTTLE) return 50;
        if (item == Items.APPLE || item == Items.CARROT || item == Items.MELON_SLICE) return 30;
        if (item == Items.COOKIE || item == Items.DRIED_KELP || item == Items.SWEET_BERRIES
                || item == Items.POTATO || item == Items.BEETROOT || item == Items.CHORUS_FRUIT) return 20;
        // Raw meat — last resort
        if (item == Items.BEEF || item == Items.PORKCHOP || item == Items.CHICKEN || item == Items.MUTTON
                || item == Items.RABBIT || item == Items.SALMON || item == Items.COD
                || item == Items.TROPICAL_FISH) return 10;
        // Golden apples — fallback if allowed and nothing else
        if (item == Items.GOLDEN_APPLE) return 5;
        if (item == Items.ENCHANTED_GOLDEN_APPLE) return 1;
        return -1;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  AUTO-FLEE
    // ════════════════════════════════════════════════════════════════════════

    private void tickFlee() {
        if (!Baritone.settings().autoFlee.value) {
            if (fleeing) stopFleeing();
            return;
        }
        // Don't trigger flee while flying — there's no way to escape on foot.
        if (ctx.player().isFallFlying()) return;

        final float hp = ctx.player().getHealth();
        final double trigger = Baritone.settings().autoFleeThreshold.value;
        final double recover = Baritone.settings().autoFleeRecoverThreshold.value;

        if (!fleeing) {
            if (hp <= trigger) {
                fleeOrigin = ctx.playerFeet();
                int dist = Baritone.settings().autoFleeDistance.value;
                fleeing = true;
                logHelper("⚠ Auto-flee triggered: health=" + String.format("%.1f", hp)
                        + ", fleeing " + dist + " blocks away.");
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalRunAway(dist, fleeOrigin));
            }
        } else {
            if (hp >= recover) {
                logHelper("Auto-flee released: health recovered to " + String.format("%.1f", hp));
                stopFleeing();
            }
        }
    }

    private void stopFleeing() {
        fleeing = false;
        fleeOrigin = null;
        // Don't forcibly cancel the path — the user may have set a new goal during the flee.
        // The GoalRunAway is satisfied once we're far enough; releasing it via setting goal=null
        // would cancel any subsequent commands.
    }

    // ════════════════════════════════════════════════════════════════════════
    //  AUTO-SLEEP
    // ════════════════════════════════════════════════════════════════════════

    private void tickSleep() {
        if (!Baritone.settings().autoSleep.value) {
            sleepInProgress = false;
            return;
        }
        if (ctx.player().isSleeping()) {
            sleepInProgress = false; // we're asleep, done
            return;
        }
        if (!SleepHelper.isNightOrStorm(ctx.world())) return;

        // Don't yank an active task unless explicitly allowed
        if (!Baritone.settings().autoSleepInterruptTasks.value) {
            if (baritone.getPathingControlManager().mostRecentInControl().isPresent()) return;
        }

        if (sleepInProgress) return; // already navigating to a bed

        Optional<BlockPos> bed = SleepHelper.findNearestBed(ctx);
        if (bed.isEmpty()) return; // no bed cached — silent skip

        sleepInProgress = true;
        BlockPos b = bed.get();
        logHelper("Auto-sleep: night detected. Navigating to bed at X="
                + b.getX() + " Y=" + b.getY() + " Z=" + b.getZ() + ".");
        baritone.getCustomGoalProcess().setGoalAndPath(
                new baritone.api.pathing.goals.GoalNear(b, 1));
        // Note: actual right-click on the bed is left to the user / SleepCommand;
        // a future iteration can fire it automatically once near the bed.
    }

    // ════════════════════════════════════════════════════════════════════════
    //  AUTO-TORCH
    // ════════════════════════════════════════════════════════════════════════

    private void tickTorch() {
        if (torchCooldownTicks > 0) torchCooldownTicks--;
        if (!Baritone.settings().autoTorch.value) return;
        if (torchCooldownTicks > 0) return;

        final Level world = ctx.world();
        // Disabled in Nether (lava risk) and End (pointless)
        if (world.dimension() == Level.NETHER || world.dimension() == Level.END) return;
        if (ctx.player().isFallFlying()) return;

        final BlockPos feet = ctx.playerFeet();
        final int blockLight = world.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, feet);
        final int threshold = Baritone.settings().autoTorchLightLevel.value;
        if (blockLight >= threshold) return;

        if (Baritone.settings().autoTorchOnlyInCaves.value) {
            int skyLight = world.getBrightness(net.minecraft.world.level.LightLayer.SKY, feet);
            if (skyLight >= 4) return; // probably on the surface
        }

        // Find a torch in hotbar only (slots 0–8)
        int torchSlot = -1;
        Inventory inv = ctx.player().getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).getItem() == Items.TORCH) {
                torchSlot = i;
                break;
            }
        }
        if (torchSlot < 0) return; // no torches in hotbar; silently skip

        // Place by right-clicking the block below the player feet (the floor)
        BlockPos floor = feet.below();
        if (!world.getBlockState(floor).isSolid()) return; // not standing on solid

        // Switch to torch
        int prevSlot = inv.getSelectedSlot();
        inv.setSelectedSlot(torchSlot);

        // Place by simulating use against the floor
        try {
            ctx.minecraft().gameMode.useItem(ctx.player(), InteractionHand.MAIN_HAND);
        } catch (Throwable ignored) {}

        // Restore selected slot
        inv.setSelectedSlot(prevSlot);
        torchCooldownTicks = Math.max(20, Baritone.settings().autoTorchIntervalTicks.value);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PUBLIC STATE — read-only accessors for #autopilot status / commands
    // ════════════════════════════════════════════════════════════════════════

    public boolean isEating() { return eatPhase != EatPhase.IDLE && eatPhase != EatPhase.COOLDOWN; }
    public boolean isFleeing() { return fleeing; }
    public boolean isSleepInProgress() { return sleepInProgress; }

    // ════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════════════

    private void logHelper(String msg) {
        Helper.HELPER.logDirect("[Autopilot] " + msg);
    }
}
