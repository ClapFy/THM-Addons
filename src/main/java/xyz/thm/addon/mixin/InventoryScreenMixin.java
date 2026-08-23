/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import xyz.thm.addon.modules.Loadouts;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends RecipeBookScreen<PlayerScreenHandler>
    implements RecipeBookProvider {
    public InventoryScreenMixin(PlayerScreenHandler handler, RecipeBookWidget<?> recipeBook, PlayerInventory inventory, Text title) {
        super(handler, recipeBook, inventory, title);
    }
    @Unique @Nullable
    private Loadouts loadouts = null;
    @Unique @Nullable
    private ButtonWidget saveLoadoutButton = null;
    @Unique @Nullable
    private ButtonWidget loadLoadoutButton = null;
    @Unique
    private int loadAnimTicks = 0;
    @Unique
    private void onSaveLoadoutButtonPress(ButtonWidget btn) {
        if (loadouts == null) {
            Modules modules = Modules.get();
            if (modules == null ) return;
            loadouts = modules.get(Loadouts.class);
            if (loadouts == null) return;
        }
        loadouts.saveLoadout("quicksave");
        btn.setMessage(Text.of("Save"));
    }
    @Unique
    private void onLoadLoadoutButtonPress(ButtonWidget btn) {
        if (loadouts == null) {
            Modules modules = Modules.get();
            if (modules == null ) return;
            loadouts = modules.get(Loadouts.class);
            if (loadouts == null) return;
        }
        loadouts.loadLoadout("quicksave");
        btn.setMessage(Text.of("Load"));
    }
    @Inject(method = "init", at = @At("TAIL"))
    private void mixinInit(CallbackInfo ci) {
        if (loadouts == null) {
            Modules modules = Modules.get();
            if (modules == null ) return;
            loadouts = modules.get(Loadouts.class);
            if (loadouts == null) return;
        }
        if (!loadouts.quickLoadout.get()) return;
        saveLoadoutButton = this.addDrawableChild(
            ButtonWidget.builder(
                    Text.of("Save"),
                    this::onSaveLoadoutButtonPress
                )
                .dimensions(this.width / 2 - 42, this.height / 2 + 83, 42, 16)
                .tooltip(Tooltip.of(Text.of("§7§oSave your current inventory to Loadouts.")))
                .build()
        );
        loadLoadoutButton = this.addDrawableChild(
            ButtonWidget.builder(
                    Text.of("Load"),
                    this::onLoadLoadoutButtonPress
                )
                .dimensions(this.width / 2, this.height / 2 + 83, 42, 16)
                .tooltip(Tooltip.of(Text.of("§7§oLoad your quicksave loadout.")))
                .build()
        );
        if (saveLoadoutButton != null) saveLoadoutButton.visible = loadouts.isActive();
        if (loadLoadoutButton != null) loadLoadoutButton.visible = loadouts.isActive();
    }
    @Inject(method = "render", at = @At("TAIL"))
    private void mixinRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (loadouts == null) {
            Modules modules = Modules.get();
            if (modules == null ) return;
            loadouts = modules.get(Loadouts.class);
            if (loadouts == null) return;
        }
        if (!loadouts.quickLoadout.get()) return;
        if (saveLoadoutButton != null) {
            saveLoadoutButton.visible = loadouts.isActive();
        }
        if (loadLoadoutButton != null) {
            loadLoadoutButton.visible = loadouts.isActive();
        }
    }
    @Inject(method = "handledScreenTick", at = @At("HEAD"))
    private void animateButtons(CallbackInfo ci) {
        if (loadouts == null) {
            Modules modules = Modules.get();
            if (modules == null ) return;
            loadouts = modules.get(Loadouts.class);
            if (loadouts == null) return;
        }
        if (!loadouts.quickLoadout.get()) return;
        if (loadLoadoutButton == null) return;

        // ponytail: dots on the Load button only - saving is instant, sorting is the one in-flight state.
        if (loadouts.isActive() && !loadouts.isSorted) {
            loadLoadoutButton.setMessage(Text.of("Load" + ".".repeat((loadAnimTicks++ / 4) % 4)));
        } else {
            loadAnimTicks = 0;
            loadLoadoutButton.setMessage(Text.of("Load"));
        }
    }
}
