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
    void fillCapacityUsesEmptySlotsAfterMinEmptyAndReserves() {
        assertEquals(4 * 64, HighwayEchestFarm.fillCapacity(5, 1, 0, 0));
        assertEquals(10 + 3 * 64, HighwayEchestFarm.fillCapacity(5, 1, 10, 1));
        assertEquals(20, HighwayEchestFarm.fillCapacity(0, 0, 20, 0));
        assertEquals(0, HighwayEchestFarm.fillCapacity(1, 1, 0, 0));
    }

    @Test
    void incompleteObsidianStacksAbsorbADropWithoutSpendingTheLastEmptySlot() {
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(1, 1, 8, 4, false, 0));
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(1, 1, 64, 4, false, 0));
    }

    @Test
    void stopsWhenTheOnlyEmptySlotsMustStayEmpty() {
        assertFalse(HighwayEchestFarm.shouldMineAnotherEchest(1, 1, 0, 4, false, 0));
        assertFalse(HighwayEchestFarm.shouldMineAnotherEchest(2, 1, 0, 4, false, 1));
        assertFalse(HighwayEchestFarm.shouldMineAnotherEchest(4, 1, 3, 0, false, 0));
    }

    @Test
    void minesWhenAnEmptySlotOrAFreedEchestSlotCanTakeTheDrop() {
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(2, 1, 0, 4, false, 0));
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(1, 1, 0, 1, true, 0));
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(0, 0, 0, 1, true, 0));
    }

    @Test
    void convertingASingletonExtraEchestStillRespectsPendingRestockReserves() {
        assertFalse(HighwayEchestFarm.shouldMineAnotherEchest(1, 1, 0, 1, true, 1));
        assertTrue(HighwayEchestFarm.shouldMineAnotherEchest(2, 1, 0, 1, true, 1));
    }
}
