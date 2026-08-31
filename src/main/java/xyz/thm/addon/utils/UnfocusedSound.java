/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

/**
 * Runtime master-gain for {@code unfocused-fps}. The Minecraft master slider stays
 * untouched; this is only the extra SoundEngine multiplier applied while the window
 * is in the background.
 *
 * <p>Never raises volume. A muted slider ({@code 0}) stays muted even if the unfocused
 * cap is 50% — writing the cap into {@code options.txt} is what made Mac background
 * sessions unmute themselves.
 */
public final class UnfocusedSound {
    private UnfocusedSound() {}

    /**
     * SoundEngine multiplies instance volume by the options slider and by this gain.
     * {@code 1} leaves the slider as-is; {@code 0} silences; values in between cap
     * output at {@code cap01} without going louder than the slider.
     */
    public static float masterGain(float userMasterVolume, float cap01) {
        if (!(userMasterVolume > 0f) || !(cap01 > 0f)) return 0f;
        if (cap01 >= 1f || cap01 >= userMasterVolume) return 1f;
        return cap01 / userMasterVolume;
    }
}
