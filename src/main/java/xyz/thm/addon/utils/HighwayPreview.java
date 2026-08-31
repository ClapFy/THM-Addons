/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

/**
 * Pure helpers for Highway Builder placement previews. Kept free of Minecraft types so the
 * overlay rules can be unit-tested without booting the client.
 *
 * <p>Face-exclusion bits match Meteor's {@code Dir} flags (UP=2, DOWN=4, NORTH=8, SOUTH=16,
 * WEST=32, EAST=64) without calling {@code Dir.get}, whose enum switch throws if a Direction
 * ordinal ever fails to map.
 */
public final class HighwayPreview {
    public static final int FACE_UP = 2;
    public static final int FACE_DOWN = 4;
    public static final int FACE_NORTH = 8;
    public static final int FACE_SOUTH = 16;
    public static final int FACE_WEST = 32;
    public static final int FACE_EAST = 64;

    private HighwayPreview() {}

    /**
     * Whether a highway template cell should be drawn as a future placement.
     * Liquids highlight only when the cell actually holds fluid. Solid placements highlight any
     * air or otherwise replaceable block — entity collision is ignored so the overlay still shows
     * where the builder will put blocks.
     */
    public static boolean shouldHighlightPlace(boolean liquids, boolean airOrReplaceable, boolean hasFluid) {
        return liquids ? hasFluid : airOrReplaceable;
    }

    /** Meteor {@code Dir} bit for the face whose outward step is {@code (dx, dy, dz)}. */
    public static int excludeFace(int dx, int dy, int dz) {
        if (dy > 0) return FACE_UP;
        if (dy < 0) return FACE_DOWN;
        if (dz < 0) return FACE_NORTH;
        if (dz > 0) return FACE_SOUTH;
        if (dx < 0) return FACE_WEST;
        if (dx > 0) return FACE_EAST;
        return 0;
    }
}
