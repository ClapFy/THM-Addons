/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

/**
 * Rules for restocking full ender-chest shulkers from the player's ender chest.
 * Identification is by shulker contents only — custom names are ignored.
 * No Minecraft types so the rules can be unit-tested.
 */
public final class HighwayEchestShulkerRestock {
    public static final int SHULKER_SLOTS = 27;
    public static final int MIN_FULL_SHULKERS = 2;
    public static final int PULL_COUNT = 3;
    public static final int RETRY_COOLDOWN_TICKS = 20 * 20;

    private HighwayEchestShulkerRestock() {}

    /**
     * A full echest shulker has every slot occupied, and every occupied slot is
     * an ender chest. Custom names are not part of this check.
     */
    public static boolean isFullEchestShulker(int occupiedSlots, int enderChestSlots) {
        return occupiedSlots >= SHULKER_SLOTS
            && enderChestSlots >= SHULKER_SLOTS
            && enderChestSlots == occupiedSlots;
    }

    /**
     * Restock when inventory is below two full echest shulkers, the ender chest
     * has not already been proven empty this session, and there is trash or an
     * empty slot to receive a shulker.
     */
    public static boolean shouldTrigger(int fullShulkerCount, boolean exhaustedThisSession, boolean canReceiveShulker) {
        return !exhaustedThisSession
            && canReceiveShulker
            && fullShulkerCount < MIN_FULL_SHULKERS;
    }

    public static boolean shouldRememberExhausted(int remainingFullEchestShulkersInEnderChest) {
        return remainingFullEchestShulkersInEnderChest <= 0;
    }

    /**
     * ThrowOutTrash should keep the normal trash reserve plus enough extra
     * stacks to swap for {@link #PULL_COUNT} shulkers instead of dropping them.
     */
    public static int trashKeepBudget(int keepTrashBlockStacks) {
        return Math.max(keepTrashBlockStacks, 0) + PULL_COUNT;
    }

    public static int remainingPullCount(int alreadyPulled) {
        return Math.max(PULL_COUNT - Math.max(alreadyPulled, 0), 0);
    }
}
