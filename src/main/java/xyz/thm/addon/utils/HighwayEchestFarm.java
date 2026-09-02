/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import java.util.Arrays;

/**
 * How many ender chests Highway Builder should mine so inventory ends up as full of
 * obsidian as it can. {@code minimum-empty-slots} is a pickup buffer: the 8-obsidian
 * drop is allowed to fill it. Only empty slots reserved for a follow-up pickaxe/food
 * restock in the same sequence stay empty. No Minecraft types so the rules can be
 * unit-tested.
 */
public final class HighwayEchestFarm {
    public static final int OBSIDIAN_PER_ECHEST = 8;
    public static final int OBSIDIAN_PER_STACK = 64;

    private HighwayEchestFarm() {}

    /**
     * Extra obsidian items the main inventory can still hold after topping off
     * incomplete stacks. Extra ender-chest slots count as fillable because mining
     * them frees the slot for the drop.
     */
    public static int fillCapacity(int emptySlots, int topOffItems, int reservedEmptySlots, int extraEchestSlots) {
        int keepEmpty = Math.max(reservedEmptySlots, 0);
        int fillableSlots = Math.max(Math.max(emptySlots, 0) + Math.max(extraEchestSlots, 0) - keepEmpty, 0);
        return Math.max(topOffItems, 0) + fillableSlots * OBSIDIAN_PER_STACK;
    }

    /**
     * Whether mining one more ender chest can be picked up. The drop may fill the
     * last empty slot (the pickup buffer). After pickup, leftover empty slots must
     * still cover {@code reservedEmptySlots}.
     *
     * <p>{@code nextMineFreesASlot} is true when that chest is an extra singleton,
     * so consuming it creates an empty slot before the 8 obsidian land.
     */
    public static boolean shouldMineAnotherEchest(
        int emptySlots,
        int topOffItems,
        int usableEchests,
        boolean nextMineFreesASlot,
        int reservedEmptySlots
    ) {
        if (usableEchests <= 0) return false;
        int topOff = Math.max(topOffItems, 0);
        int reserved = Math.max(reservedEmptySlots, 0);
        int emptyAfterConsume = Math.max(emptySlots, 0) + (nextMineFreesASlot ? 1 : 0);
        boolean needsNewSlot = topOff < OBSIDIAN_PER_ECHEST;
        if (needsNewSlot && emptyAfterConsume < 1) return false;
        int emptyAfterPickup = needsNewSlot ? emptyAfterConsume - 1 : emptyAfterConsume;
        return emptyAfterPickup >= reserved;
    }

    /**
     * Inventory slots that hold only extra ender chests after the save-reserve
     * items are packed into the largest stacks. Leftover extras that share a
     * slot with the reserve do not count: mining them leaves the reserve behind.
     */
    public static int extraEchestSlots(int[] stackCounts, int saveReserve) {
        int extra = 0;
        for (int count : extraStackCounts(stackCounts, saveReserve)) {
            if (count > 0) extra++;
        }
        return extra;
    }

    /**
     * True when at least one extra ender chest (above {@code saveReserve}) sits
     * alone in its own slot, so mining it frees that slot. A leftover extra in
     * the reserve stack does not free a slot.
     */
    public static boolean nextMineFreesASlot(int[] stackCounts, int saveReserve) {
        for (int count : extraStackCounts(stackCounts, saveReserve)) {
            if (count == 1) return true;
        }
        return false;
    }

    static int[] extraStackCounts(int[] stackCounts, int saveReserve) {
        if (stackCounts == null || stackCounts.length == 0) return new int[0];
        int[] counts = Arrays.copyOf(stackCounts, stackCounts.length);
        Arrays.sort(counts);
        int reserve = Math.max(saveReserve, 0);
        for (int i = counts.length - 1; i >= 0 && reserve > 0; i--) {
            if (counts[i] <= 0) continue;
            int take = Math.min(counts[i], reserve);
            counts[i] -= take;
            reserve -= take;
            if (take > 0) {
                // Reserve occupied this slot. Leftover extras here do not free it.
                counts[i] = 0;
            }
        }
        return counts;
    }
}
