/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.compat;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** World-space 2D text for Minecraft 26.1.x ({@link MultiBufferSource} still exists). */
public final class ClientText {
    private static final MultiBufferSource.BufferSource VERTEX =
        MultiBufferSource.immediate(new ByteBufferBuilder(2048));

    private ClientText() {}

    public static void draw(String text, PoseStack stack, float x, float y, int color) {
        if (mc == null) return;
        mc.font.drawInBatch(
            text, x, y, color, false,
            stack.last().pose(), VERTEX,
            Font.DisplayMode.NORMAL, 0, 15728880
        );
        VERTEX.endBatch();
    }
}
