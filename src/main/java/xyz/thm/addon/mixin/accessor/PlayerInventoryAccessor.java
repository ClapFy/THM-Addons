/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.accessor;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(PlayerInventory.class)
public interface PlayerInventoryAccessor {
    @Accessor("selectedSlot")
    int getSelectedSlot();
    @Accessor("selectedSlot")
    void setSelectedSlot(int slot);
    @Accessor("main")
    DefaultedList<ItemStack> getMain();
}
