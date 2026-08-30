/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.renderer.Fonts;
import meteordevelopment.meteorclient.renderer.text.CustomTextRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * World-space 2D text for Minecraft 26.2. {@code Font.drawInBatch} and
 * {@code MultiBufferSource} were removed, so this uses Meteor's custom-font atlas
 * with the same PoseStack transform the 26.1 helper applied.
 */
public final class ClientText {
    private ClientText() {}

    public static void draw(String text, PoseStack stack, float x, float y, int color) {
        if (mc == null || text == null || stack == null) return;
        CustomTextRenderer renderer = Fonts.RENDERER;
        if (renderer == null || renderer.isBuilding()) return;

        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.mul(stack.last().pose());
            renderer.begin((GuiGraphicsExtractor) null, 1.0, false, false);
            try {
                renderer.render(text, x, y, new Color(color), false);
            } finally {
                renderer.end();
            }
        } finally {
            modelView.popMatrix();
        }
    }
}
