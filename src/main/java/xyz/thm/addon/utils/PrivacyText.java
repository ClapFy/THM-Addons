/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure text/geometry helpers for main-account stash privacy. No Minecraft types so
 * Highway Builder, Discord, KitBot, and packet logs can share one scrubber, and so
 * the rules can be unit-tested without booting the client.
 *
 * <p>PvP and PvE stay local. Coordinates in outbound text are the leak.
 */
public final class PrivacyText {
    public static final double HIGHWAY_AXIS_TOLERANCE = 5.0;

    private static final String COORDINATE_NUMBER = "[+-]?\\d+(?:\\.\\d+)?";
    private static final Pattern LABELED_COORDINATES = Pattern.compile(
        "(?i)(?:blockpos\\s*\\{\\s*)?x\\s*[=:]\\s*" + COORDINATE_NUMBER
            + "\\s*[,; ]+\\s*y\\s*[=:]\\s*" + COORDINATE_NUMBER
            + "\\s*[,; ]+\\s*z\\s*[=:]\\s*" + COORDINATE_NUMBER + "\\s*\\}?"
    );
    private static final Pattern DELIMITED_COORDINATE_TRIPLE = Pattern.compile(
        "(?<![A-Za-z0-9_.])" + COORDINATE_NUMBER
            + "\\s*[,/]\\s*" + COORDINATE_NUMBER
            + "\\s*[,/]\\s*" + COORDINATE_NUMBER
            + "(?![A-Za-z0-9_.])"
    );
    private static final Pattern BRACKETED_COORDINATE_TRIPLE = Pattern.compile(
        "[\\[(]\\s*" + COORDINATE_NUMBER
            + "\\s+" + COORDINATE_NUMBER
            + "\\s+" + COORDINATE_NUMBER + "\\s*[\\])]"
    );
    private static final Pattern SPACED_COORDINATE_TRIPLE = Pattern.compile(
        "(?<![A-Za-z0-9_.])" + COORDINATE_NUMBER
            + "\\s+" + COORDINATE_NUMBER
            + "\\s+" + COORDINATE_NUMBER
            + "(?![A-Za-z0-9_.])"
    );

    private PrivacyText() {}

    /**
     * Spawn-axis pavement used by 6b6t / 2b2t-style nether highways: X, Z, or diagonal,
     * within {@link #HIGHWAY_AXIS_TOLERANCE} blocks.
     */
    public static boolean isMainHighwayPosition(double x, double z) {
        boolean onXAxis = Math.abs(z) < HIGHWAY_AXIS_TOLERANCE;
        boolean onZAxis = Math.abs(x) < HIGHWAY_AXIS_TOLERANCE;
        boolean onDiagonal = Math.abs(Math.abs(x) - Math.abs(z)) < HIGHWAY_AXIS_TOLERANCE;
        return onXAxis || onZAxis || onDiagonal;
    }

    /**
     * Remote export is only legal on the public nether highway of 6b6t, never in the
     * overworld/end and never off the spawn axes (typical stash).
     */
    public static boolean isOfficialHighwayPosition(boolean sixb6t, boolean nether, double x, double z) {
        return sixb6t && nether && isMainHighwayPosition(x, z);
    }

    public static boolean containsCoordinates(String text) {
        if (text == null || text.isEmpty()) return false;
        return LABELED_COORDINATES.matcher(text).find()
            || DELIMITED_COORDINATE_TRIPLE.matcher(text).find()
            || BRACKETED_COORDINATE_TRIPLE.matcher(text).find()
            || SPACED_COORDINATE_TRIPLE.matcher(text).find();
    }

    public static String scrubCoordinates(String text) {
        if (text == null || text.isEmpty()) return text;
        String scrubbed = LABELED_COORDINATES.matcher(text).replaceAll("[coordinates]");
        scrubbed = DELIMITED_COORDINATE_TRIPLE.matcher(scrubbed).replaceAll("[coordinates]");
        scrubbed = BRACKETED_COORDINATE_TRIPLE.matcher(scrubbed).replaceAll("[coordinates]");
        return SPACED_COORDINATE_TRIPLE.matcher(scrubbed).replaceAll("[coordinates]");
    }

    /** Starscript / Discord RPC templates that would evaluate to a world position. */
    public static boolean templateCanLeakCoordinates(String template) {
        if (template == null || template.isEmpty()) return false;
        String lower = template.toLowerCase(Locale.ROOT);
        return lower.contains("pos")
            || lower.contains("coord")
            || lower.contains("{x}")
            || lower.contains("{y}")
            || lower.contains("{z}")
            || lower.contains("player.x")
            || lower.contains("player.y")
            || lower.contains("player.z");
    }
}
