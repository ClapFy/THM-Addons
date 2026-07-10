package xyz.thm.addon.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import xyz.thm.addon.shaders.ShaderManager;
import xyz.thm.addon.system.THMSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Plain vanilla Screen - no Meteor GuiTheme/WidgetScreen dependency - styled with the same
// BleachHack window chrome as the title screen (see MainMenuFx). Reads/writes THMSystem's
// settings directly instead of going through Meteor's generic settings-widget factory.
public class MainMenuSettingsScreen extends Screen {
    private static final int WINDOW_WIDTH = 320;
    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private int x1, y1, x2, y2;

    public MainMenuSettingsScreen(Screen parent) {
        super(Text.literal("THM Menu"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        THMSystem system = THMSystem.get();
        boolean random = system.shaderRandom.get();

        int windowHeight = computeContentHeight(random);

        x1 = Math.max(4, (this.width - WINDOW_WIDTH) / 2);
        y1 = Math.max(4, (this.height - windowHeight) / 2);
        x2 = x1 + WINDOW_WIDTH;
        y2 = y1 + windowHeight;

        int contentX = x1 + 10;
        int contentWidth = WINDOW_WIDTH - 20;
        int y = y1 + 16;

        y = addCheckbox("Rainbow Title", system.mainMenuRainbowTitle.get(), contentX, y, contentWidth,
            v -> system.mainMenuRainbowTitle.set(v));
        y = addCheckbox("Particle Trail", system.mainMenuParticles.get(), contentX, y, contentWidth,
            v -> system.mainMenuParticles.set(v));
        y = addCheckbox("Random Shader", random, contentX, y, contentWidth, v -> {
            system.shaderRandom.set(v);
            this.clearAndInit();
        });

        if (random) {
            y = addShaderPoolGrid(system, contentX, y, contentWidth);
        } else {
            y = addShaderChoice(system, contentX, y, contentWidth);
        }

        y = addBlurSlider(system, contentX, y, contentWidth);

        ButtonWidget close = this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> this.close())
            .dimensions(contentX, y, contentWidth, 20)
            .build());
        ThmStyledButtons.mark(close);
    }

    private int addCheckbox(String label, boolean checked, int x, int y, int width, java.util.function.Consumer<Boolean> onChange) {
        CheckboxWidget checkbox = CheckboxWidget.builder(Text.literal(label), this.textRenderer)
            .pos(x, y)
            .checked(checked)
            .maxWidth(width)
            .callback((cb, value) -> onChange.accept(value))
            .build();
        this.addDrawableChild(checkbox);
        return y + ROW_HEIGHT;
    }

    private int addShaderChoice(THMSystem system, int x, int y, int width) {
        List<String> options = new ArrayList<>();
        options.add("None");
        options.addAll(ShaderManager.availableShaders());
        String current = options.contains(system.shaderChoice.get()) ? system.shaderChoice.get() : "None";

        CyclingButtonWidget<String> widget = CyclingButtonWidget.builder((String s) -> Text.literal("Shader: " + s), current)
            .values(options)
            .build(x, y, width, 20, Text.literal("Shader"), (btn, value) -> system.shaderChoice.set(value));
        this.addDrawableChild(widget);
        ThmStyledButtons.mark(widget);
        return y + ROW_HEIGHT;
    }

    private int gridRows() {
        int count = ShaderManager.availableShaders().size();
        return (count + 2) / 3;
    }

    // Mirrors init()'s Y increments exactly (16 top pad, 3 checkboxes, shader row(s), blur
    // slider, close button + bottom pad) - kept in sync by hand since it has to know the final
    // height *before* x1/y1 (and therefore any real widget) can be positioned.
    private int computeContentHeight(boolean random) {
        int y = 16;
        y += ROW_HEIGHT * 3; // Rainbow Title, Particle Trail, Random Shader
        y += random ? (12 + gridRows() * 14 + 4) : ROW_HEIGHT; // shader pool grid vs. shader choice
        y += ROW_HEIGHT; // blur slider
        y += 20 + 10; // close button + bottom padding
        return y;
    }

    // Simple 3-column checkbox grid instead of a scrollable two-column picker - 19 shaders fits
    // comfortably without needing a real list widget.
    private int addShaderPoolGrid(THMSystem system, int x, int y, int width) {
        this.addDrawableChild(new TextWidget(x, y, width, 12,
            Text.literal("Allowed shaders (none checked = allow all):"), this.textRenderer));
        y += 12;

        Set<String> selected = system.shaderPool.get().selected();
        List<String> shaders = ShaderManager.availableShaders();
        int columns = 3;
        int columnWidth = width / columns;

        for (int i = 0; i < shaders.size(); i++) {
            String shader = shaders.get(i);
            int col = i % columns;
            int row = i / columns;

            CheckboxWidget checkbox = CheckboxWidget.builder(Text.literal(shader), this.textRenderer)
                .pos(x + col * columnWidth, y + row * 14)
                .checked(selected.contains(shader))
                .maxWidth(columnWidth - 4)
                .callback((cb, checked) -> {
                    if (checked) selected.add(shader);
                    else selected.remove(shader);
                })
                .build();
            this.addDrawableChild(checkbox);
        }

        return y + gridRows() * 14 + 4;
    }

    private int addBlurSlider(THMSystem system, int x, int y, int width) {
        int initial = system.mainMenuBlur.get();
        SliderWidget slider = new SliderWidget(x, y, width, 20, Text.literal("Blur: " + initial), initial / 100.0) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Blur: " + Math.round(this.value * 100)));
            }

            @Override
            protected void applyValue() {
                system.mainMenuBlur.set((int) Math.round(this.value * 100));
            }
        };
        this.addDrawableChild(slider);
        return y + ROW_HEIGHT;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        MainMenuFx.renderChrome(context, this.textRenderer, x1, y1, x2, y2, "THM Menu");
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // Keep whatever's already drawn behind this screen (the title screen's own shader/
        // window) instead of vanilla's dirt/blur background.
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
