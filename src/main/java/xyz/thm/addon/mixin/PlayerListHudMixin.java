/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.utils.FastTab;

import java.util.List;
import java.util.UUID;

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

    // Every head is two textured quads with a texture nothing else in the frame uses, so the GUI renderer
    // has to break its batch per player - a thousand-entry tab list is a thousand extra draw calls, which
    // is what actually costs the framerate. Caching cannot help with that; not drawing them can.
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/PlayerSkinDrawer;draw(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;IIIZZI)V"))
    private void thmAddon$skipHead(DrawContext context, Identifier texture, int x, int y, int size, boolean hat, boolean upsideDown, int color) {
        if (!FastTab.hideHeads()) PlayerSkinDrawer.draw(context, texture, x, y, size, hat, upsideDown, color);
    }

    // this lookup is a linear scan of the loaded players, per tab entry, and its only use is deciding
    // whether the head is drawn upside down - dead work once the head isn't drawn at all
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getPlayerByUuid(Ljava/util/UUID;)Lnet/minecraft/entity/player/PlayerEntity;"))
    private PlayerEntity thmAddon$skipUpsideDownLookup(ClientWorld world, UUID uuid) {
        return FastTab.hideHeads() ? null : world.getPlayerByUuid(uuid);
    }
}
