/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

/**
 * Highway stats/status API rules shared with {@link APIUtils}. No Minecraft types so
 * disable-time sends can be unit-tested without booting the client.
 *
 * <p>The main repo posts {@code {"content": "..."}} with a Bearer token and does not
 * re-check live position after Highway Builder turns off. This fork keeps stash
 * coordinates, KitBot, and webhooks fail-closed; only an official nether-highway
 * session (or standing on that highway right now) may send aggregate stats.
 */
public final class HighwayApiPolicy {
    private HighwayApiPolicy() {}

    /**
     * {@code officialHighwaySession} is sticky for one Highway Builder run that was
     * earned on the official 6b6t nether highway. Live position is for an in-progress
     * status ping. Neither flag is enough to unlock Discord, KitBot, or coordinate export.
     */
    public static boolean allowsStatsExport(boolean officialHighwaySession, boolean currentlyOnOfficialHighway) {
        return officialHighwaySession || currentlyOnOfficialHighway;
    }

    /**
     * Same wire format as the main repo {@code APIUtils.jsonContent}: a JSON object
     * with a {@code content} string. Quotes and backslashes are escaped so a token or
     * player name cannot break the body.
     */
    public static String jsonContent(String message) {
        String value = message == null ? "" : message;
        StringBuilder out = new StringBuilder(value.length() + 16);
        out.append("{\"content\": \"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        out.append("\"}");
        return out.toString();
    }
}
