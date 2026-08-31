/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.waveycapes.WaveyCapesConfig;

@Mixin(CapeLayer.class)
public class WaveyCapesCapeMixin {

    // 26.x renamed CapeLayer drawing from render to submit (extractor/submit pipeline).
    // Targeting the old name fails mixin apply during avatar reload and leaves a black window.
    @Inject(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void thm$skipWhenWavyEnabled(CallbackInfo ci) {
        if (WaveyCapesConfig.enabled) ci.cancel();
    }
}
