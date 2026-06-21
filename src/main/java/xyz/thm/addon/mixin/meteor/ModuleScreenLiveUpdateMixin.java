package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.screens.ModuleScreen;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.glfw.GLFW.*;

@Mixin(value = ModuleScreen.class, remap = false)
public abstract class ModuleScreenLiveUpdateMixin {
    @Shadow private Module module;

    @Unique private final Map<String, Integer> thm$hashes = new HashMap<>();

    @Inject(method = "initWidgets", at = @At("RETURN"))
    private void thm$snapshotOnInit(CallbackInfo ci) {
        thm$hashes.clear();
        for (SettingGroup g : module.settings.groups)
            for (Setting<?> s : g) {
                Object v = s.get();
                thm$hashes.put(s.name, v != null ? v.hashCode() : 0);
            }
    }

    // WidgetScreen subscribes `this` to Meteor's event bus on screen open and
    // unsubscribes on close, so this handler is active only while the screen is open.
    @EventHandler
    private void thm$onTick(TickEvent.Post event) {
        // Skip while a mouse button is held — avoids disrupting slider drags
        long w = mc.getWindow().getHandle();
        if (glfwGetMouseButton(w, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS
         || glfwGetMouseButton(w, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS) return;

        boolean dirty = false;
        for (SettingGroup g : module.settings.groups) {
            for (Setting<?> s : g) {
                Object v = s.get();
                int hash = v != null ? v.hashCode() : 0;
                Integer prev = thm$hashes.put(s.name, hash);
                if (prev != null && prev != hash) dirty = true;
            }
        }
        if (dirty) module.settings.invalidate();
    }
}
