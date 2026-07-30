/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.thm.addon.modules.HighwayBuilderTHM;

@Mixin(MinecraftClient.class)
public abstract class HighwayBuilderBowMixin {
    @Redirect(
        method = "handleInputEvents",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;stopUsingItem(Lnet/minecraft/entity/player/PlayerEntity;)V"
        )
    )
    private void thm$preserveHighwayBuilderBowDraw(ClientPlayerInteractionManager interactionManager, PlayerEntity player) {
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder != null && builder.isActive() && builder.drawingBow) return;

        interactionManager.stopUsingItem(player);
    }
}
