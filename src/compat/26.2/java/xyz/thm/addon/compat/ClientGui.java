/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Minecraft 26.2 moved the current screen onto {@code Minecraft.gui}. */
public final class ClientGui {
    private ClientGui() {}

    public static Screen screen(Minecraft mc) {
        return mc == null ? null : mc.gui.screen();
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        if (mc != null) mc.gui.setScreen(screen);
    }
}
