/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.compat;

import meteordevelopment.meteorclient.mixin.LevelRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.BlockDestructionProgress;

import java.util.Collection;
import java.util.List;

/** Minecraft 26.1.x exposes breaking progress through Meteor's {@link LevelRendererAccessor}. */
public final class BreakingBlocks {
    private BreakingBlocks() {}

    public static Collection<BlockDestructionProgress> all(Minecraft mc) {
        if (mc == null || mc.levelRenderer == null) return List.of();
        return ((LevelRendererAccessor) mc.levelRenderer).meteor$getDestroyingBlocks().values();
    }
}
