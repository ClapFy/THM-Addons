package xyz.thm.addon.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.shaders.ShaderBackground;

// Swaps the vanilla rotating-cube panorama for the active THM shader background on every
// screen that would otherwise show it - vanilla's own Screen#renderBackground already scopes
// renderPanoramaBackground to "no world loaded" screens (title, singleplayer/multiplayer
// selection, realms, create-world, ...), so no extra instanceof check is needed here. See
// ShaderBackground for the render path.
//
// ponytail: no dimming/blur layered on top here anymore. Both vanilla's GameRenderer#renderBlur()
// (real box-blur) and a plain translucent DrawContext.fill() were tried and both left later
// draws that same frame (the "Minecraft <version>" text, at minimum) not rendering - root cause
// not fully pinned down. Contrast against the shader is handled by MainMenuFx's window/button
// fills being mostly opaque instead.
@Mixin(Screen.class)
public abstract class TitleScreenShaderMixin {

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void thm$renderShaderBackground(DrawContext context, float deltaTicks, CallbackInfo ci) {
        if (ShaderBackground.render()) ci.cancel();
    }
}
