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

class HighwayEchestFarmTest {
    @Test
    void fillCapacityUsesEveryEmptySlotExceptFollowUpReserves() {
        assertEquals(5 * 64, HighwayEchestFarm.fillCapacity(5, 0, 0, 0));
        assertEquals(10 + 4 * 64, HighwayEchestFarm.fillCapacity(5, 10, 1, 0));
        assertEquals(20, HighwayEchestFarm.fillCapacity(0, 20, 0, 0));
        assertEquals(64, HighwayEchestFarm.fillCapacity(1, 0, 0, 0));
        assertEquals(0, HighwayEchestFarm.fillCapacity(1, 0, 1, 0));
    }

    @Test
    void extraEchestSlotsCountAsFillableBecauseMiningFreesThem() {
        assertEquals(64, HighwayEchestFarm.fillCapacity(0, 0, 0, 1));
        assertEquals(3 * 64, HighwayEchestFarm.fillCapacity(1, 0, 0, 2));
    }

    @Test
    void incompleteObsidianStacksAbsorbADropWithoutSpendingAnEmptySlot() {
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(0, 8, 4, false, 0));
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(0, 64, 4, false, 0));
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(1, 7, 4, false, 0));
    }

    @Test
    void lastEmptySlotIsThePickupBufferAndMayBeFilled() {
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(1, 0, 4, false, 0));
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(1, 5, 4, false, 0));
    }

    @Test
    void stopsWhenTheDropWouldStealAFollowUpRestockSlot() {
        assertFalse(HighwayEchestFarm.shouldMineAnotherEchest(1, 0, 4, false, 1));
        assertFalse(HighwayEchestFarm.shouldMineAnotherEchest(2, 0, 4, false, 2));
        assertFalse(HighwayEchestFarm.shouldMineAnotherEchest(0, 3, 4, false, 0));
        assertFalse(HighwayEchestFarm.shouldMineAnotherEchest(4, 3, 0, false, 0));
    }

    @Test
    void minesWhenAnEmptySlotOrAFreedExtraEchestSlotCanTakeTheDrop() {
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(2, 0, 4, false, 1));
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(0, 0, 1, true, 0));
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(0, 0, 1, true, 0));
    }

    @Test
    void convertingASingletonExtraEchestStillRespectsPendingRestockReserves() {
        assertFalse(HighwayEchestFarm.shouldMineAnotherEchest(0, 0, 1, true, 1));
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(1, 0, 1, true, 1));
    }

    @Test
    void leftoverEmptySlotsStayOnTheFillTargetAfterExtrasAreGone() {
        assertEquals(4 * 64, HighwayEchestFarm.fillCapacity(4, 0, 0, 0));
        assertEquals(64, HighwayEchestFarm.fillCapacity(1, 0, 0, 0));
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(4, 0, 8, false, 0));
    }

    @Test
    void extraEchestSlotsIgnoreTheSaveReservePackedIntoLargestStacks() {
        assertEquals(0, HighwayEchestFarm.extraEchestSlots(new int[]{4}, 4));
        assertEquals(0, HighwayEchestFarm.extraEchestSlots(new int[]{8}, 4));
        assertEquals(1, HighwayEchestFarm.extraEchestSlots(new int[]{4, 1}, 4));
        assertEquals(2, HighwayEchestFarm.extraEchestSlots(new int[]{1, 1, 1, 1, 1, 1}, 4));
        assertEquals(0, HighwayEchestFarm.extraEchestSlots(new int[]{}, 4));
    }

    @Test
    void nextMineFreesOnlyAnExtraSingletonNotAReserveStack() {
        assertFalse(HighwayEchestFarm.nextMineFreesASlot(new int[]{8}, 4));
        assertTrue(HighwayEchestFarm.nextMineFreesASlot(new int[]{4, 1}, 4));
        assertTrue(HighwayEchestFarm.nextMineFreesASlot(new int[]{1, 1, 1, 1, 1}, 4));
        assertFalse(HighwayEchestFarm.nextMineFreesASlot(new int[]{1, 1, 1, 1}, 4));
        assertFalse(HighwayEchestFarm.nextMineFreesASlot(new int[]{5}, 4));
    }
}
