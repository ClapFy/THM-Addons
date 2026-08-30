/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.compat;

import meteordevelopment.meteorclient.systems.modules.player.SpeedMine;

/** Meteor 26.1.2 keeps the blocks-filter enum nested on {@link SpeedMine}. */
public final class SpeedMineFilter {
    private SpeedMineFilter() {}

    public static Object blacklist() {
        return SpeedMine.ListMode.Blacklist;
    }

    public static boolean isBlacklist(Object value) {
        return value == SpeedMine.ListMode.Blacklist;
    }
}
