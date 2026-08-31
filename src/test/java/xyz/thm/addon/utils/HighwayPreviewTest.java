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

class HighwayPreviewTest {
    @Test
    void solidPreviewShowsAirAndReplaceableCells() {
        assertTrue(HighwayPreview.shouldHighlightPlace(false, true, false));
        assertFalse(HighwayPreview.shouldHighlightPlace(false, false, false));
        assertFalse(HighwayPreview.shouldHighlightPlace(false, false, true));
    }

    @Test
    void liquidPreviewRequiresFluidEvenIfTheCellIsReplaceable() {
        assertTrue(HighwayPreview.shouldHighlightPlace(true, false, true));
        assertTrue(HighwayPreview.shouldHighlightPlace(true, true, true));
        assertFalse(HighwayPreview.shouldHighlightPlace(true, true, false));
        assertFalse(HighwayPreview.shouldHighlightPlace(true, false, false));
    }

    @Test
    void excludeFaceMatchesMeteorDirBits() {
        assertEquals(HighwayPreview.FACE_UP, HighwayPreview.excludeFace(0, 1, 0));
        assertEquals(HighwayPreview.FACE_DOWN, HighwayPreview.excludeFace(0, -1, 0));
        assertEquals(HighwayPreview.FACE_NORTH, HighwayPreview.excludeFace(0, 0, -1));
        assertEquals(HighwayPreview.FACE_SOUTH, HighwayPreview.excludeFace(0, 0, 1));
        assertEquals(HighwayPreview.FACE_WEST, HighwayPreview.excludeFace(-1, 0, 0));
        assertEquals(HighwayPreview.FACE_EAST, HighwayPreview.excludeFace(1, 0, 0));
        assertEquals(0, HighwayPreview.excludeFace(0, 0, 0));
    }
}
