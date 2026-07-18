/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.MainMenuFx;
import xyz.thm.addon.gui.ThmStyledButtons;

// Re-skins buttons TitleScreenMenuMixin marked via ThmStyledButtons (the repositioned title
// screen buttons) with BleachHack-flat/THM-gold chrome instead of vanilla's beveled texture.
// Every other PressableWidget (options screen, pause menu, ...) is untouched.
@Mixin(PressableWidget.class)
public abstract class ButtonWidgetStyleMixin extends ClickableWidget {

    protected ButtonWidgetStyleMixin(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void thm$renderThmStyle(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (!ThmStyledButtons.isStyled(this)) return;

        MainMenuFx.renderButton(context, MinecraftClient.getInstance().textRenderer,
            this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(),
            this.getMessage().getString(), this.isHovered(), this.active);
        ci.cancel();
    }
}
