/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.MainMenuFx;

// Mouse particle trail on every screen shown before a world is loaded (title, singleplayer/
// multiplayer/realms/create-world selection, ...). Mixed into Screen's own base render() (not
// TitleScreen's override) so it fires both for screens that use that base implementation
// directly (e.g. MultiplayerScreen) and for TitleScreen via its super.render() call -
// TitleScreenMenuMixin no longer ticks/renders particles itself, to avoid double-drawing.
@Mixin(Screen.class)
public abstract class MenuParticlesMixin {

    @Shadow @Final protected MinecraftClient client;

    @Inject(method = "render", at = @At("TAIL"))
    private void thm$renderMenuParticles(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (this.client.world != null) return;

        MainMenuFx.tick(mouseX, mouseY);
        MainMenuFx.renderParticles(context);
    }
}
