/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.BlockDestructionProgress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;

/** Minecraft 26.2 stores breaking progress on the client level. */
public final class BreakingBlocks {
    private BreakingBlocks() {}

    public static Collection<BlockDestructionProgress> all(Minecraft mc) {
        if (mc == null || mc.level == null) return List.of();
        List<BlockDestructionProgress> out = new ArrayList<>();
        for (SortedSet<BlockDestructionProgress> progresses : mc.level.destructionProgress().values()) {
            if (progresses == null || progresses.isEmpty()) continue;
            out.add(progresses.last());
        }
        return out;
    }
}
