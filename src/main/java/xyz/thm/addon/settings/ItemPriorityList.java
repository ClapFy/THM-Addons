/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.settings.GenericSetting;
import meteordevelopment.meteorclient.settings.IGeneric;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

// Like StringMultiSelect, but the selection order IS the value — index 0 is highest priority.
// Its screen shows the selected column in list order with Up/Down buttons instead of sorting it
// alphabetically, so the user can rank items instead of just picking them.
public class ItemPriorityList implements IGeneric<ItemPriorityList> {
    private final List<Item> selected = new ArrayList<>();

    /** Live, mutable backing list — index 0 is highest priority. The settings screen edits this directly. */
    public List<Item> selected() {
        return selected;
    }

    @Override
    public WidgetScreen createScreen(GuiTheme theme, GenericSetting<ItemPriorityList> setting) {
        return new ItemPriorityListScreen(theme, this, setting);
    }

    @Override
    public ItemPriorityList set(ItemPriorityList value) {
        selected.clear();
        selected.addAll(value.selected);
        return this;
    }

    @Override
    public ItemPriorityList copy() {
        ItemPriorityList copy = new ItemPriorityList();
        copy.selected.addAll(selected);
        return copy;
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        NbtList list = new NbtList();
        for (Item item : selected) list.add(NbtString.of(Registries.ITEM.getId(item).toString()));
        tag.put("selected", list);
        return tag;
    }

    @Override
    public ItemPriorityList fromTag(NbtCompound tag) {
        selected.clear();
        tag.getListOrEmpty("selected").forEach(el -> {
            Item item = Registries.ITEM.get(Identifier.of(el.asString().orElse("minecraft:air")));
            if (item != Items.AIR) selected.add(item);
        });
        return this;
    }
}
