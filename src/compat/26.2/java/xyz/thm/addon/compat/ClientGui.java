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

/** Minecraft 26.2 moved the current screen, chat HUD, and the main target onto {@code Gui}/{@code GameRenderer}. */
public final class ClientGui {
    private ClientGui() {}

    public static Screen screen(Minecraft mc) {
        return mc == null ? null : mc.gui.screen();
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        if (mc != null) mc.gui.setScreen(screen);
    }

    public static ChatComponent chat(Minecraft mc) {
        return mc == null || mc.gui == null || mc.gui.hud == null ? null : mc.gui.hud.getChat();
    }

    public static int guiTicks(Minecraft mc) {
        return mc == null || mc.gui == null || mc.gui.hud == null ? 0 : mc.gui.hud.getGuiTicks();
    }

    public static boolean hideHud(Minecraft mc) {
        return mc != null && mc.gameRenderer != null && mc.gameRenderer.gameRenderState().guiRenderState.isHudHidden;
    }

    public static RenderTarget mainRenderTarget(Minecraft mc) {
        return mc == null || mc.gameRenderer == null ? null : mc.gameRenderer.mainRenderTarget();
    }

    public static Camera mainCamera(Minecraft mc) {
        return mc == null || mc.gameRenderer == null ? null : mc.gameRenderer.mainCamera();
    }
}
