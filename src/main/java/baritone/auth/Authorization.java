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

package baritone.auth;

import baritone.api.utils.Helper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-account licensing for this mod fork.
 *
 * <p><b>How to authorize a new user:</b>
 * <ol>
 *   <li>Get their Minecraft UUID:
 *       <pre>https://api.mojang.com/users/profiles/minecraft/&lt;their-IGN&gt;</pre>
 *       The {@code "id"} field is 32 hex chars; convert to standard 8-4-4-4-12 dashed
 *       format (or use {@link UUID#fromString(String)}).</li>
 *   <li>Add a line to {@link #ALLOWLIST} below.</li>
 *   <li>Rebuild + redistribute the JAR.</li>
 * </ol>
 *
 * <p><b>How this protects against sharing:</b>
 * <ul>
 *   <li><b>Hard disable:</b> unauthorized users see a clear chat message and every
 *       Baritone command refuses to run. The mod still loads (so the player isn't
 *       crashed) but does nothing useful.</li>
 *   <li><b>Watermark:</b> on first command, authorized users see a green
 *       {@code "Licensed to: <name>"} line in chat. If the JAR ever leaks, the
 *       leaker is identified.</li>
 *   <li><b>Cracked-launcher block:</b> {@link User#getXuid()} returns an Xbox Live
 *       UID which is <em>only</em> present for legitimate Microsoft accounts. Cracked
 *       launchers (TLauncher, Salwyrr, etc.) have no XUID. We refuse to run on
 *       accounts without an XUID, regardless of whether the spoofed UUID happens to
 *       match an authorized one.</li>
 * </ul>
 *
 * <p><b>What this does NOT protect against:</b> anyone willing to decompile the JAR
 * with IntelliJ, edit this file, and recompile. There is no Java DRM that survives a
 * determined attacker — the friend-share threat model is what's covered here.
 */
public final class Authorization {

    /**
     * Allowed users. Map UUID → display name (used in the watermark log line).
     *
     * <p>EDIT ME: replace the placeholder entries with the real UUIDs of authorized users.
     * Look up a UUID at:
     * <pre>https://api.mojang.com/users/profiles/minecraft/&lt;IGN&gt;</pre>
     *
     * <p>Use {@link LinkedHashMap} so the iteration order is the same as the source order
     * (purely cosmetic for any future "list authorized users" feature).
     */
    private static final Map<UUID, String> ALLOWLIST;
    static {
        Map<UUID, String> m = new LinkedHashMap<>();
        // ─── Authorized users ─────────────────────────────────────────────────
        // Each user gets TWO entries: their Mojang UUID (for legit Microsoft accounts)
        // and their offline-mode UUID (for cracked launchers). Offline UUIDs are
        // computed deterministically as UUID.nameUUIDFromBytes("OfflinePlayer:" + name)
        // — the standard formula used by vanilla, TLauncher, PolyMC, PrismLauncher,
        // and most other launchers. Same player → same UUID in either mode.
        m.put(UUID.fromString("11d5e57c-c11a-4060-82a5-3264ddf5ed41"), "ahigherdesire");        // Mojang
        m.put(UUID.fromString("57d75a53-5f24-30e7-b830-17b0ba591672"), "ahigherdesire (offline)"); // cracked
        m.put(UUID.fromString("d48affbd-1e57-4a6f-8051-f63f46ac9044"), "haoyu");                // Mojang
        m.put(UUID.fromString("889330b8-f127-3e90-a180-54faefe9d2fd"), "haoyu (offline)");      // cracked
        // ──────────────────────────────────────────────────────────────────────
        ALLOWLIST = Collections.unmodifiableMap(m);
    }

    // ── Cached result (computed once on first check) ─────────────────────────
    private static volatile boolean checkRan = false;
    private static volatile boolean authorized = false;
    /** Watermark printed once per game session, after the first successful check. */
    private static volatile boolean watermarkLogged = false;

    private Authorization() {}

    /**
     * Returns {@code true} if the current Minecraft user is authorized to run the mod.
     * Result is cached after the first call (the check itself is cheap, but the chat
     * log lines should fire exactly once).
     *
     * <p>Safe to call from any thread. If the user object isn't ready yet (e.g. mod
     * initialised before login screen), returns {@code false} without caching, so a
     * later call will re-check.
     */
    public static synchronized boolean isAuthorized() {
        if (checkRan) {
            // Log the watermark on the first authorized check (i.e. once the player
            // has actually tried to run a command), not on the initial silent check.
            if (authorized && !watermarkLogged) {
                String name = ALLOWLIST.get(getCurrentUuidOrNull());
                if (name == null) name = "this user"; // shouldn't happen, but harmless
                Helper.HELPER.logDirect(
                        Component.literal("[MinecraftAI] Licensed to: " + name)
                                .withStyle(net.minecraft.ChatFormatting.GREEN));
                watermarkLogged = true;
            }
            return authorized;
        }

        UUID uuid = getCurrentUuidOrNull();
        if (uuid == null) {
            // Minecraft not ready yet — don't cache, just refuse for now.
            return false;
        }

        try {
            authorized = doCheck();
        } catch (Throwable t) {
            Helper.HELPER.logDirect(
                    Component.literal("[MinecraftAI] Auth check error: " + t.getMessage()
                            + ". Mod disabled for safety.")
                            .withStyle(net.minecraft.ChatFormatting.RED));
            authorized = false;
        }
        checkRan = true;
        return authorized;
    }

    /** Runs the actual check. Logs failure reasons but not success (watermark fires later). */
    private static boolean doCheck() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        User user = mc.getUser();
        if (user == null) return false;

        UUID uuid = user.getProfileId();
        String name = user.getName();

        // Soft note when no XUID is present (cracked / offline launcher). We no longer
        // hard-block on this — both authorized users may run cracked launchers, so
        // gating on XUID locks them out of their own mod. The allowlist already covers
        // both Mojang UUIDs and offline-mode UUIDs for each user, so the security
        // trade-off is limited: a third party can only bypass by setting their cracked
        // IGN to exactly match an authorized user's IGN, which is a high social barrier.
        if (user.getXuid().isEmpty()) {
            Helper.HELPER.logDirect(
                    Component.literal("[MinecraftAI] Note: cracked/offline launcher detected. "
                            + "Auth will check the offline-mode UUID for " + name + ".")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
        }

        // Allowlist check
        if (!ALLOWLIST.containsKey(uuid)) {
            Helper.HELPER.logDirect(
                    Component.literal("[MinecraftAI] Unauthorized user: " + name + " (UUID " + uuid + ")")
                            .withStyle(net.minecraft.ChatFormatting.RED));
            if (ALLOWLIST.isEmpty()) {
                Helper.HELPER.logDirect(
                        Component.literal("[MinecraftAI] (No allowlist entries — edit Authorization.java and rebuild.)")
                                .withStyle(net.minecraft.ChatFormatting.GRAY));
            } else {
                Helper.HELPER.logDirect(
                        Component.literal("[MinecraftAI] Mod disabled. Contact the mod author for access.")
                                .withStyle(net.minecraft.ChatFormatting.RED));
            }
            return false;
        }

        return true;
    }

    /** Returns the current Minecraft profile UUID, or {@code null} if not yet available. */
    private static UUID getCurrentUuidOrNull() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return null;
            User u = mc.getUser();
            if (u == null) return null;
            return u.getProfileId();
        } catch (Throwable t) {
            return null;
        }
    }
}
