/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.system.THMSystem;

/**
 * Main-account privacy: PvP, PvE, HUD, Homes, Stash Mover, and other local combat/farm
 * modules always run, including at a stash. Chat, coordinates, screenshots, webhooks,
 * API stats, packet-log positions, and KitBot may leave this client only while Highway
 * Builder is paving the official 6b6t nether highway, plus {@link #GRACE_MS} after it
 * turns off (so disable-time webhooks can still send while you are still on that highway).
 *
 * <p>Highway stats/status API posts are separate: an attested official-highway session may
 * POST aggregate counts after {@code /home} or reconnect, matching the main repo send path.
 * That path still refuses coordinate triples and never unlocks KitBot, Discord, or screenshots.
 *
 * <p>Position is read live and fails closed for everything that can leak a stash. A sticky
 * "last on highway" flag would keep exporting after {@code /home} and could summon KitBot
 * there. There is no opt-out.
 */
public final class PrivacyGuard {
    public static final long GRACE_MS = 5_000L;

    public static final String REMOTE_EXPORT_BLOCKED =
        "Chat, coordinates, webhooks, and KitBot only leave this client while Highway Builder is paving the official nether highway, plus 5 seconds after it turns off.";

    private static final PrivacyGuard INSTANCE = new PrivacyGuard();

    private static volatile long highwayBuilderDisabledAtMs;
    private static volatile boolean subscribed;

    private PrivacyGuard() {}

    public static void init() {
        if (subscribed) return;
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
        subscribed = true;
    }

    public static void onHighwayBuilderDeactivated() {
        highwayBuilderDisabledAtMs = System.currentTimeMillis();
    }

    public static boolean isPrivacyWindowOpen() {
        if (isHighwayBuilderActive()) return true;
        long disabledAt = highwayBuilderDisabledAtMs;
        return disabledAt > 0L && System.currentTimeMillis() - disabledAt < GRACE_MS;
    }

    /**
     * Local chat processing for PvP, PvE, Homes, HUD, and module logic. Always allowed.
     * Forwarding that chat off-device still requires {@link #allowsRemoteExport()}.
     */
    public static boolean allowsChatAccess() {
        return true;
    }

    /**
     * Chat forwarding, webhooks, API stats, screenshots, KitBot summons, and coordinate
     * export. Requires the Highway Builder window and a live official-highway position
     * so a stash in the overworld, end, or off the nether spawn axes cannot leak.
     */
    public static boolean allowsRemoteExport() {
        if (!isPrivacyWindowOpen()) return false;
        return onOfficialHighwayNow();
    }

    public static boolean allowsCoordinateExport() {
        return allowsRemoteExport();
    }

    public static boolean isHighwayBuilderActive() {
        try {
            Modules modules = Modules.get();
            if (modules == null) return false;
            HighwayBuilderTHM builder = modules.get(HighwayBuilderTHM.class);
            return builder != null && builder.isActive();
        } catch (Throwable t) {
            return false;
        }
    }

    /** True if {@code text} contains the local cracked-login password. Never log that value. */
    public static boolean containsCrackedPassword(String text) {
        if (text == null || text.isEmpty()) return false;
        try {
            String pw = THMSystem.get().getCrackedPassword();
            return pw != null && pw.length() >= 3 && text.contains(pw);
        } catch (Throwable t) {
            return false;
        }
    }

    /** True if {@code text} contains the local API token. Never put that on Discord/webhooks. */
    public static boolean containsApiToken(String text) {
        if (text == null || text.isEmpty()) return false;
        try {
            String token = THMSystem.get().getApiToken();
            return token != null && token.length() >= 8 && text.contains(token);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean containsSecrets(String text) {
        return containsCrackedPassword(text) || containsApiToken(text);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        highwayBuilderDisabledAtMs = 0L;
    }

    private static boolean onOfficialHighwayNow() {
        try {
            if (MeteorClient.mc == null) return false;
            return THMUtils.isOnOfficialHighway();
        } catch (Throwable t) {
            return false;
        }
    }
}
