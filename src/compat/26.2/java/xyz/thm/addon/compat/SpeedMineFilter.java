/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.compat;

import meteordevelopment.meteorclient.utils.misc.ListMode;

/** Meteor 26.2 moved SpeedMine's blocks-filter enum to {@link ListMode}. */
public final class SpeedMineFilter {
    private SpeedMineFilter() {}

    public static Object blacklist() {
        return ListMode.Blacklist;
    }

    public static boolean isBlacklist(Object value) {
        return value == ListMode.Blacklist;
    }
}
