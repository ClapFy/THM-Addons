/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.system.THMSystem;

/**
 * Main-account privacy window: the addon may read chat, export coordinates, post
 * webhooks, or summon KitBot only while Highway Builder is active, and for
 * {@link #GRACE_MS} after it turns off (so disable-time stats can still send).
 *
 * <p>Remote export additionally requires standing on a main highway so a
 * stash at arbitrary X/Z cannot leak through Discord, the THM API, or KitBot.
 * There is no opt-out; this is always enforced.
 */
public final class PrivacyGuard {
    public static final long GRACE_MS = 5_000L;

    private static final PrivacyGuard INSTANCE = new PrivacyGuard();

    private static volatile long highwayBuilderDisabledAtMs;
    private static volatile boolean lastOnMainHighway;
    private static volatile boolean subscribed;

    private PrivacyGuard() {}

    public static void init() {
        if (subscribed) return;
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
        subscribed = true;
    }

    public static void onHighwayBuilderDeactivated() {
        refreshHighwayPosition();
        highwayBuilderDisabledAtMs = System.currentTimeMillis();
    }

    public static boolean isPrivacyWindowOpen() {
        if (isHighwayBuilderActive()) return true;
        long disabledAt = highwayBuilderDisabledAtMs;
        return disabledAt > 0L && System.currentTimeMillis() - disabledAt < GRACE_MS;
    }

    public static boolean allowsChatAccess() {
        return isPrivacyWindowOpen();
    }

    /**
     * Chat forwarding, webhooks, API stats, screenshots, and KitBot summons.
     * Requires the privacy window and a main-highway position so a base off
     * the spawn axes cannot leak.
     */
    public static boolean allowsRemoteExport() {
        if (!isPrivacyWindowOpen()) return false;
        return onMainHighwayNowOrLastKnown();
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
    private void onTick(TickEvent.Post event) {
        refreshHighwayPosition();
    }

    private static boolean onMainHighwayNowOrLastKnown() {
        refreshHighwayPosition();
        return lastOnMainHighway;
    }

    private static void refreshHighwayPosition() {
        try {
            if (MeteorClient.mc != null && MeteorClient.mc.player != null) {
                lastOnMainHighway = THMUtils.isOnMainHighway();
            }
        } catch (Throwable ignored) {
            // Fail closed: keep the last known highway flag.
        }
    }
}
