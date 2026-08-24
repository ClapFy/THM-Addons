/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.gui.DeathChatScreen;

/**
 * Lets the chat/command key open chat on the death screen — it is client-side only, the server
 * accepts chat from a dead player. DeathScreen doesn't override keyPressed, so this targets
 * Screen's and filters on the instance.
 */
@Mixin(Screen.class)
public class DeathScreenChatMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void thm$chatOnDeathScreen(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof DeathScreen)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean command = mc.options.commandKey.matchesKey(input);
        if (!command && !mc.options.chatKey.matchesKey(input)) return;

        mc.setScreen(new DeathChatScreen((Screen) (Object) this, command ? "/" : ""));
        cir.setReturnValue(true);
    }
}
