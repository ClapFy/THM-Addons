package xyz.thm.addon.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.ColorHelper;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// ponytail: ported from BleachHack's BleachTitleScreen/ParticleManager/Particle/Window - an
// OS-window-styled frame (same chrome as BleachHack's gui/window/Window.java, recolored to
// THMAddon.THMColor/THMSideColor) wrapping the title screen's actual buttons, which
// TitleScreenMenuMixin repositions into it and ButtonWidgetStyleMixin re-skins. Everything here
// is plain DrawContext.fill/text - same cost as any other GUI widget, no custom GL state - so
// it doesn't add meaningfully to title screen render time.
public class MainMenuFx {

    // THMAddon.THMColor/THMSideColor are the addon's actual brand colors (already used as the
    // line/side colors for THM's block-ESP-style renders elsewhere) instead of a guessed accent.
    private static final int THM_COLOR = THMAddon.THMColor.getPacked();
    private static final int THM_SIDE = THMAddon.THMSideColor.getPacked();

    private static final int BORDER_TOP = ColorHelper.lerp(0.3f, 0xff000000, THM_COLOR);
    private static final int BORDER_BOTTOM = THM_COLOR;
    private static final int TITLEBAR_LEFT = ColorHelper.lerp(0.75f, 0xff000000, THM_COLOR);
    private static final int TITLEBAR_RIGHT = ColorHelper.lerp(0.55f, 0xff000000, THM_COLOR);
    // Genuinely see-through now that the backdrop behind it is actually blurred (see
    // ShaderBackground.renderBlurredRegion) - a soft, low-contrast blurred image can sit behind
    // a much more transparent pane than a sharp/busy one without hurting text readability.
    private static final int BODY_FILL = withAlpha(ColorHelper.lerp(0.8f, 0xff000000, THM_COLOR), 0x70);
    private static final int BUTTON_FILL = withAlpha(ColorHelper.lerp(0.7f, 0xff000000, THM_COLOR), 0xe0);
    private static final int BUTTON_FILL_HOVER = withAlpha(THM_COLOR, 0xe0);

    // Header text above the buttons - edit this to change it. Defaults to "Minecraft" (the
    // word the vanilla logo it replaces used to show).
    private static final String HEADER_TEXT = "Minecraft";
    private static final float HEADER_SCALE = 3f;

    private static final List<Particle> particles = new ArrayList<>();
    private static final Random RAND = new Random();

    public static void tick(int mouseX, int mouseY) {
        if (!THMSystem.get().mainMenuParticles.get()) return;

        particles.add(new Particle(mouseX, mouseY));
        particles.removeIf(Particle::update);
    }

    /** Window bounds matching BleachHack's own layout: centered, 3/4 of the screen. */
    public static int[] windowBounds(int screenWidth, int screenHeight) {
        return new int[] {
            screenWidth / 8, screenHeight / 8,
            screenWidth - screenWidth / 8, screenHeight - screenHeight / 8 + 2
        };
    }

    /** Window frame + big title - draw this BEFORE the screen's buttons so they sit on top of it. */
    public static void renderWindow(DrawContext context, TextRenderer tr, int screenWidth, int screenHeight) {
        if (!THMSystem.get().mainMenuWindow.get()) return;

        int[] bounds = windowBounds(screenWidth, screenHeight);
        int x1 = bounds[0], y1 = bounds[1], x2 = bounds[2], y2 = bounds[3];
        int centerX = x1 + (x2 - x1) / 2;
        int headerY = y1 + (y2 - y1) / 4 - 25;

        renderChrome(context, tr, x1, y1, x2, y2, "THM Addons");
        renderBigTitle(context, tr, centerX, headerY);

        String version = "THM Addon v" + THMAddon.VERSION;
        context.drawTextWithShadow(tr, version, centerX - tr.getWidth(version) / 2, headerY + (int) (10 * HEADER_SCALE) + 2, THM_COLOR);
    }

    /** Mouse particle trail - draw this AFTER the screen's buttons so it drifts over everything. */
    public static void renderParticles(DrawContext context) {
        if (!THMSystem.get().mainMenuParticles.get()) return;

        for (Particle p : particles) {
            for (int[] pos : p.points) {
                context.fill(pos[0], pos[1], pos[0] + 1, pos[1] + 1, THM_COLOR);
            }
        }
    }

    /** BleachHack-styled flat button chrome, drawn in place of a repositioned vanilla button. */
    public static void renderButton(DrawContext context, TextRenderer tr, int x1, int y1, int x2, int y2, String text, boolean hovered, boolean active) {
        int fill = hovered && active ? BUTTON_FILL_HOVER : BUTTON_FILL;

        context.fill(x1, y1 + 1, x1 + 1, y2 - 1, BORDER_TOP);
        horizontalGradient(context, x1 + 1, y1, x2 - 1, y1 + 1, BORDER_TOP, BORDER_BOTTOM);
        context.fill(x2 - 1, y1 + 1, x2, y2 - 1, BORDER_BOTTOM);
        horizontalGradient(context, x1 + 1, y2 - 1, x2 - 1, y2, BORDER_TOP, BORDER_BOTTOM);
        context.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);

