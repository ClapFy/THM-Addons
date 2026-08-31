/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnfocusedSoundTest {
    private static final float EPS = 1.0e-5f;

    @Test
    void mutedSliderStaysMutedEvenWhenUnfocusedCapIsHalf() {
        assertEquals(0f, UnfocusedSound.masterGain(0f, 0.5f), EPS);
        assertEquals(0f, UnfocusedSound.masterGain(0f, 1f), EPS);
        assertEquals(0f, UnfocusedSound.masterGain(0f, 0f), EPS);
    }

    @Test
    void doesNotRaiseVolumeWhenSliderIsAlreadyAtOrBelowTheCap() {
        assertEquals(1f, UnfocusedSound.masterGain(0.2f, 0.5f), EPS);
        assertEquals(1f, UnfocusedSound.masterGain(0.5f, 0.5f), EPS);
        assertEquals(1f, UnfocusedSound.masterGain(1f, 1f), EPS);
    }

    @Test
    void capsALoudSliderWithoutRewritingIt() {
        assertEquals(0.5f, UnfocusedSound.masterGain(1f, 0.5f), EPS);
        assertEquals(0.25f, UnfocusedSound.masterGain(0.8f, 0.2f), EPS);
    }

    @Test
    void zeroCapMutesWhileUnfocused() {
        assertEquals(0f, UnfocusedSound.masterGain(1f, 0f), EPS);
        assertEquals(0f, UnfocusedSound.masterGain(0.3f, 0f), EPS);
    }
}
