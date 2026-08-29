/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.hud;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.TotemTracker;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TotemPopHud extends HudElement {
    public static final HudElementInfo<TotemPopHud> INFO = new HudElementInfo<>(
        THMAddon.HUD_GROUP, "totem-pop-hud", "Shows how many totems of undying you've popped, like an item count.", TotemPopHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the totem icon.")
        .defaultValue(2.0)
        .min(0.5)
        .sliderRange(0.5, 4)
        .onChanged(v -> calculateSize())
        .build()
    );

    public TotemPopHud() {
        super(INFO);
        calculateSize();
    }

    private void calculateSize() {
        setSize(17 * scale.get(), 17 * scale.get());
    }

    @Override
    public void render(HudRenderer renderer) {
        ItemStack stack = new ItemStack(Items.TOTEM_OF_UNDYING, 1);
        int pops = mc.player == null ? 0 : TotemTracker.get(mc.player);

        renderer.post(() -> renderer.item(stack, x, y, scale.get().floatValue(), true, String.valueOf(pops)));
    }
}
