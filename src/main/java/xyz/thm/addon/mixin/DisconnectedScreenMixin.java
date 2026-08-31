/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.thm.addon.modules.HighwayBuilderTHM;

/**
 * Vanilla timeout / connection-lost screens only show the network reason. When Highway Builder was
 * still running, append the same Distance / blocks broken / placed lines that an intentional
 * Highway Builder disconnect already includes.
 *
 * Meteor already mixins this screen (AutoReconnect buttons). This only rewrites the
 * {@code MultiLineTextWidget} reason argument and is optional so a missed inject cannot take
 * down the whole client mixin config.
 */
@Mixin(DisconnectedScreen.class)
public class DisconnectedScreenMixin {
    @ModifyArg(
        method = "init()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/MultiLineTextWidget;<init>(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/Font;)V"
        ),
        index = 0,
        require = 0
    )
    private Component thm$appendHighwayBuilderStats(Component reason) {
        try {
            return HighwayBuilderTHM.appendActiveSessionStatsToDisconnectReason(reason);
        } catch (Throwable ignored) {
            return reason;
        }
    }
}
