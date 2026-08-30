/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.gui;

import xyz.thm.addon.utils.Homes;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

/**
 * Homes as a 9x4 chest, drawn from vanilla's own {@code generic_54} container texture: the top three rows
 * are one page of homes, the bottom row is the controls (page glass panes on the outside, green/red
 * concrete in the middle). Everything is a slot — there is not a single widget on this screen.
 *
 * <p>The texture is drawn in two pieces, the title bar plus the four slot rows and then the frame's bottom
 * edge from the very bottom of the texture; that is how a chest with fewer than six rows gets drawn at all.
 */
public class HomesScreen extends Screen {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int TEX_SIZE = 256;
    private static final int PANEL_WIDTH = 176;
    private static final int COLUMNS = 9;
    private static final int ROWS = 4;
    private static final int PAGE_SIZE = COLUMNS * (ROWS - 1);
    private static final int HEADER = 17;
    private static final int SLOT = 18;
    private static final int TRIM = 7;
    private static final int TRIM_V = 215;
    private static final int PANEL_HEIGHT = HEADER + ROWS * SLOT + TRIM;

    // control-row columns
    private static final int PREV = 0;
    private static final int TELEPORT = 3;
    private static final int DELETE = 5;
    private static final int NEXT = 8;

    private static final int TEXT = 0xFF404040;
    private static final int HOVER = 0x80FFFFFF;
    private static final int SELECTED = 0x9033DD33;
    private static final int ARMED = 0x80FF3333;

    private final Homes module;

    private String selected;
    private boolean confirmDelete;
    private int page;
    private int x, y;

    public HomesScreen(Homes module) {
        super(Component.literal("Homes"));
        this.module = module;
    }

    @Override
    protected void init() {
        x = (width - PANEL_WIDTH) / 2;
        y = (height - PANEL_HEIGHT) / 2;
    }

    private int pageCount() {
        return Math.max(1, (int) Math.ceil(module.homes().size() / (double) PAGE_SIZE));
    }

    private int slotX(int slot) {
        return x + 8 + (slot % COLUMNS) * SLOT;
    }

    private int slotY(int slot) {
        return y + HEADER + 1 + (slot / COLUMNS) * SLOT;
    }

    /** Index into the visible 9x4 grid under the cursor, matching vanilla's own 16x16 slot hit box. */
    private int slotAt(double mouseX, double mouseY) {
        for (int slot = 0; slot < COLUMNS * ROWS; slot++) {
            int sx = slotX(slot), sy = slotY(slot);
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) return slot;
        }
        return -1;
    }

    /** The home in a grid slot of the top three rows, or null. */
    private String homeAt(int slot) {
        if (slot < 0 || slot >= PAGE_SIZE) return null;
        int index = page * PAGE_SIZE + slot;
        List<String> homes = module.homes();
        return index < homes.size() ? homes.get(index) : null;
    }

    private ItemStack controlItem(int column) {
        return switch (column) {
            case PREV -> page > 0 ? new ItemStack(Items.GLASS_PANE) : ItemStack.EMPTY;
            case NEXT -> page < pageCount() - 1 ? new ItemStack(Items.GLASS_PANE) : ItemStack.EMPTY;
            case TELEPORT -> new ItemStack(Items.GREEN_CONCRETE);
            case DELETE -> new ItemStack(Items.RED_CONCRETE);
            default -> ItemStack.EMPTY;
        };
    }

    private Component controlTooltip(int column) {
        return switch (column) {
            case PREV -> Component.literal("Previous page");
            case NEXT -> Component.literal("Next page");
            case TELEPORT -> Component.literal(selected == null ? "Select a home first" : "Teleport to " + selected);
            case DELETE -> Component.literal(selected == null ? "Select a home first"
                : confirmDelete ? "Click again to delete " + selected : "Delete " + selected);
            default -> null;
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int slot = slotAt(click.x(), click.y());
        if (slot < 0) return super.mouseClicked(click, doubled);

        String home = homeAt(slot);
        if (home != null) {
            if (click.button() == GLFW_MOUSE_BUTTON_RIGHT) {
                ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
                module.setIcon(home, held.isEmpty() ? null : held.getItem());
            } else {
                selected = home.equals(selected) ? null : home;
                confirmDelete = false;
            }
            return true;
        }

        if (slot >= PAGE_SIZE) onControl(slot - PAGE_SIZE);
        return true;
    }

    private void onControl(int column) {
        switch (column) {
            case PREV -> {
                if (page > 0) page--;
            }
            case NEXT -> {
                if (page < pageCount() - 1) page++;
            }
            case TELEPORT -> {
                if (selected != null) module.teleport(selected);
            }
            case DELETE -> {
                if (selected == null) return;
                if (!confirmDelete) {
                    confirmDelete = true;
                    return;
                }
                module.delete(selected);
                selected = null;
                confirmDelete = false;
                page = Math.min(page, pageCount() - 1);
            }
            default -> { }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);

        int body = HEADER + ROWS * SLOT;
        context.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0f, 0f,
            PANEL_WIDTH, body, PANEL_WIDTH, body, TEX_SIZE, TEX_SIZE);
        context.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y + body, 0f, TRIM_V,
            PANEL_WIDTH, TRIM, PANEL_WIDTH, TRIM, TEX_SIZE, TEX_SIZE);

        String heading = pageCount() > 1 ? "Homes (" + (page + 1) + "/" + pageCount() + ")" : "Homes";
        context.text(font, heading, x + 8, y + 6, TEXT, false);
        // a grid of identical dirt blocks is unreadable otherwise - the tooltip only covers hover
        if (selected != null) {
            context.text(font, selected,
                x + PANEL_WIDTH - 8 - font.width(selected), y + 6, TEXT, false);
        }

        int hovered = slotAt(mouseX, mouseY);

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            String home = homeAt(slot);
            if (home == null) continue;
            int sx = slotX(slot), sy = slotY(slot);
            context.item(module.icon(home), sx, sy);
            if (home.equals(selected)) context.fill(sx, sy, sx + 16, sy + 16, SELECTED);
            else if (hovered == slot) context.fill(sx, sy, sx + 16, sy + 16, HOVER);
        }

        for (int column = 0; column < COLUMNS; column++) {
            ItemStack stack = controlItem(column);
            if (stack.isEmpty()) continue;
            int slot = PAGE_SIZE + column;
            int sx = slotX(slot), sy = slotY(slot);
            context.item(stack, sx, sy);
            if (column == DELETE && confirmDelete) context.fill(sx, sy, sx + 16, sy + 16, ARMED);
            else if (hovered == slot) context.fill(sx, sy, sx + 16, sy + 16, HOVER);
        }

        context.centeredText(font, Component.literal("Right-click a home to use your held item as its icon"),
            x + PANEL_WIDTH / 2, y + PANEL_HEIGHT + 6, 0xFFFFFFFF);

        if (hovered < 0) return;
        String home = homeAt(hovered);
        if (home != null) context.setTooltipForNextFrame(font, Component.literal(home), mouseX, mouseY);
        else if (hovered >= PAGE_SIZE && !controlItem(hovered - PAGE_SIZE).isEmpty()) {
            context.setTooltipForNextFrame(font, controlTooltip(hovered - PAGE_SIZE), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
