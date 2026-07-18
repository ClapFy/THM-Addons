/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 */

package xyz.thm.addon.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.settings.GenericSetting;
import meteordevelopment.meteorclient.settings.IGeneric;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

// Universal "pick some strings out of a dynamic list" setting: renders as a two-column
// picker with +/- buttons, same UX as Meteor's target/module list settings (e.g. KillAura).
// candidates() is re-evaluated live, so it works for any dynamically changing pool
// (available shaders, online THM members, ...) without needing a dedicated setting type.
public class StringMultiSelect implements IGeneric<StringMultiSelect> {
    private final String screenTitle;
    private final Supplier<List<String>> candidates;
    private final Set<String> selected = new LinkedHashSet<>();

    public StringMultiSelect(String screenTitle, Supplier<List<String>> candidates) {
        this.screenTitle = screenTitle;
        this.candidates = candidates;
    }

    public List<String> candidates() {
        return candidates.get();
    }

    /** Live, mutable backing set — the settings screen edits this directly. */
    public Set<String> selected() {
        return selected;
    }

    public String screenTitle() {
        return screenTitle;
    }

    @Override
    public WidgetScreen createScreen(GuiTheme theme, GenericSetting<StringMultiSelect> setting) {
        return new StringMultiSelectScreen(theme, this, setting);
    }

    @Override
    public StringMultiSelect set(StringMultiSelect value) {
        selected.clear();
        selected.addAll(value.selected);
        return this;
    }

    @Override
    public StringMultiSelect copy() {
        StringMultiSelect copy = new StringMultiSelect(screenTitle, candidates);
        copy.selected.addAll(selected);
        return copy;
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        NbtList list = new NbtList();
        for (String s : selected) list.add(NbtString.of(s));
        tag.put("selected", list);
        return tag;
    }

    @Override
    public StringMultiSelect fromTag(NbtCompound tag) {
        selected.clear();
        tag.getListOrEmpty("selected").forEach(el -> selected.add(el.asString().orElse("")));
        return this;
    }
}
