/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;

/** Minecraft 26.1.x still keeps screen, chat, and the main target on {@link Minecraft}/{@code Gui}. */
public final class ClientGui {
    private ClientGui() {}

    public static Screen screen(Minecraft mc) {
        return mc == null ? null : mc.screen;
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        if (mc != null) mc.setScreen(screen);
    }

    public static ChatComponent chat(Minecraft mc) {
        return mc == null || mc.gui == null ? null : mc.gui.getChat();
    }

    public static int guiTicks(Minecraft mc) {
        return mc == null || mc.gui == null ? 0 : mc.gui.getGuiTicks();
    }

    public static boolean hideHud(Minecraft mc) {
        return mc != null && mc.options.hideGui;
    }

    public static RenderTarget mainRenderTarget(Minecraft mc) {
        return mc == null ? null : mc.getMainRenderTarget();
    }

    public static Camera mainCamera(Minecraft mc) {
        return mc == null || mc.gameRenderer == null ? null : mc.gameRenderer.getMainCamera();
    }
}
