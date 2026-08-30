/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.compat;

import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Meteor 26.1.2 {@link TextRenderer} does not take a {@link GuiGraphicsExtractor}. */
public final class ClientFonts {
    private ClientFonts() {}

    public static void begin(TextRenderer renderer, GuiGraphicsExtractor graphics, double scale, boolean scaleOnly, boolean big) {
        renderer.begin(scale, scaleOnly, big);
    }

    public static void beginBig(TextRenderer renderer, GuiGraphicsExtractor graphics) {
        renderer.beginBig();
    }
}
