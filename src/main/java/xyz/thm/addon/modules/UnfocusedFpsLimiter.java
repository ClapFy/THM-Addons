/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.UnfocusedSound;

import java.util.Objects;

public class UnfocusedFpsLimiter extends Module {
    private static final float RESTORED_MASTER_GAIN = 1.0f;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> unfocusedFps = sgGeneral.add(new IntSetting.Builder()
        .name("unfocused-fps")
        .description("The FPS limit when the game window is not focused.")
        .defaultValue(30)
        .min(1)
        .max(260)
        .sliderRange(1, 260)
        .build()
    );

    private final Setting<Boolean> limitSound = sgGeneral.add(new BoolSetting.Builder()
        .name("limit-sound")
        .description("Lowers in-game sound while the window is in the background. Never unmutes a master slider you set to 0.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> soundVolume = sgGeneral.add(new IntSetting.Builder()
        .name("sound-volume")
        .description("Cap on master volume while unfocused (0-100%). Cannot go above your Minecraft master slider.")
        .defaultValue(50)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .visible(() -> limitSound.get())
        .build()
    );

    private int originalFps;
    private boolean unfocusedGainApplied;

    public UnfocusedFpsLimiter() {
        super(THMAddon.MAIN, "unfocused-fps", "Limits the FPS and optionally sound when the game is unfocused or not the main task.");
    }

    @Override
    public void onActivate() {
        Minecraft mc = Minecraft.getInstance();
        originalFps = mc.options.framerateLimit().get();
        unfocusedGainApplied = false;
    }

    @Override
    public void onDeactivate() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.framerateLimit().set(originalFps);
        restoreMasterGain(mc);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.isWindowActive()) {
            if (mc.options.framerateLimit().get() != originalFps) {
                mc.options.framerateLimit().set(originalFps);
            }
            restoreMasterGain(mc);
        } else {
            if (!Objects.equals(mc.options.framerateLimit().get(), unfocusedFps.get())) {
                mc.options.framerateLimit().set(unfocusedFps.get());
            }
            applyUnfocusedMasterGain(mc);
        }
    }

    private void applyUnfocusedMasterGain(Minecraft mc) {
        if (!limitSound.get()) {
            restoreMasterGain(mc);
            return;
        }
        SoundManager sounds = mc.getSoundManager();
        if (sounds == null) return;
        float userVolume = mc.options.getSoundSourceVolume(SoundSource.MASTER);
        float cap = soundVolume.get() / 100.0f;
        // Re-apply every tick: macOS can recreate the OpenAL device in the background and
        // drop SoundEngine's in-memory gain map. Never write the cap into options.
        sounds.updateCategoryVolume(SoundSource.MASTER, UnfocusedSound.masterGain(userVolume, cap));
        unfocusedGainApplied = true;
    }

    private void restoreMasterGain(Minecraft mc) {
        if (!unfocusedGainApplied) return;
        SoundManager sounds = mc.getSoundManager();
        if (sounds != null) {
            sounds.updateCategoryVolume(SoundSource.MASTER, RESTORED_MASTER_GAIN);
        }
        unfocusedGainApplied = false;
    }
}
