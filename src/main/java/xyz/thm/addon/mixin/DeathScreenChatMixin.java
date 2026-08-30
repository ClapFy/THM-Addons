/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.gui.DeathChatScreen;
import xyz.thm.addon.compat.ClientGui;

/**
 * Lets the chat/command key open chat on the death screen — it is client-side only, the server
 * accepts chat from a dead player. DeathScreen doesn't override keyPressed, so this targets
 * Screen's and filters on the instance.
 */
@Mixin(Screen.class)
public class DeathScreenChatMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void thm$chatOnDeathScreen(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof DeathScreen)) return;

        Minecraft mc = Minecraft.getInstance();
        boolean command = mc.options.keyCommand.matches(input);
        if (!command && !mc.options.keyChat.matches(input)) return;

        ClientGui.setScreen(mc, new DeathChatScreen((Screen) (Object) this, command ? "/" : ""));
        cir.setReturnValue(true);
    }
}
