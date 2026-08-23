/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.utils.FastTab;

import java.util.List;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
    @Inject(method = "collectPlayerEntries", at = @At("HEAD"), cancellable = true)
    private void thmAddon$cachedEntries(CallbackInfoReturnable<List<PlayerListEntry>> cir) {
        List<PlayerListEntry> cached = FastTab.entries();
        if (cached != null) cir.setReturnValue(cached);
    }

    @Inject(method = "collectPlayerEntries", at = @At("RETURN"))
    private void thmAddon$storeEntries(CallbackInfoReturnable<List<PlayerListEntry>> cir) {
        FastTab.storeEntries(cir.getReturnValue());
    }

    // Redirecting the call site rather than injecting into getPlayerName keeps this independent of the
    // order Meteor's own HEAD injection into that method is applied in - a miss still runs the full
    // Meteor + THM name pipeline, we just stop it running once per player per frame.
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/PlayerListHud;getPlayerName(Lnet/minecraft/client/network/PlayerListEntry;)Lnet/minecraft/text/Text;"))
    private Text thmAddon$cachedName(PlayerListHud hud, PlayerListEntry entry) {
        Text cached = FastTab.name(entry);
        return cached != null ? cached : FastTab.store(entry, hud.getPlayerName(entry));
    }
}
