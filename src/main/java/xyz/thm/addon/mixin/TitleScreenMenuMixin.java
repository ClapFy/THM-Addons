package xyz.thm.addon.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextIconButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.MainMenuFx;
import xyz.thm.addon.gui.MainMenuSettingsScreen;
import xyz.thm.addon.gui.ThmStyledButtons;
import xyz.thm.addon.shaders.ShaderBackground;
import xyz.thm.addon.shaders.ShaderManager;
import xyz.thm.addon.system.THMSystem;

import java.util.ArrayList;
import java.util.List;

// Reuses vanilla's own buttons (so their click handlers stay untouched) but moves them into
// a BleachHack-styled window frame (see MainMenuFx) instead of vanilla's default layout, and
// hides the vanilla logo/splash the window's own title replaces. All purely GUI-layer (button
// repositioning + DrawContext fills), no custom rendering pipeline - see MainMenuFx for why
// that matters.
@Mixin(TitleScreen.class)
public abstract class TitleScreenMenuMixin extends Screen {

    protected TitleScreenMenuMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void thm$layoutWindow(CallbackInfo ci) {
        ShaderManager.reroll();

        if (!THMSystem.get().mainMenuRainbowTitle.get()) return;

        int[] bounds = MainMenuFx.windowBounds(this.width, this.height);
        int x1 = bounds[0], y1 = bounds[1], x2 = bounds[2], y2 = bounds[3];
        int h = y2 - y1;
        int centerX = x1 + (x2 - x1) / 2;
        int maxY = y1 + MathHelper.clamp(h / 4 + 119, 0, h - 22);

        thm$repositionByLabel("menu.singleplayer", centerX - 100, y1 + h / 4 + 38);
        thm$repositionByLabel("menu.multiplayer", centerX - 100, y1 + h / 4 + 62);
        thm$repositionByLabel("menu.online", centerX - 100, y1 + h / 4 + 86);
        thm$repositionByLabel("menu.options", centerX - 100, maxY);
        thm$repositionByLabel("menu.quit", centerX + 2, maxY);

        List<TextIconButtonWidget> iconButtons = new ArrayList<>();
        for (Element el : this.children()) {
            if (el instanceof TextIconButtonWidget icon) iconButtons.add(icon);
        }
        // Icon-only buttons (language/accessibility) keep their vanilla look - just repositioned,
        // not re-skinned, since BleachHack's own recreation doesn't have icon buttons either.
        if (!iconButtons.isEmpty()) iconButtons.get(0).setPosition(centerX - 124, maxY);
        if (iconButtons.size() > 1) iconButtons.get(1).setPosition(centerX + 104, maxY);

        ClickableWidget menuButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("THM Menu"), b ->
                this.client.setScreen(new MainMenuSettingsScreen(this)))
            .dimensions(x2 - 90, y1 + 16, 82, 16)
            .build());
        ThmStyledButtons.mark(menuButton);
    }

    private void thm$repositionByLabel(String i18nKey, int x, int y) {
        String label = I18n.translate(i18nKey);
        for (Element el : this.children()) {
            if (el instanceof ClickableWidget widget && widget.getMessage().getString().equals(label)) {
                widget.setPosition(x, y);
                ThmStyledButtons.mark(widget);
                return;
            }
        }
    }

    // Chrome is drawn before super.render() (which draws the buttons) so the buttons sit on
    // top of the window frame, not under it. The particle trail is handled globally by
    // MenuParticlesMixin (every world-not-loaded screen, not just this one).
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
    private void thm$renderWindowChrome(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (THMSystem.get().mainMenuRainbowTitle.get()) {
            int[] bounds = MainMenuFx.windowBounds(this.width, this.height);
            ShaderBackground.renderBlurredRegion(bounds[0], bounds[1], bounds[2], bounds[3], THMSystem.get().mainMenuBlur.get());
        }

        MainMenuFx.renderWindow(context, this.textRenderer, this.width, this.height);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/LogoDrawer;draw(Lnet/minecraft/client/gui/DrawContext;IF)V"))
    private void thm$suppressLogo(LogoDrawer logoDrawer, DrawContext context, int screenWidth, float alpha) {
        if (THMSystem.get().mainMenuRainbowTitle.get()) return;
        logoDrawer.draw(context, screenWidth, alpha);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/SplashTextRenderer;render(Lnet/minecraft/client/gui/DrawContext;ILnet/minecraft/client/font/TextRenderer;F)V"))
    private void thm$suppressSplash(SplashTextRenderer splashText, DrawContext context, int screenWidth, TextRenderer textRenderer, float alpha) {
        if (THMSystem.get().mainMenuRainbowTitle.get()) return;
        splashText.render(context, screenWidth, textRenderer, alpha);
    }
}
