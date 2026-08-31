/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

/**
 * How many ender chests Highway Builder should mine so inventory ends up as full of
 * obsidian as it can, without eating slots that must stay empty for pickup or a
 * follow-up restock. No Minecraft types so the rules can be unit-tested.
 */
public final class HighwayEchestFarm {
    public static final int OBSIDIAN_PER_ECHEST = 8;
    public static final int OBSIDIAN_PER_STACK = 64;

    private HighwayEchestFarm() {}

    /**
     * Extra obsidian items the main inventory can still hold after topping off
     * incomplete stacks, leaving {@code minEmpty} plus any reserved empty slots.
     */
    public static int fillCapacity(int emptySlots, int minEmpty, int topOffItems, int reservedEmptySlots) {
        int keepEmpty = Math.max(minEmpty, 0) + Math.max(reservedEmptySlots, 0);
        int usableEmpty = Math.max(emptySlots - keepEmpty, 0);
        return Math.max(topOffItems, 0) + usableEmpty * OBSIDIAN_PER_STACK;
    }

    /**
     * Whether mining one more ender chest can be picked up without dropping below
     * {@code minEmpty + reservedEmptySlots} empty slots afterwards.
     *
     * <p>{@code nextMineFreesASlot} is true when that chest is the last item in its
     * inventory slot, so consuming it creates an empty slot before the 8 obsidian land.
     */
    public static boolean shouldMineAnotherEchest(
        int emptySlots,
        int minEmpty,
        int topOffItems,
        int usableEchests,
        boolean nextMineFreesASlot,
        int reservedEmptySlots
    ) {
        if (usableEchests <= 0) return false;
        int topOff = Math.max(topOffItems, 0);
        if (topOff >= OBSIDIAN_PER_ECHEST) return true;
        int keepEmpty = Math.max(minEmpty, 0) + Math.max(reservedEmptySlots, 0);
        int emptyAfterConsume = Math.max(emptySlots, 0) + (nextMineFreesASlot ? 1 : 0);
        return emptyAfterConsume - 1 >= keepEmpty;
    }
}
