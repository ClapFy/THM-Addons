/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.thm.addon.modules.HighwayBuilderTHM;

/**
 * Vanilla timeout / connection-lost screens only show the network reason. When Highway Builder was
 * still running, append the same Distance / blocks broken / placed lines that an intentional
 * Highway Builder disconnect already includes.
 */
@Mixin(DisconnectedScreen.class)
public class DisconnectedScreenMixin {
    @Redirect(
        method = {"init", "getNarrationMessage"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/DisconnectionDetails;reason()Lnet/minecraft/network/chat/Component;"
        )
    )
    private Component thm$appendHighwayBuilderStats(DisconnectionDetails details) {
        Component reason = details.reason();
        return HighwayBuilderTHM.appendActiveSessionStatsToDisconnectReason(reason);
    }
}
