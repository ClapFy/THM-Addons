/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedButton;
import net.minecraft.item.ItemStack;
import xyz.thm.addon.utils.Homes;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** The Meteor-themed variant of {@link HomesScreen}, one row per home instead of a slot grid. */
public class HomesMeteorScreen extends WindowScreen {
    private final Homes module;

    public HomesMeteorScreen(GuiTheme theme, Homes module) {
        super(theme, "Homes");
        this.module = module;
    }

    @Override
    public void initWidgets() {
        if (module.homes().isEmpty()) {
            add(theme.label("No homes captured - run /homes."));
            return;
        }

        WTable table = add(theme.table()).expandX().widget();

        for (String home : module.homes()) {
            table.add(theme.item(module.icon(home)));
            table.add(theme.label(home)).expandCellX();

            WButton icon = table.add(theme.button("Icon")).widget();
            icon.tooltip = "Use your held item as this home's icon.";
            icon.action = () -> {
                ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandStack();
                module.setIcon(home, held.isEmpty() ? null : held.getItem());
                reload();
            };

            WButton teleport = table.add(theme.button("Teleport")).widget();
            teleport.action = () -> module.teleport(home);

            WConfirmedButton delete = table.add(theme.confirmedButton("Delete", "Are you sure?")).widget();
            delete.action = () -> {
                module.delete(home);
                reload();
            };

            table.row();
        }
    }
}
