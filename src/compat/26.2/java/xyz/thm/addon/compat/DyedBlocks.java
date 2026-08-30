/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.compat;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** Dyed shulker boxes live in {@code Blocks.DYED_SHULKER_BOX} on Minecraft 26.2. */
public final class DyedBlocks {
    private DyedBlocks() {}

    public static List<Block> shulkerBoxes() {
        List<Block> blocks = new ArrayList<>();
        blocks.add(Blocks.SHULKER_BOX);
        blocks.addAll(Blocks.DYED_SHULKER_BOX.asList());
        return blocks;
    }
}
