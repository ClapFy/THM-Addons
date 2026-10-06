/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighwayEchestShulkerRestockTest {
    @Test
    void fullShulkerRequiresTwentySevenEnderChestSlots() {
        assertTrue(HighwayEchestShulkerRestock.isFullEchestShulker(27, 27));
        assertTrue(HighwayEchestShulkerRestock.isFullEchestShulker(28, 28));
    }

    @Test
    void partialOrMixedContentsAreNotFullEchestShulkers() {
        assertFalse(HighwayEchestShulkerRestock.isFullEchestShulker(26, 26));
        assertFalse(HighwayEchestShulkerRestock.isFullEchestShulker(27, 26));
        assertFalse(HighwayEchestShulkerRestock.isFullEchestShulker(27, 0));
        assertFalse(HighwayEchestShulkerRestock.isFullEchestShulker(0, 0));
        assertFalse(HighwayEchestShulkerRestock.isFullEchestShulker(27, 13));
    }

    @Test
    void namedButNonEchestContentsDoNotCountBecauseOnlySlotItemsAreInspected() {
        // occupied=27 netherrack slots, 0 ender chests — a shulker named "echest" would still fail
        assertFalse(HighwayEchestShulkerRestock.isFullEchestShulker(27, 0));
    }

    @Test
    void triggersBelowTwoFullShulkersWhenRoomExists() {
        assertTrue(HighwayEchestShulkerRestock.shouldTrigger(0, false, true));
        assertTrue(HighwayEchestShulkerRestock.shouldTrigger(1, false, true));
        assertFalse(HighwayEchestShulkerRestock.shouldTrigger(2, false, true));
        assertFalse(HighwayEchestShulkerRestock.shouldTrigger(3, false, true));
    }

    @Test
    void doesNotTriggerWhenExhaustedOrUnableToReceive() {
        assertFalse(HighwayEchestShulkerRestock.shouldTrigger(0, true, true));
        assertFalse(HighwayEchestShulkerRestock.shouldTrigger(1, false, false));
        assertFalse(HighwayEchestShulkerRestock.shouldTrigger(0, true, false));
    }

    @Test
    void remembersExhaustedOnlyWhenTheEnderChestHasNoneLeft() {
        assertTrue(HighwayEchestShulkerRestock.shouldRememberExhausted(0));
        assertFalse(HighwayEchestShulkerRestock.shouldRememberExhausted(1));
        assertFalse(HighwayEchestShulkerRestock.shouldRememberExhausted(3));
    }

    @Test
    void trashKeepBudgetPreservesNormalReservePlusThreeSwapStacks() {
        assertEquals(4, HighwayEchestShulkerRestock.trashKeepBudget(1));
        assertEquals(3, HighwayEchestShulkerRestock.trashKeepBudget(0));
        assertEquals(5, HighwayEchestShulkerRestock.trashKeepBudget(2));
    }

    @Test
    void remainingPullCountStopsAtThree() {
        assertEquals(3, HighwayEchestShulkerRestock.remainingPullCount(0));
        assertEquals(1, HighwayEchestShulkerRestock.remainingPullCount(2));
        assertEquals(0, HighwayEchestShulkerRestock.remainingPullCount(3));
        assertEquals(0, HighwayEchestShulkerRestock.remainingPullCount(5));
    }
}
