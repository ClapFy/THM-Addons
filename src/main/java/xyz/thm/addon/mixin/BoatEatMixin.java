/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla's {@code doItemUse} returns early while {@code ClientPlayerEntity#isRiding()} — true only
 * while steering a boat with a movement key held — so item use in a moving boat is a client-side stop
 * only; the server accepts the packets fine.
 */
@Mixin(Minecraft.class)
public class BoatEatMixin {
    @Redirect(
        method = "startUseItem",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z")
    )
    private boolean thm$allowItemUseWhileRiding(LocalPlayer player) {
        return false;
    }
}