        int color = active ? 0xffffffff : 0xffa0a0a0;
        int textWidth = tr.getWidth(text);
        context.drawTextWithShadow(tr, text, x1 + (x2 - x1) / 2 - textWidth / 2, y1 + (y2 - y1) / 2 - 4, color);
    }

    // Found it: both earlier attempts here passed 0xffffff as the text color, which is only
    // 6 hex digits - i.e. alpha byte 0x00, fully transparent. Every OTHER text draw in this
    // file (version/button/title text, all confirmed visible in screenshots) uses a color with
    // alpha explicitly set (THM_COLOR, -1, ...). Scaled via the same pushMatrix/translate/scale
    // wrapper as before, just with that one-byte bug fixed and a plain String instead of a
    // MutableText (no need for per-character styling here).
    private static void renderBigTitle(DrawContext context, TextRenderer tr, int centerX, int y) {
        float textWidth = tr.getWidth(HEADER_TEXT) * HEADER_SCALE;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(centerX - textWidth / 2f, (float) y);
        context.getMatrices().scale(HEADER_SCALE, HEADER_SCALE);
        context.drawTextWithShadow(tr, HEADER_TEXT, 0, 0, THM_COLOR);
        context.getMatrices().popMatrix();
    }

    // Same chrome as BleachHack's Window, recolored to the THM gold accent: gradient border,
    // title bar, translucent body, close/minimize glyphs (wired up - see isCloseButton/
    // isMinimizeButton below, hit-tested by callers against the same coordinates used to draw
    // them here).
    // Public so MainMenuSettingsScreen (the non-Meteor settings screen) can reuse the same look.
    public static void renderChrome(DrawContext context, TextRenderer tr, int x1, int y1, int x2, int y2, String title) {
        context.fill(x1, y1 + 1, x1 + 1, y2 - 1, BORDER_TOP);
        horizontalGradient(context, x1 + 1, y1, x2 - 1, y1 + 1, BORDER_TOP, BORDER_BOTTOM);
        context.fill(x2 - 1, y1 + 1, x2, y2 - 1, BORDER_BOTTOM);
        horizontalGradient(context, x1 + 1, y2 - 1, x2 - 1, y2, BORDER_TOP, BORDER_BOTTOM);

        context.fill(x1 + 1, y1 + 12, x2 - 1, y2 - 1, BODY_FILL);
        horizontalGradient(context, x1 + 1, y1 + 1, x2 - 1, y1 + 12, TITLEBAR_LEFT, TITLEBAR_RIGHT);

        context.drawTextWithShadow(tr, title, x1 + 4, y1 + 3, -1);

        context.drawText(tr, "x", x2 - 10, y1 + 3, 0, false);
        context.drawText(tr, "x", x2 - 11, y1 + 2, -1, false);
        context.drawText(tr, "_", x2 - 21, y1 + 2, 0, false);
        context.drawText(tr, "_", x2 - 22, y1 + 1, -1, false);
    }

    /** Hit-box for the "x" glyph drawn by renderChrome, in the same x1/y1/x2/y2 coordinates. */
    public static boolean isCloseButton(int x1, int y1, int x2, int y2, double mouseX, double mouseY) {
        return mouseX >= x2 - 11 && mouseX < x2 - 1 && mouseY >= y1 && mouseY < y1 + 11;
    }

    /** Hit-box for the "_" glyph drawn by renderChrome, in the same x1/y1/x2/y2 coordinates. */
    public static boolean isMinimizeButton(int x1, int y1, int x2, int y2, double mouseX, double mouseY) {
        return mouseX >= x2 - 23 && mouseX < x2 - 12 && mouseY >= y1 && mouseY < y1 + 11;
    }

    // 1.21.11's DrawContext.fillGradient(...) only interpolates vertically - see BleachHack's
    // own Window.horizontalGradient for the same workaround on this MC version.
    private static void horizontalGradient(DrawContext context, int x1, int y1, int x2, int y2, int color1, int color2) {
        int width = x2 - x1;
        if (width <= 0) return;

        for (int i = 0; i < width; i++) {
            float t = width <= 1 ? 0f : (float) i / (width - 1);
            context.fill(x1 + i, y1, x1 + i + 1, y2, ColorHelper.lerp(t, color1, color2));
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00ffffff) | (alpha << 24);
    }

    private static class Particle {
        final int x, y;
        final int lifespan;
        final List<int[]> points = new ArrayList<>();
        int tick;
        long lastTick;

        Particle(int x, int y) {
            this.x = x;
            this.y = y;
            this.lifespan = RAND.nextInt(20);
            this.lastTick = System.currentTimeMillis();
            for (int j = 0; j < RAND.nextInt(10); j++) {
                points.add(new int[] { x + RAND.nextInt(5) - 2, y + RAND.nextInt(5) - 2 });
            }
        }

        /** @return true once the particle has finished its life and should be removed */
        boolean update() {
            if (System.currentTimeMillis() < lastTick + 16) return false;
            lastTick = System.currentTimeMillis();
            tick++;

            if (tick > lifespan) {
                points.clear();
                return true;
            }

            for (int i = 0; i < points.size(); i++) {
                int[] pos = points.get(i);
                points.set(i, new int[] { pos[0] + (pos[0] - x) / tick, pos[1] + (pos[1] - y) / tick });
            }
            return false;
        }
    }
}
