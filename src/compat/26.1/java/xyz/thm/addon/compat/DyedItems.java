/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.compat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Per-color item constants on Minecraft 26.1.x. */
public final class DyedItems {
    private DyedItems() {}

    public static Item greenConcrete() {
        return Items.GREEN_CONCRETE;
    }

    public static Item redConcrete() {
        return Items.RED_CONCRETE;
    }
}
